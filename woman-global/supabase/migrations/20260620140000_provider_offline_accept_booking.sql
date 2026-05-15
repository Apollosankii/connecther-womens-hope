-- Providers who toggle offline cannot accept pending booking requests.

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

  SELECT COALESCE(u.available_for_booking, true) INTO v_available
  FROM public.users u
  WHERE u.id = br.provider_id;

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
