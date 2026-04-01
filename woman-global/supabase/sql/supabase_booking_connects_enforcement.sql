-- Booking ↔ subscription connects: consume on create, refund on decline/cancel/expire.
-- Prerequisites: supabase_booking_working_hours_providers_map.sql, supabase_subscription_connects.sql
--
-- After this file, run **supabase_free_tier_connects.sql** for free-tier connects + updated RPC signatures
-- (consume returns o_source; no subscription → free tier on users.free_connects_*).
--
-- Rules (before free tier file):
-- - Active subscription required (user_plan_subscriptions: status=active, started_at..expires_at contains today).
-- - If plan has connects_limit_enabled and row has connects_granted NOT NULL → consume 1 connect when creating a request.
-- - Unlimited: plan limit off, or connects_granted NULL → no consume.
-- - Period reset: calendar_month / calendar_year refresh bucket from plan before checking quota.
-- - Refund: if connect_consumed, decrement connects_used on the stored subscription row (once).

-- =============================================================================
-- 1. booking_requests: link charge to subscription row for idempotent refund
-- =============================================================================
ALTER TABLE public.booking_requests
  ADD COLUMN IF NOT EXISTS connect_consumed boolean NOT NULL DEFAULT false;

ALTER TABLE public.booking_requests
  ADD COLUMN IF NOT EXISTS connect_subscription_id integer NULL
    REFERENCES public.user_plan_subscriptions (id) ON DELETE SET NULL;

COMMENT ON COLUMN public.booking_requests.connect_consumed IS 'True if this request incremented connects_used on connect_subscription_id.';
COMMENT ON COLUMN public.booking_requests.connect_subscription_id IS 'user_plan_subscriptions row charged for this request (refund target).';

-- =============================================================================
-- 2. Refund one connect for a booking (idempotent if connect_consumed is false)
-- =============================================================================
CREATE OR REPLACE FUNCTION public.refund_booking_connect(p_booking_id bigint)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  br public.booking_requests%ROWTYPE;
BEGIN
  SELECT * INTO br FROM public.booking_requests WHERE id = p_booking_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN;
  END IF;
  IF NOT COALESCE(br.connect_consumed, false) OR br.connect_subscription_id IS NULL THEN
    RETURN;
  END IF;

  UPDATE public.user_plan_subscriptions ups
  SET
    connects_used = GREATEST(0, ups.connects_used - 1),
    updated_at = now()
  WHERE ups.id = br.connect_subscription_id;

  UPDATE public.booking_requests
  SET connect_consumed = false
  WHERE id = p_booking_id;
END;
$$;

-- =============================================================================
-- 3. Consume one connect (or pass through unlimited / no charge)
-- =============================================================================
CREATE OR REPLACE FUNCTION public.consume_client_subscription_connect(p_client_id integer)
RETURNS TABLE (o_sub_id integer, o_err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_ups_id integer;
  v_used integer;
  v_grant integer;
  v_period_started date;
  v_started date;
  v_limit_on boolean;
  v_per integer;
  v_rule text;
  v_anchor date;
  v_month_start date;
  v_year_start date;
BEGIN
  SELECT
    ups.id,
    ups.connects_used,
    ups.connects_granted,
    ups.connects_period_started_at,
    ups.started_at,
    sp.connects_limit_enabled,
    sp.connects_per_period,
    sp.connects_period_rule
  INTO
    v_ups_id,
    v_used,
    v_grant,
    v_period_started,
    v_started,
    v_limit_on,
    v_per,
    v_rule
  FROM public.user_plan_subscriptions ups
  JOIN public.subscription_plans sp ON sp.id = ups.plan_id
  WHERE ups.user_id = p_client_id
    AND ups.status = 'active'
    AND ups.started_at <= CURRENT_DATE
    AND ups.expires_at >= CURRENT_DATE
  ORDER BY ups.expires_at DESC, ups.id DESC
  LIMIT 1
  FOR UPDATE OF ups;

  IF NOT FOUND OR v_ups_id IS NULL THEN
    RETURN QUERY SELECT NULL::integer, 'no_active_subscription'::text;
    RETURN;
  END IF;

  IF NOT COALESCE(v_limit_on, false) OR v_grant IS NULL THEN
    RETURN QUERY SELECT NULL::integer, NULL::text;
    RETURN;
  END IF;

  IF v_per IS NULL OR v_per < 0 THEN
    RETURN QUERY SELECT NULL::integer, NULL::text;
    RETURN;
  END IF;

  v_rule := COALESCE(NULLIF(trim(v_rule), ''), 'subscription_term');

  IF v_rule = 'calendar_month' THEN
    v_month_start := date_trunc('month', CURRENT_DATE::timestamp)::date;
    v_anchor := COALESCE(v_period_started, v_started);
    IF v_anchor IS NULL OR date_trunc('month', v_anchor::timestamp)::date < v_month_start THEN
      UPDATE public.user_plan_subscriptions u
      SET
        connects_used = 0,
        connects_granted = v_per,
        connects_period_started_at = v_month_start,
        updated_at = now()
      WHERE u.id = v_ups_id;
    END IF;
  ELSIF v_rule = 'calendar_year' THEN
    v_year_start := date_trunc('year', CURRENT_DATE::timestamp)::date;
    v_anchor := COALESCE(v_period_started, v_started);
    IF v_anchor IS NULL OR date_trunc('year', v_anchor::timestamp)::date < v_year_start THEN
      UPDATE public.user_plan_subscriptions u
      SET
        connects_used = 0,
        connects_granted = v_per,
        connects_period_started_at = v_year_start,
        updated_at = now()
      WHERE u.id = v_ups_id;
    END IF;
  END IF;

  SELECT u.connects_used, u.connects_granted INTO v_used, v_grant
  FROM public.user_plan_subscriptions u
  WHERE u.id = v_ups_id;

  v_used := COALESCE(v_used, 0);
  v_grant := COALESCE(v_grant, 0);

  IF v_used >= v_grant THEN
    RETURN QUERY SELECT NULL::integer, 'connects_exhausted'::text;
    RETURN;
  END IF;

  UPDATE public.user_plan_subscriptions u
  SET
    connects_used = u.connects_used + 1,
    updated_at = now()
  WHERE u.id = v_ups_id;

  RETURN QUERY SELECT v_ups_id, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.refund_booking_connect(bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION public.refund_booking_connect(bigint) TO service_role;
GRANT EXECUTE ON FUNCTION public.consume_client_subscription_connect(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.consume_client_subscription_connect(integer) TO service_role;

-- =============================================================================
-- 4. Expire pending requests: refund then mark expired
-- =============================================================================
CREATE OR REPLACE FUNCTION public.expire_stale_booking_requests()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT id
    FROM public.booking_requests
    WHERE status = 'pending' AND expires_at < now()
    ORDER BY id
    FOR UPDATE
  LOOP
    PERFORM public.refund_booking_connect(r.id);
    UPDATE public.booking_requests SET status = 'expired' WHERE id = r.id;
  END LOOP;
END;
$$;

-- =============================================================================
-- 5. create_booking_request — consume before insert
-- =============================================================================
CREATE OR REPLACE FUNCTION public.create_booking_request(
  p_provider_ref text,
  p_service_id integer,
  p_proposed_price double precision,
  p_location_text text DEFAULT NULL,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_message text DEFAULT NULL
)
RETURNS TABLE (request_id bigint, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_client int;
  v_provider int;
  v_new_id bigint;
  v_conn_sub int;
  v_conn_err text;
BEGIN
  PERFORM public.expire_stale_booking_requests();

  v_client := current_user_pk();
  IF v_client IS NULL THEN
    RETURN QUERY SELECT NULL::bigint, 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT u.id INTO v_provider
  FROM public.users u
  WHERE u.service_provider = true
    AND COALESCE(u.available_for_booking, true) = true
    AND (u.user_id = p_provider_ref OR u.id::text = p_provider_ref)
  LIMIT 1;

  IF v_provider IS NULL THEN
    RETURN QUERY SELECT NULL::bigint, 'provider_not_found_or_unavailable'::text;
    RETURN;
  END IF;

  IF v_provider = v_client THEN
    RETURN QUERY SELECT NULL::bigint, 'cannot_book_self'::text;
    RETURN;
  END IF;

  IF public.provider_has_incomplete_job(v_provider) THEN
    RETURN QUERY SELECT NULL::bigint, 'provider_busy'::text;
    RETURN;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.subscriptions s
    WHERE s.user_id = v_provider AND s.service_id = p_service_id
  ) THEN
    RETURN QUERY SELECT NULL::bigint, 'provider_not_subscribed_to_service'::text;
    RETURN;
  END IF;

  SELECT c.o_sub_id, c.o_err INTO v_conn_sub, v_conn_err
  FROM public.consume_client_subscription_connect(v_client) AS c;

  IF v_conn_err IS NOT NULL THEN
    RETURN QUERY SELECT NULL::bigint, v_conn_err;
    RETURN;
  END IF;

  INSERT INTO public.booking_requests (
    client_id, provider_id, service_id, proposed_price,
    location_text, latitude, longitude, message,
    status, expires_at,
    connect_consumed, connect_subscription_id
  )
  VALUES (
    v_client, v_provider, p_service_id, p_proposed_price,
    NULLIF(trim(p_location_text), ''), p_lat, p_lng, NULLIF(trim(p_message), ''),
    'pending', now() + interval '30 minutes',
    (v_conn_sub IS NOT NULL), v_conn_sub
  )
  RETURNING id INTO v_new_id;

  RETURN QUERY SELECT v_new_id, NULL::text;
END;
$$;

-- =============================================================================
-- 6. decline / cancel — refund pending connect charge
-- =============================================================================
CREATE OR REPLACE FUNCTION public.decline_booking_request(p_request_id bigint)
RETURNS TABLE (err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me int;
  br RECORD;
BEGIN
  PERFORM public.expire_stale_booking_requests();
  v_me := current_user_pk();
  IF v_me IS NULL THEN
    RETURN QUERY SELECT 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT * INTO br FROM public.booking_requests WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN QUERY SELECT 'not_found'::text;
    RETURN;
  END IF;
  IF br.provider_id <> v_me THEN
    RETURN QUERY SELECT 'not_your_request'::text;
    RETURN;
  END IF;
  IF br.status <> 'pending' THEN
    RETURN QUERY SELECT 'not_pending'::text;
    RETURN;
  END IF;

  PERFORM public.refund_booking_connect(p_request_id);
  UPDATE public.booking_requests SET status = 'declined' WHERE id = p_request_id;
  RETURN QUERY SELECT NULL::text;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_booking_request(p_request_id bigint)
RETURNS TABLE (err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me int;
  br RECORD;
BEGIN
  PERFORM public.expire_stale_booking_requests();
  v_me := current_user_pk();
  IF v_me IS NULL THEN
    RETURN QUERY SELECT 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT * INTO br FROM public.booking_requests WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN QUERY SELECT 'not_found'::text;
    RETURN;
  END IF;
  IF br.client_id <> v_me THEN
    RETURN QUERY SELECT 'not_your_request'::text;
    RETURN;
  END IF;
  IF br.status <> 'pending' THEN
    RETURN QUERY SELECT 'not_pending'::text;
    RETURN;
  END IF;

  PERFORM public.refund_booking_connect(p_request_id);
  UPDATE public.booking_requests SET status = 'cancelled' WHERE id = p_request_id;
  RETURN QUERY SELECT NULL::text;
END;
$$;

-- =============================================================================
-- 7. Optional: remaining connects for signed-in user (Profile / Engage UI)
-- =============================================================================
CREATE OR REPLACE FUNCTION public.get_my_connect_balance()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid int;
  v_exp date;
  v_used int;
  v_grant int;
  v_period_started date;
  v_started date;
  v_limit_on boolean;
  v_per integer;
  v_rule text;
  v_anchor date;
  v_month_start date;
  v_year_start date;
BEGIN
  v_uid := current_user_pk();
  IF v_uid IS NULL THEN
    RETURN jsonb_build_object('error', 'not_authenticated');
  END IF;

  SELECT
    ups.connects_used,
    ups.connects_granted,
    ups.connects_period_started_at,
    ups.started_at,
    ups.expires_at,
    sp.connects_limit_enabled,
    sp.connects_per_period,
    sp.connects_period_rule
  INTO
    v_used,
    v_grant,
    v_period_started,
    v_started,
    v_exp,
    v_limit_on,
    v_per,
    v_rule
  FROM public.user_plan_subscriptions ups
  JOIN public.subscription_plans sp ON sp.id = ups.plan_id
  WHERE ups.user_id = v_uid
    AND ups.status = 'active'
    AND ups.started_at <= CURRENT_DATE
    AND ups.expires_at >= CURRENT_DATE
  ORDER BY ups.expires_at DESC, ups.id DESC
  LIMIT 1;

  IF NOT FOUND THEN
    RETURN jsonb_build_object(
      'has_subscription', false,
      'unlimited', false,
      'remaining', NULL,
      'granted', NULL,
      'used', NULL,
      'period_rule', NULL
    );
  END IF;

  IF NOT COALESCE(v_limit_on, false) OR v_grant IS NULL THEN
    RETURN jsonb_build_object(
      'has_subscription', true,
      'unlimited', true,
      'remaining', NULL,
      'granted', NULL,
      'used', NULL,
      'period_rule', COALESCE(NULLIF(trim(v_rule), ''), 'subscription_term'),
      'expires_at', v_exp
    );
  END IF;

  v_rule := COALESCE(NULLIF(trim(v_rule), ''), 'subscription_term');

  IF v_rule = 'calendar_month' THEN
    v_month_start := date_trunc('month', CURRENT_DATE::timestamp)::date;
    v_anchor := COALESCE(v_period_started, v_started);
    IF v_anchor IS NULL OR date_trunc('month', v_anchor::timestamp)::date < v_month_start THEN
      v_used := 0;
      v_grant := COALESCE(v_per, v_grant);
    END IF;
  ELSIF v_rule = 'calendar_year' THEN
    v_year_start := date_trunc('year', CURRENT_DATE::timestamp)::date;
    v_anchor := COALESCE(v_period_started, v_started);
    IF v_anchor IS NULL OR date_trunc('year', v_anchor::timestamp)::date < v_year_start THEN
      v_used := 0;
      v_grant := COALESCE(v_per, v_grant);
    END IF;
  END IF;

  RETURN jsonb_build_object(
    'has_subscription', true,
    'unlimited', false,
    'remaining', GREATEST(0, COALESCE(v_grant, 0) - COALESCE(v_used, 0)),
    'granted', v_grant,
    'used', v_used,
    'period_rule', v_rule,
    'expires_at', v_exp
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_my_connect_balance() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_connect_balance() TO service_role;

GRANT EXECUTE ON FUNCTION public.expire_stale_booking_requests() TO authenticated;
GRANT EXECUTE ON FUNCTION public.expire_stale_booking_requests() TO service_role;
GRANT EXECUTE ON FUNCTION public.create_booking_request(text, integer, double precision, text, double precision, double precision, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_booking_request(text, integer, double precision, text, double precision, double precision, text) TO service_role;
GRANT EXECUTE ON FUNCTION public.decline_booking_request(bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION public.decline_booking_request(bigint) TO service_role;
GRANT EXECUTE ON FUNCTION public.cancel_booking_request(bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION public.cancel_booking_request(bigint) TO service_role;
