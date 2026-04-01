-- Chat list: always expose the *other* party's name and avatar (fixes client/provider header mix-ups).
-- Booking: Google Maps search URL in accepted job location + RPC payload for list views.
-- Storage: ensure provider-docs bucket exists (Android uploads verification docs here).

-- -----------------------------------------------------------------------------
-- 1) Storage bucket for provider verification documents (private)
-- -----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES ('provider-docs', 'provider-docs', false)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2) get_conversations — append peer_name / peer_pic for the non–current-user party
-- -----------------------------------------------------------------------------
-- OUT parameters changed: must drop before create (PostgreSQL).
DROP FUNCTION IF EXISTS public.get_conversations();

CREATE OR REPLACE FUNCTION public.get_conversations()
RETURNS TABLE (
  quote_code text,
  chat_id text,
  provider text,
  client text,
  service text,
  msg_text text,
  msg_time text,
  peer_name text,
  peer_pic text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    q.quote_code,
    c.chat_code,
    trim(both FROM coalesce(prov.first_name, '') || ' ' || coalesce(prov.last_name, '')),
    trim(both FROM coalesce(cli.first_name, '') || ' ' || coalesce(cli.last_name, '')),
    s.name,
    coalesce(
      (SELECT m.content FROM messages m WHERE m.chat_id = c.id ORDER BY m.time DESC LIMIT 1),
      'Start Chat..'
    ),
    coalesce(
      (SELECT to_char(m.time, 'HH12:MI AM') FROM messages m WHERE m.chat_id = c.id ORDER BY m.time DESC LIMIT 1),
      ''
    ),
    CASE
      WHEN q.client_id = current_user_pk() THEN
        trim(both FROM coalesce(prov.first_name, '') || ' ' || coalesce(prov.last_name, ''))
      ELSE
        trim(both FROM coalesce(cli.first_name, '') || ' ' || coalesce(cli.last_name, ''))
    END,
    CASE
      WHEN q.client_id = current_user_pk() THEN prov.prof_pic
      ELSE cli.prof_pic
    END
  FROM quotes q
  JOIN chats c ON c.quote_id = q.id
  JOIN users prov ON prov.id = q.provider_id
  JOIN users cli ON cli.id = q.client_id
  JOIN services s ON s.id = q.service_id
  WHERE (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
    AND q.converted = false
    AND c.chat_code IS NOT NULL
  ORDER BY q.id DESC;
$$;

GRANT EXECUTE ON FUNCTION public.get_conversations() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_conversations() TO service_role;

-- -----------------------------------------------------------------------------
-- 3) get_chat_header — single row for ChatActivity when intent extras are missing
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_chat_header(p_chat_code text)
RETURNS TABLE (peer_name text, peer_pic text, service_name text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    CASE
      WHEN q.client_id = current_user_pk() THEN
        trim(both FROM coalesce(prov.first_name, '') || ' ' || coalesce(prov.last_name, ''))
      ELSE
        trim(both FROM coalesce(cli.first_name, '') || ' ' || coalesce(cli.last_name, ''))
    END,
    CASE
      WHEN q.client_id = current_user_pk() THEN prov.prof_pic
      ELSE cli.prof_pic
    END,
    s.name
  FROM chats c
  JOIN quotes q ON q.id = c.quote_id
  JOIN users prov ON prov.id = q.provider_id
  JOIN users cli ON cli.id = q.client_id
  JOIN services s ON s.id = q.service_id
  WHERE c.chat_code = p_chat_code
    AND (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
  LIMIT 1;
$$;

GRANT EXECUTE ON FUNCTION public.get_chat_header(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_chat_header(text) TO service_role;

-- -----------------------------------------------------------------------------
-- 4) get_my_booking_requests — maps_url when coordinates are present
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_my_booking_requests()
RETURNS SETOF jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public.expire_stale_booking_requests();
  RETURN QUERY
  SELECT jsonb_build_object(
    'id', b.id,
    'status', b.status,
    'role', CASE WHEN b.client_id = current_user_pk() THEN 'client' ELSE 'provider' END,
    'expires_at', b.expires_at,
    'proposed_price', b.proposed_price,
    'location_text', b.location_text,
    'latitude', b.latitude,
    'longitude', b.longitude,
    'maps_url', CASE
      WHEN b.latitude IS NOT NULL AND b.longitude IS NOT NULL THEN
        format(
          'https://www.google.com/maps/search/?api=1&query=%s,%s',
          round(b.latitude::numeric, 7)::text,
          round(b.longitude::numeric, 7)::text
        )
      ELSE NULL
    END,
    'message', b.message,
    'service_id', b.service_id,
    'client_display', coalesce(trim(both FROM coalesce(c.first_name, '') || ' ' || coalesce(c.last_name, '')), ''),
    'provider_display', coalesce(trim(both FROM coalesce(p.first_name, '') || ' ' || coalesce(p.last_name, '')), ''),
    'created_at', b.created_at
  )
  FROM booking_requests b
  JOIN users c ON c.id = b.client_id
  JOIN users p ON p.id = b.provider_id
  WHERE b.client_id = current_user_pk() OR b.provider_id = current_user_pk()
  ORDER BY b.created_at DESC;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_my_booking_requests() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_booking_requests() TO service_role;

-- -----------------------------------------------------------------------------
-- 5) accept_booking_request — append Google Maps link line to jobs.location
-- -----------------------------------------------------------------------------
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
BEGIN
  PERFORM public.expire_stale_booking_requests();

  v_me := current_user_pk();
  IF v_me IS NULL THEN
    RETURN QUERY SELECT NULL::text, NULL::text, NULL::text, 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT * INTO br FROM booking_requests WHERE id = p_request_id FOR UPDATE;
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

  INSERT INTO jobs (quote_id, price, location, date, complete)
  VALUES (v_quote_id, br.proposed_price, v_loc, now(), false)
  RETURNING id INTO v_job_id;

  UPDATE booking_requests
  SET status = 'accepted', quote_id = v_quote_id, job_id = v_job_id
  WHERE id = p_request_id;

  RETURN QUERY SELECT v_quote_id::text, v_chat_code, v_job_id::text, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.accept_booking_request(bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION public.accept_booking_request(bigint) TO service_role;
