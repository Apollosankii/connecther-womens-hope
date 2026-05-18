-- Provider suspension: admin can suspend providers, cancel open bookings, hide from discovery.

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS provider_suspended boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS provider_suspended_at timestamptz;

COMMENT ON COLUMN public.users.provider_suspended IS
  'When true, provider is hidden from search and cannot accept bookings.';
COMMENT ON COLUMN public.users.provider_suspended_at IS
  'When the account was last suspended by an admin.';

-- Extend admin user list RPC.
DROP FUNCTION IF EXISTS public.get_users_for_admin();

CREATE OR REPLACE FUNCTION public.get_users_for_admin()
RETURNS TABLE (
  id integer,
  user_id text,
  first_name text,
  last_name text,
  email text,
  phone text,
  nat_id text,
  wh_badge boolean,
  service_provider boolean,
  provider_suspended boolean
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT u.id, u.user_id, u.first_name, u.last_name, u.email, u.phone, u.nat_id,
         COALESCE(u."WH_badge", false) AS wh_badge,
         COALESCE(u.service_provider, false) AS service_provider,
         COALESCE(u.provider_suspended, false) AS provider_suspended
  FROM users u
  WHERE is_admin();
$$;

GRANT EXECUTE ON FUNCTION public.get_users_for_admin() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_users_for_admin() TO anon;
GRANT EXECUTE ON FUNCTION public.get_users_for_admin() TO service_role;

-- Suspend / unsuspend provider (admin only). On suspend: cancel pending + accepted bookings.
CREATE OR REPLACE FUNCTION public.admin_set_provider_suspended(
  p_user_id text,
  p_suspended boolean
)
RETURNS TABLE (ok boolean, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_provider_id integer;
  br RECORD;
  v_old_status text;
BEGIN
  IF NOT public.is_admin() THEN
    RETURN QUERY SELECT false, 'not_admin'::text;
    RETURN;
  END IF;

  SELECT u.id INTO v_provider_id
  FROM public.users u
  WHERE u.user_id = p_user_id
    AND COALESCE(u.service_provider, false) = true
  LIMIT 1;

  IF v_provider_id IS NULL THEN
    RETURN QUERY SELECT false, 'provider_not_found'::text;
    RETURN;
  END IF;

  IF COALESCE(p_suspended, false) THEN
  FOR br IN
    SELECT br.id, br.client_id, br.provider_id, br.status, br.job_id, br.service_id
    FROM public.booking_requests br
    WHERE br.provider_id = v_provider_id
      AND br.status IN ('pending', 'accepted')
    FOR UPDATE
  LOOP
    v_old_status := br.status;

    IF v_old_status = 'pending' THEN
      PERFORM public.refund_booking_connect(br.id);
    END IF;

    IF br.job_id IS NOT NULL THEN
      UPDATE public.jobs j
      SET complete = true,
          completed_at = COALESCE(j.completed_at, now())
      WHERE j.id = br.job_id
        AND COALESCE(j.complete, false) = false;
    END IF;

    UPDATE public.booking_requests
    SET status = 'cancelled'
    WHERE id = br.id;

    -- Trigger notifies provider; pending cancellations do not notify client — notify here.
    IF v_old_status = 'pending' THEN
      PERFORM public.notify_app_user(
        br.client_id,
        'Booking cancelled',
        'This booking was cancelled because the provider account was suspended.',
        'booking_cancelled',
        jsonb_build_object(
          'request_id', br.id::text,
          'service_id', br.service_id::text,
          'job_id', COALESCE(br.job_id::text, '')
        )
      );
    END IF;
  END LOOP;

    UPDATE public.users
    SET provider_suspended = true,
        provider_suspended_at = now(),
        available_for_booking = false
    WHERE id = v_provider_id;
  ELSE
    UPDATE public.users
    SET provider_suspended = false,
        provider_suspended_at = NULL
    WHERE id = v_provider_id;
  END IF;

  RETURN QUERY SELECT true, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.admin_set_provider_suspended(text, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_set_provider_suspended(text, boolean) TO service_role;

-- Discovery: exclude suspended providers.
CREATE OR REPLACE FUNCTION public.get_providers_for_service(p_service_id integer)
RETURNS SETOF jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT jsonb_build_object(
    'first_name', u.first_name,
    'last_name', u.last_name,
    'title', u.title,
    'user_name', u.user_id,
    'phone', u.phone,
    'nat_id', u.nat_id,
    'dob', u.birth_date,
    'gender', u.gender,
    'pic', u.prof_pic,
    'WH Badge', u."WH_badge",
    'area_name', u.area_name,
    'country', u.country,
    'county', u.county,
    'emm_cont_1', u.emm_cont_1,
    'emm_cont_2', u.emm_cont_2,
    'id', u.id,
    'latitude', ll.latitude,
    'longitude', ll.longitude,
    'working_hours', u.working_hours,
    'occupation', u.occupation
  )
  FROM public.users u
  JOIN public.subscriptions s ON s.user_id = u.id AND s.service_id = p_service_id
  LEFT JOIN public.live_location ll ON ll.user_id = u.id
  WHERE u.service_provider = true
    AND COALESCE(u.provider_suspended, false) = false
    AND COALESCE(u.available_for_booking, true) = true
    AND NOT public.provider_has_incomplete_job(u.id);
$$;

CREATE OR REPLACE FUNCTION public.get_providers_for_service_near(
  p_service_id integer,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_radius_meters double precision DEFAULT NULL
)
RETURNS SETOF jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_radius double precision;
BEGIN
  IF p_lat IS NULL OR p_lng IS NULL THEN
    RETURN QUERY SELECT * FROM public.get_providers_for_service(p_service_id);
    RETURN;
  END IF;

  SELECT COALESCE(s.search_radius_meters, 10000)::double precision
  INTO v_radius
  FROM public.services s
  WHERE s.id = p_service_id;

  IF v_radius IS NULL OR v_radius <= 0 THEN
    v_radius := 10000;
  END IF;

  RETURN QUERY
  SELECT jsonb_build_object(
    'first_name', u.first_name,
    'last_name', u.last_name,
    'title', u.title,
    'user_name', u.user_id,
    'phone', u.phone,
    'nat_id', u.nat_id,
    'dob', u.birth_date,
    'gender', u.gender,
    'pic', u.prof_pic,
    'WH Badge', u."WH_badge",
    'area_name', u.area_name,
    'country', u.country,
    'county', u.county,
    'emm_cont_1', u.emm_cont_1,
    'emm_cont_2', u.emm_cont_2,
    'id', u.id,
    'latitude', ll.latitude,
    'longitude', ll.longitude,
    'working_hours', u.working_hours,
    'occupation', u.occupation
  )
  FROM public.users u
  JOIN public.subscriptions sub ON sub.user_id = u.id AND sub.service_id = p_service_id
  INNER JOIN LATERAL (
    SELECT ll.latitude, ll.longitude
    FROM public.live_location ll
    WHERE ll.user_id = u.id
    ORDER BY ll.id DESC
    LIMIT 1
  ) ll ON TRUE
  WHERE u.service_provider = true
    AND COALESCE(u.provider_suspended, false) = false
    AND COALESCE(u.available_for_booking, true) = true
    AND NOT public.provider_has_incomplete_job(u.id)
    AND ll.latitude IS NOT NULL
    AND ll.longitude IS NOT NULL
    AND ST_DWithin(
      ST_SetSRID(ST_MakePoint(ll.longitude, ll.latitude), 4326)::geography,
      ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::geography,
      v_radius
    );
END;
$$;

-- Block accept when suspended.
CREATE OR REPLACE FUNCTION public.accept_booking_request(p_request_id bigint)
RETURNS TABLE (quote_id text, chat_code text, job_id text, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me int;
  br RECORD;
  v_quote_id int;
  v_chat_code text;
  v_job_id int;
  v_loc text;
  v_geo text := '';
  v_maps text := '';
  v_available boolean;
  v_suspended boolean;
BEGIN
  PERFORM public.expire_stale_booking_requests();

  v_me := current_user_pk();
  IF v_me IS NULL THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT * INTO br FROM public.booking_requests WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'not_found'::text;
    RETURN;
  END IF;

  IF br.provider_id <> v_me THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'not_your_request'::text;
    RETURN;
  END IF;

  IF br.status <> 'pending' THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'not_pending'::text;
    RETURN;
  END IF;

  IF br.expires_at < now() THEN
    UPDATE booking_requests SET status = 'expired' WHERE id = p_request_id;
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'expired'::text;
    RETURN;
  END IF;

  SELECT COALESCE(u.available_for_booking, true), COALESCE(u.provider_suspended, false)
  INTO v_available, v_suspended
  FROM public.users u
  WHERE u.id = br.provider_id;

  IF v_suspended THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'provider_suspended'::text;
    RETURN;
  END IF;

  IF NOT COALESCE(v_available, true) THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'provider_offline'::text;
    RETURN;
  END IF;

  IF public.provider_has_incomplete_job(br.provider_id) THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'provider_busy'::text;
    RETURN;
  END IF;

  IF br.latitude IS NOT NULL AND br.longitude IS NOT NULL THEN
    v_geo := round(br.latitude::numeric, 6)::text || ',' || round(br.longitude::numeric, 6)::text;
    v_maps := 'https://www.google.com/maps/search/?api=1&query=' || v_geo;
  END IF;

  v_loc := NULLIF(trim(COALESCE(br.location_text, '')), '');
  IF v_loc IS NOT NULL AND v_geo <> '' THEN
    v_loc := v_loc || ' · ' || v_geo;
  ELSIF v_loc IS NULL AND v_geo <> '' THEN
    v_loc := v_geo;
  ELSIF v_loc IS NULL THEN
    v_loc := 'Location not specified';
  END IF;

  IF v_maps <> '' THEN
    v_loc := v_loc || E'\n' || v_maps;
  END IF;

  INSERT INTO quotes (client_id, provider_id, service_id, quote_code, converted)
  VALUES (
    br.client_id,
    br.provider_id,
    br.service_id,
    'Q' || substr(md5(random()::text || clock_timestamp()::text), 1, 12),
    true
  )
  RETURNING id INTO v_quote_id;

  v_chat_code := 'CH' || substr(md5(random()::text || clock_timestamp()::text), 1, 14);
  INSERT INTO chats (quote_id, chat_code) VALUES (v_quote_id, v_chat_code);

  INSERT INTO jobs (quote_id, price, location, location_extra, date, complete)
  VALUES (
    v_quote_id,
    br.proposed_price,
    v_loc,
    COALESCE(br.location_extra, '{}'::jsonb),
    now(),
    false
  )
  RETURNING id INTO v_job_id;

  UPDATE booking_requests
  SET status = 'accepted', quote_id = v_quote_id, job_id = v_job_id
  WHERE id = p_request_id;

  RETURN QUERY SELECT v_quote_id::text, v_chat_code, v_job_id::text, NULL::text;
END;
$$;
