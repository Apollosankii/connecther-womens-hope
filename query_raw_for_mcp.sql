-- Free tier: new accounts get N connects (admin-configured) before subscribing.
-- Run after: supabase_booking_connects_enforcement.sql (replaces consume/refund/create_booking/get_my_connect_balance).
--
-- Rules:
-- - platform_settings.free_tier_connects = default grant for new users (trigger on INSERT users).
-- - Booking: if active paid subscription → subscription connect rules (unchanged). Else → free tier on users row.
-- - Refund: connect_source 'free' vs 'subscription'.

-- =============================================================================
-- 1. platform_settings (singleton row id = 1)
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.platform_settings (
  id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  free_tier_connects integer NOT NULL DEFAULT 5 CHECK (free_tier_connects >= 0),
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO public.platform_settings (id, free_tier_connects)
VALUES (1, 5)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.platform_settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_all_platform_settings" ON public.platform_settings;
CREATE POLICY "admin_all_platform_settings" ON public.platform_settings
  FOR ALL TO authenticated
  USING (public.is_admin())
  WITH CHECK (public.is_admin());

COMMENT ON TABLE public.platform_settings IS 'Singleton app config; free_tier_connects seeds new users.';

-- =============================================================================
-- 2. users: free tier counters
-- =============================================================================
ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS free_connects_granted integer NULL;

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS free_connects_used integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN public.users.free_connects_granted IS 'Free-tier cap for this account; NULL until first booking or trigger fills from platform_settings.';
COMMENT ON COLUMN public.users.free_connects_used IS 'Free-tier connects consumed (booking requests).';

-- Backfill existing users who have NULL granted (one-time)
UPDATE public.users u
SET
  free_connects_granted = COALESCE(
    (SELECT ps.free_tier_connects FROM public.platform_settings ps WHERE ps.id = 1 LIMIT 1),
    5
  )
WHERE u.free_connects_granted IS NULL;

CREATE OR REPLACE FUNCTION public.users_init_free_tier()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_ft integer;
BEGIN
  SELECT ps.free_tier_connects INTO v_ft FROM public.platform_settings ps WHERE ps.id = 1 LIMIT 1;
  v_ft := COALESCE(v_ft, 5);
  IF NEW.free_connects_granted IS NULL THEN
    NEW.free_connects_granted := v_ft;
  END IF;
  IF NEW.free_connects_used IS NULL THEN
    NEW.free_connects_used := 0;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_users_init_free_tier ON public.users;
CREATE TRIGGER tr_users_init_free_tier
  BEFORE INSERT ON public.users
  FOR EACH ROW
  EXECUTE PROCEDURE public.users_init_free_tier();

-- =============================================================================
-- 3. booking_requests: connect_source
-- =============================================================================
ALTER TABLE public.booking_requests
  ADD COLUMN IF NOT EXISTS connect_source text NULL;

ALTER TABLE public.booking_requests DROP CONSTRAINT IF EXISTS booking_requests_connect_source_check;
ALTER TABLE public.booking_requests
  ADD CONSTRAINT booking_requests_connect_source_check
  CHECK (connect_source IS NULL OR connect_source IN ('free', 'subscription'));

COMMENT ON COLUMN public.booking_requests.connect_source IS 'free = users.free_connects_used; subscription = user_plan_subscriptions.';

-- =============================================================================
-- 4. refund_booking_connect (free + subscription)
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
  IF NOT COALESCE(br.connect_consumed, false) THEN
    RETURN;
  END IF;

  IF br.connect_subscription_id IS NOT NULL THEN
    UPDATE public.user_plan_subscriptions ups
    SET
      connects_used = GREATEST(0, ups.connects_used - 1),
      updated_at = now()
    WHERE ups.id = br.connect_subscription_id;
  ELSIF COALESCE(br.connect_source, 'free') = 'free' THEN
    UPDATE public.users u
    SET free_connects_used = GREATEST(0, COALESCE(u.free_connects_used, 0) - 1)
    WHERE u.id = br.client_id;
  END IF;

  UPDATE public.booking_requests
  SET connect_consumed = false
  WHERE id = p_booking_id;
END;
$$;

-- =============================================================================
-- 5. consume: subscription first, else free tier
-- =============================================================================
DROP FUNCTION IF EXISTS public.consume_client_subscription_connect(integer);

CREATE OR REPLACE FUNCTION public.consume_client_subscription_connect(p_client_id integer)
RETURNS TABLE (o_sub_id integer, o_source text, o_err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_has_sub boolean;
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
  v_ft_default integer;
  v_u_gr integer;
  v_u_used integer;
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

  v_has_sub := FOUND AND v_ups_id IS NOT NULL;

  IF v_has_sub THEN
    IF NOT COALESCE(v_limit_on, false) OR v_grant IS NULL THEN
      RETURN QUERY SELECT NULL::integer, NULL::text, NULL::text;
      RETURN;
    END IF;

    IF v_per IS NULL OR v_per < 0 THEN
      RETURN QUERY SELECT NULL::integer, NULL::text, NULL::text;
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
      RETURN QUERY SELECT NULL::integer, NULL::text, 'connects_exhausted'::text;
      RETURN;
    END IF;

    UPDATE public.user_plan_subscriptions u
    SET
      connects_used = u.connects_used + 1,
      updated_at = now()
    WHERE u.id = v_ups_id;

    RETURN QUERY SELECT v_ups_id, 'subscription'::text, NULL::text;
    RETURN;
  END IF;

  -- No active subscription: free tier on users
  v_ft_default := COALESCE((SELECT ps.free_tier_connects FROM public.platform_settings ps WHERE ps.id = 1 LIMIT 1), 5);

  SELECT u.id, u.free_connects_granted, u.free_connects_used
  INTO v_ups_id, v_u_gr, v_u_used
  FROM public.users u
  WHERE u.id = p_client_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RETURN QUERY SELECT NULL::integer, NULL::text, 'not_authenticated'::text;
    RETURN;
  END IF;

  v_u_gr := COALESCE(v_u_gr, v_ft_default);
  v_u_used := COALESCE(v_u_used, 0);

  IF v_u_gr - v_u_used <= 0 THEN
    RETURN QUERY SELECT NULL::integer, NULL::text, 'free_tier_exhausted'::text;
    RETURN;
  END IF;

  UPDATE public.users u
  SET
    free_connects_granted = v_u_gr,
    free_connects_used = v_u_used + 1
  WHERE u.id = p_client_id;

  RETURN QUERY SELECT NULL::integer, 'free'::text, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.consume_client_subscription_connect(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.consume_client_subscription_connect(integer) TO service_role;

-- =============================================================================
-- 6. create_booking_request
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
  v_conn_src text;
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

  SELECT c.o_sub_id, c.o_source, c.o_err INTO v_conn_sub, v_conn_src, v_conn_err
  FROM public.consume_client_subscription_connect(v_client) AS c;

  IF v_conn_err IS NOT NULL THEN
    RETURN QUERY SELECT NULL::bigint, v_conn_err;
    RETURN;
  END IF;

  INSERT INTO public.booking_requests (
    client_id, provider_id, service_id, proposed_price,
    location_text, latitude, longitude, message,
    status, expires_at,
    connect_consumed, connect_subscription_id, connect_source
  )
  VALUES (
    v_client, v_provider, p_service_id, p_proposed_price,
    NULLIF(trim(p_location_text), ''), p_lat, p_lng, NULLIF(trim(p_message), ''),
    'pending', now() + interval '30 minutes',
    (v_conn_src IS NOT NULL),
    v_conn_sub,
    v_conn_src
  )
  RETURNING id INTO v_new_id;

  RETURN QUERY SELECT v_new_id, NULL::text;
END;
$$;

-- =============================================================================
-- 7. get_my_connect_balance (subscription + free preview)
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
  v_ft_default integer;
  v_fg int;
  v_fu int;
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

  IF FOUND THEN
    IF NOT COALESCE(v_limit_on, false) OR v_grant IS NULL THEN
      RETURN jsonb_build_object(
        'has_subscription', true,
        'on_free_tier', false,
        'unlimited', true,
        'remaining', NULL,
        'granted', NULL,
        'used', NULL,
        'period_rule', COALESCE(NULLIF(trim(v_rule), ''), 'subscription_term'),
        'expires_at', v_exp,
        'free_remaining', NULL,
        'free_granted', NULL,
        'free_used', NULL
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
      'on_free_tier', false,
      'unlimited', false,
      'remaining', GREATEST(0, COALESCE(v_grant, 0) - COALESCE(v_used, 0)),
      'granted', v_grant,
      'used', v_used,
      'period_rule', v_rule,
      'expires_at', v_exp,
      'free_remaining', NULL,
      'free_granted', NULL,
      'free_used', NULL
    );
  END IF;

  -- No active subscription: show free tier
  v_ft_default := COALESCE((SELECT ps.free_tier_connects FROM public.platform_settings ps WHERE ps.id = 1 LIMIT 1), 5);

  SELECT u.free_connects_granted, u.free_connects_used INTO v_fg, v_fu
  FROM public.users u
  WHERE u.id = v_uid;

  v_fg := COALESCE(v_fg, v_ft_default);
  v_fu := COALESCE(v_fu, 0);

  RETURN jsonb_build_object(
    'has_subscription', false,
    'on_free_tier', true,
    'unlimited', false,
    'remaining', GREATEST(0, v_fg - v_fu),
    'granted', v_fg,
    'used', v_fu,
    'period_rule', 'free_tier',
    'expires_at', NULL,
    'free_remaining', GREATEST(0, v_fg - v_fu),
    'free_granted', v_fg,
    'free_used', v_fu
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_my_connect_balance() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_connect_balance() TO service_role;
GRANT EXECUTE ON FUNCTION public.create_booking_request(text, integer, double precision, text, double precision, double precision, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_booking_request(text, integer, double precision, text, double precision, double precision, text) TO service_role;
