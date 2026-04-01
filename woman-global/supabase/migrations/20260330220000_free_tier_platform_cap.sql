-- Enforce platform_settings.free_tier_connects as a hard cap for free-tier bookings.
-- Previously stored users.free_connects_granted could stay high after admin lowered the platform cap.
-- Effective cap = LEAST(COALESCE(user_row, platform), platform). Missing platform row → 0.

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
  v_ft := COALESCE(v_ft, 0);
  IF NEW.free_connects_granted IS NULL THEN
    NEW.free_connects_granted := v_ft;
  END IF;
  IF NEW.free_connects_used IS NULL THEN
    NEW.free_connects_used := 0;
  END IF;
  RETURN NEW;
END;
$$;

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

  v_ft_default := COALESCE((SELECT ps.free_tier_connects FROM public.platform_settings ps WHERE ps.id = 1 LIMIT 1), 0);

  SELECT u.id, u.free_connects_granted, u.free_connects_used
  INTO v_ups_id, v_u_gr, v_u_used
  FROM public.users u
  WHERE u.id = p_client_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RETURN QUERY SELECT NULL::integer, NULL::text, 'not_authenticated'::text;
    RETURN;
  END IF;

  v_u_gr := LEAST(COALESCE(v_u_gr, v_ft_default), v_ft_default);
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

  v_ft_default := COALESCE((SELECT ps.free_tier_connects FROM public.platform_settings ps WHERE ps.id = 1 LIMIT 1), 0);

  SELECT u.free_connects_granted, u.free_connects_used INTO v_fg, v_fu
  FROM public.users u
  WHERE u.id = v_uid;

  v_fg := LEAST(COALESCE(v_fg, v_ft_default), v_ft_default);
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
