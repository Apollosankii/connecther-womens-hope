-- Service marketplace config (radius, address detail), booking/job location_extra,
-- provider arrival photo + work start + hourly safety check-ins, storage bucket job_site_photos.

-- -----------------------------------------------------------------------------
-- 1) services: admin-configurable search radius and optional address detail schema
-- -----------------------------------------------------------------------------
ALTER TABLE public.services
  ADD COLUMN IF NOT EXISTS search_radius_meters integer NOT NULL DEFAULT 10000
    CHECK (search_radius_meters > 0 AND search_radius_meters <= 500000),
  ADD COLUMN IF NOT EXISTS require_location_detail boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS location_detail_schema jsonb NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN public.services.search_radius_meters IS 'Max distance in meters for get_providers_for_service_near (server-enforced).';
COMMENT ON COLUMN public.services.require_location_detail IS 'When true, booking must include non-empty location_extra JSON.';
COMMENT ON COLUMN public.services.location_detail_schema IS 'Optional JSON array of field hints, e.g. [{"key":"floor","label":"Floor"}].';

-- -----------------------------------------------------------------------------
-- 2) booking_requests + jobs: structured address extras (apartments, unit, etc.)
-- -----------------------------------------------------------------------------
ALTER TABLE public.booking_requests
  ADD COLUMN IF NOT EXISTS location_extra jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE public.jobs
  ADD COLUMN IF NOT EXISTS location_extra jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS arrived_at timestamptz,
  ADD COLUMN IF NOT EXISTS site_photo_path text,
  ADD COLUMN IF NOT EXISTS work_started_at timestamptz;

COMMENT ON COLUMN public.jobs.site_photo_path IS 'Storage object path in bucket job_site_photos (first folder = job id).';
COMMENT ON COLUMN public.jobs.work_started_at IS 'Provider tapped Start work; hourly safety check-ins apply until job complete.';

-- -----------------------------------------------------------------------------
-- 3) job_safety_checkins
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.job_safety_checkins (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  job_id integer NOT NULL REFERENCES public.jobs (id) ON DELETE CASCADE,
  hour_index integer NOT NULL CHECK (hour_index >= 1),
  recorded_at timestamptz NOT NULL DEFAULT now(),
  latitude double precision,
  longitude double precision,
  note text,
  UNIQUE (job_id, hour_index)
);

CREATE INDEX IF NOT EXISTS idx_job_safety_checkins_job ON public.job_safety_checkins (job_id);

ALTER TABLE public.job_safety_checkins ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS job_safety_checkins_select_participant ON public.job_safety_checkins;
CREATE POLICY job_safety_checkins_select_participant
  ON public.job_safety_checkins FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.jobs j
      JOIN public.quotes q ON q.id = j.quote_id
      WHERE j.id = job_safety_checkins.job_id
        AND (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
    )
  );

DROP POLICY IF EXISTS job_safety_checkins_insert_provider ON public.job_safety_checkins;
CREATE POLICY job_safety_checkins_insert_provider
  ON public.job_safety_checkins FOR INSERT
  TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.jobs j
      JOIN public.quotes q ON q.id = j.quote_id
      WHERE j.id = job_safety_checkins.job_id
        AND q.provider_id = current_user_pk()
        AND COALESCE(j.complete, false) = false
    )
  );

GRANT SELECT ON public.job_safety_checkins TO authenticated;
GRANT INSERT ON public.job_safety_checkins TO authenticated;

-- -----------------------------------------------------------------------------
-- 4) Storage: private bucket for arrival / site photos
-- -----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES ('job_site_photos', 'job_site_photos', false)
ON CONFLICT (id) DO UPDATE SET public = false;

-- Path convention: {job_id}/{firebase_sub}/{filename}
DROP POLICY IF EXISTS job_site_photos_insert_own_folder ON storage.objects;
CREATE POLICY job_site_photos_insert_own_folder
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'job_site_photos'
    AND split_part(name, '/', 2) = (auth.jwt()->>'sub')
    AND split_part(name, '/', 1) ~ '^[0-9]+$'
  );

DROP POLICY IF EXISTS job_site_photos_select_participants ON storage.objects;
CREATE POLICY job_site_photos_select_participants
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'job_site_photos'
    AND (
      split_part(name, '/', 2) = (auth.jwt()->>'sub')
      OR EXISTS (
        SELECT 1 FROM public.jobs j
        JOIN public.quotes q ON q.id = j.quote_id
        WHERE j.id::text = split_part(name, '/', 1)
          AND (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
      )
    )
  );

-- -----------------------------------------------------------------------------
-- 5) get_providers_for_service_near — radius from services row only (ignore client tampering)
-- -----------------------------------------------------------------------------
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

GRANT EXECUTE ON FUNCTION public.get_providers_for_service_near(integer, double precision, double precision, double precision) TO anon;
GRANT EXECUTE ON FUNCTION public.get_providers_for_service_near(integer, double precision, double precision, double precision) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_providers_for_service_near(integer, double precision, double precision, double precision) TO service_role;

-- -----------------------------------------------------------------------------
-- 6) create_booking_request — location_extra + validation when service requires detail
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.create_booking_request(text, integer, double precision, text, double precision, double precision, text);

CREATE OR REPLACE FUNCTION public.create_booking_request(
  p_provider_ref text,
  p_service_id integer,
  p_proposed_price double precision,
  p_location_text text DEFAULT NULL,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_message text DEFAULT NULL,
  p_location_extra jsonb DEFAULT '{}'::jsonb
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
  v_need_detail boolean;
  v_extra_ok boolean;
BEGIN
  PERFORM public.expire_stale_booking_requests();

  v_client := current_user_pk();
  IF v_client IS NULL THEN
    RETURN QUERY SELECT NULL::bigint, 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT COALESCE(s.require_location_detail, false)
  INTO v_need_detail
  FROM public.services s
  WHERE s.id = p_service_id;

  IF NOT FOUND THEN
    RETURN QUERY SELECT NULL::bigint, 'service_not_found'::text;
    RETURN;
  END IF;

  IF v_need_detail THEN
    v_extra_ok :=
      p_location_extra IS NOT NULL
      AND p_location_extra <> '{}'::jsonb
      AND EXISTS (
        SELECT 1
        FROM jsonb_each_text(p_location_extra) AS e(k, v)
        WHERE length(trim(both from coalesce(v, ''))) > 0
      );
    IF NOT v_extra_ok THEN
      RETURN QUERY SELECT NULL::bigint, 'location_detail_required'::text;
      RETURN;
    END IF;
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

  IF EXISTS (
    SELECT 1
    FROM public.booking_requests br
    LEFT JOIN public.jobs j ON j.id = br.job_id
    WHERE br.client_id = v_client
      AND br.provider_id = v_provider
      AND (
        br.status = 'pending'
        OR (
          br.status = 'accepted'
          AND br.job_id IS NOT NULL
          AND COALESCE(j.complete, false) = false
        )
      )
  ) THEN
    RETURN QUERY SELECT NULL::bigint, 'duplicate_booking_same_provider'::text;
    RETURN;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.jobs j
    JOIN public.quotes q ON q.id = j.quote_id
    WHERE q.client_id = v_client
      AND q.provider_id = v_provider
      AND COALESCE(j.complete, false) = false
  ) THEN
    RETURN QUERY SELECT NULL::bigint, 'duplicate_booking_same_provider'::text;
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
    location_text, latitude, longitude, message, location_extra,
    status, expires_at,
    connect_consumed, connect_subscription_id
  )
  VALUES (
    v_client, v_provider, p_service_id, p_proposed_price,
    NULLIF(trim(p_location_text), ''), p_lat, p_lng, NULLIF(trim(p_message), ''),
    COALESCE(p_location_extra, '{}'::jsonb),
    'pending', now() + interval '30 minutes',
    (v_conn_sub IS NOT NULL), v_conn_sub
  )
  RETURNING id INTO v_new_id;

  RETURN QUERY SELECT v_new_id, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.create_booking_request(text, integer, double precision, text, double precision, double precision, text, jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_booking_request(text, integer, double precision, text, double precision, double precision, text, jsonb) TO service_role;

COMMENT ON FUNCTION public.create_booking_request(text, integer, double precision, text, double precision, double precision, text, jsonb) IS
  'Creates a booking request; location_extra required when service.require_location_detail.';

-- -----------------------------------------------------------------------------
-- 7) get_my_booking_requests — include location_extra
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
    'location_extra', COALESCE(b.location_extra, '{}'::jsonb),
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
    'client_display', COALESCE(trim(both FROM coalesce(c.first_name, '') || ' ' || coalesce(c.last_name, '')), ''),
    'provider_display', COALESCE(trim(both FROM coalesce(p.first_name, '') || ' ' || coalesce(p.last_name, '')), ''),
    'created_at', b.created_at
  )
  FROM public.booking_requests b
  JOIN public.users c ON c.id = b.client_id
  JOIN public.users p ON p.id = b.provider_id
  WHERE b.client_id = current_user_pk() OR b.provider_id = current_user_pk()
  ORDER BY b.created_at DESC;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_my_booking_requests() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_booking_requests() TO service_role;

-- -----------------------------------------------------------------------------
-- 8) accept_booking_request — copy location_extra into job
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

GRANT EXECUTE ON FUNCTION public.accept_booking_request(bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION public.accept_booking_request(bigint) TO service_role;

-- -----------------------------------------------------------------------------
-- 9) get_pending_jobs / get_completed_jobs — safety fields + location_extra excerpt
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.get_pending_jobs();

CREATE OR REPLACE FUNCTION public.get_pending_jobs()
RETURNS TABLE (
  client text,
  provider text,
  "Service" text,
  "Price" double precision,
  location text,
  job_id integer,
  rated boolean,
  score real,
  my_review_submitted boolean,
  my_stars real,
  their_review_submitted boolean,
  their_stars real,
  started_at timestamptz,
  completed_at timestamptz,
  i_am_client boolean,
  location_extra jsonb,
  arrived_at timestamptz,
  work_started_at timestamptz,
  site_photo_path text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    (cli.first_name || ' ' || cli.last_name)::text AS client,
    (prov.first_name || ' ' || prov.last_name)::text AS provider,
    s.name AS "Service",
    j.price AS "Price",
    COALESCE(j.location, '')::text AS location,
    j.id AS job_id,
    false::boolean AS rated,
    0::real AS score,
    false::boolean AS my_review_submitted,
    0::real AS my_stars,
    false::boolean AS their_review_submitted,
    0::real AS their_stars,
    j.date AS started_at,
    j.completed_at AS completed_at,
    (q.client_id = current_user_pk()) AS i_am_client,
    COALESCE(j.location_extra, '{}'::jsonb) AS location_extra,
    j.arrived_at,
    j.work_started_at,
    j.site_photo_path
  FROM jobs j
  JOIN quotes q ON q.id = j.quote_id
  JOIN users cli ON cli.id = q.client_id
  JOIN users prov ON prov.id = q.provider_id
  JOIN services s ON s.id = q.service_id
  WHERE (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
    AND COALESCE(j.complete, false) = false;
$$;

DROP FUNCTION IF EXISTS public.get_completed_jobs();

CREATE OR REPLACE FUNCTION public.get_completed_jobs()
RETURNS TABLE (
  client text,
  provider text,
  "Service" text,
  "Price" double precision,
  location text,
  job_id integer,
  rated boolean,
  score real,
  my_review_submitted boolean,
  my_stars real,
  their_review_submitted boolean,
  their_stars real,
  started_at timestamptz,
  completed_at timestamptz,
  i_am_client boolean,
  location_extra jsonb,
  arrived_at timestamptz,
  work_started_at timestamptz,
  site_photo_path text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  WITH base AS (
    SELECT
      j.id AS jid,
      j.date AS j_started,
      j.completed_at AS j_completed,
      (cli.first_name || ' ' || cli.last_name)::text AS cname,
      (prov.first_name || ' ' || prov.last_name)::text AS pname,
      s.name AS sname,
      j.price AS jprice,
      COALESCE(j.location, '')::text AS jloc,
      q.client_id AS q_client,
      q.provider_id AS q_provider,
      (q.client_id = current_user_pk()) AS i_am_client,
      COALESCE(j.location_extra, '{}'::jsonb) AS jextra,
      j.arrived_at AS j_arrived,
      j.work_started_at AS j_work_started,
      j.site_photo_path AS j_site_photo
    FROM jobs j
    JOIN quotes q ON q.id = j.quote_id
    JOIN users cli ON cli.id = q.client_id
    JOIN users prov ON prov.id = q.provider_id
    JOIN services s ON s.id = q.service_id
    WHERE (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
      AND COALESCE(j.complete, false) = true
  ),
  my_rev AS (
    SELECT r.job_id, r.stars::real AS st
    FROM job_reviews r
    WHERE r.reviewer_user_id = current_user_pk()
  ),
  their_rev AS (
    SELECT r.job_id, r.stars::real AS st
    FROM job_reviews r
    WHERE r.reviewee_user_id = current_user_pk()
  )
  SELECT
    b.cname AS client,
    b.pname AS provider,
    b.sname AS "Service",
    b.jprice AS "Price",
    b.jloc AS location,
    b.jid AS job_id,
    (mr.job_id IS NOT NULL) AS rated,
    COALESCE(mr.st, 0::real) AS score,
    (mr.job_id IS NOT NULL) AS my_review_submitted,
    COALESCE(mr.st, 0::real) AS my_stars,
    (tr.job_id IS NOT NULL) AS their_review_submitted,
    COALESCE(tr.st, 0::real) AS their_stars,
    b.j_started AS started_at,
    b.j_completed AS completed_at,
    b.i_am_client AS i_am_client,
    b.jextra AS location_extra,
    b.j_arrived AS arrived_at,
    b.j_work_started AS work_started_at,
    b.j_site_photo AS site_photo_path
  FROM base b
  LEFT JOIN my_rev mr ON mr.job_id = b.jid
  LEFT JOIN their_rev tr ON tr.job_id = b.jid;
$$;

GRANT EXECUTE ON FUNCTION public.get_pending_jobs() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_pending_jobs() TO service_role;
GRANT EXECUTE ON FUNCTION public.get_completed_jobs() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_completed_jobs() TO service_role;

COMMENT ON FUNCTION public.get_pending_jobs() IS 'Incomplete jobs for current user; includes safety/location_extra fields.';

-- -----------------------------------------------------------------------------
-- 10) Provider RPCs: arrival photo path, start work, hourly check-in
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.provider_record_job_arrival(
  p_job_id integer,
  p_site_photo_path text
)
RETURNS TABLE (ok boolean, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me int;
  v_pid int;
  v_path text;
BEGIN
  v_me := current_user_pk();
  IF v_me IS NULL THEN
    RETURN QUERY SELECT false, 'not_authenticated'::text;
    RETURN;
  END IF;

  v_path := NULLIF(trim(both from coalesce(p_site_photo_path, '')), '');
  IF v_path IS NULL OR v_path LIKE '%..%' THEN
    RETURN QUERY SELECT false, 'invalid_path'::text;
    RETURN;
  END IF;

  SELECT q.provider_id INTO v_pid
  FROM public.jobs j
  JOIN public.quotes q ON q.id = j.quote_id
  WHERE j.id = p_job_id;

  IF v_pid IS NULL THEN
    RETURN QUERY SELECT false, 'job_not_found'::text;
    RETURN;
  END IF;

  IF v_pid <> v_me THEN
    RETURN QUERY SELECT false, 'not_provider'::text;
    RETURN;
  END IF;

  IF EXISTS (SELECT 1 FROM public.jobs j WHERE j.id = p_job_id AND COALESCE(j.complete, false) = true) THEN
    RETURN QUERY SELECT false, 'job_already_complete'::text;
    RETURN;
  END IF;

  IF split_part(v_path, '/', 1) <> p_job_id::text THEN
    RETURN QUERY SELECT false, 'path_job_mismatch'::text;
    RETURN;
  END IF;

  IF split_part(v_path, '/', 2) <> (auth.jwt()->>'sub') THEN
    RETURN QUERY SELECT false, 'path_user_mismatch'::text;
    RETURN;
  END IF;

  UPDATE public.jobs
  SET
    arrived_at = COALESCE(arrived_at, now()),
    site_photo_path = v_path
  WHERE id = p_job_id;

  RETURN QUERY SELECT true, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.provider_record_job_arrival(integer, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.provider_record_job_arrival(integer, text) TO service_role;

CREATE OR REPLACE FUNCTION public.provider_start_job_work(p_job_id integer)
RETURNS TABLE (ok boolean, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me int;
  v_pid int;
BEGIN
  v_me := current_user_pk();
  IF v_me IS NULL THEN
    RETURN QUERY SELECT false, 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT q.provider_id INTO v_pid
  FROM public.jobs j
  JOIN public.quotes q ON q.id = j.quote_id
  WHERE j.id = p_job_id;

  IF v_pid IS NULL THEN
    RETURN QUERY SELECT false, 'job_not_found'::text;
    RETURN;
  END IF;

  IF v_pid <> v_me THEN
    RETURN QUERY SELECT false, 'not_provider'::text;
    RETURN;
  END IF;

  IF EXISTS (SELECT 1 FROM public.jobs j WHERE j.id = p_job_id AND COALESCE(j.complete, false) = true) THEN
    RETURN QUERY SELECT false, 'job_already_complete'::text;
    RETURN;
  END IF;

  UPDATE public.jobs
  SET work_started_at = COALESCE(work_started_at, now())
  WHERE id = p_job_id;

  RETURN QUERY SELECT true, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.provider_start_job_work(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.provider_start_job_work(integer) TO service_role;

CREATE OR REPLACE FUNCTION public.provider_submit_job_checkin(
  p_job_id integer,
  p_hour_index integer,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL
)
RETURNS TABLE (ok boolean, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me int;
  v_pid int;
BEGIN
  v_me := current_user_pk();
  IF v_me IS NULL THEN
    RETURN QUERY SELECT false, 'not_authenticated'::text;
    RETURN;
  END IF;

  IF p_hour_index IS NULL OR p_hour_index < 1 THEN
    RETURN QUERY SELECT false, 'invalid_hour_index'::text;
    RETURN;
  END IF;

  SELECT q.provider_id INTO v_pid
  FROM public.jobs j
  JOIN public.quotes q ON q.id = j.quote_id
  WHERE j.id = p_job_id;

  IF v_pid IS NULL THEN
    RETURN QUERY SELECT false, 'job_not_found'::text;
    RETURN;
  END IF;

  IF v_pid <> v_me THEN
    RETURN QUERY SELECT false, 'not_provider'::text;
    RETURN;
  END IF;

  IF EXISTS (SELECT 1 FROM public.jobs j WHERE j.id = p_job_id AND COALESCE(j.complete, false) = true) THEN
    RETURN QUERY SELECT false, 'job_already_complete'::text;
    RETURN;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.jobs j WHERE j.id = p_job_id AND j.work_started_at IS NOT NULL
  ) THEN
    RETURN QUERY SELECT false, 'work_not_started'::text;
    RETURN;
  END IF;

  INSERT INTO public.job_safety_checkins (job_id, hour_index, latitude, longitude)
  VALUES (p_job_id, p_hour_index, p_lat, p_lng)
  ON CONFLICT (job_id, hour_index) DO UPDATE SET
    recorded_at = now(),
    latitude = COALESCE(EXCLUDED.latitude, job_safety_checkins.latitude),
    longitude = COALESCE(EXCLUDED.longitude, job_safety_checkins.longitude);

  RETURN QUERY SELECT true, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.provider_submit_job_checkin(integer, integer, double precision, double precision) TO authenticated;
GRANT EXECUTE ON FUNCTION public.provider_submit_job_checkin(integer, integer, double precision, double precision) TO service_role;
