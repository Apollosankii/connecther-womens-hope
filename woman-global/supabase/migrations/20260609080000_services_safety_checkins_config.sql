-- Admin-configurable service safety check-ins (per service).
-- Providers receive periodic "I'm OK" reminders only when enabled for the booked service.

ALTER TABLE public.services
  ADD COLUMN IF NOT EXISTS safety_checkins_required boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS safety_checkin_interval_min integer NOT NULL DEFAULT 60
    CHECK (safety_checkin_interval_min >= 15 AND safety_checkin_interval_min <= 240);

COMMENT ON COLUMN public.services.safety_checkins_required IS 'When true, provider safety check-ins apply during active jobs for this service.';
COMMENT ON COLUMN public.services.safety_checkin_interval_min IS 'Check-in reminder interval in minutes (15–240).';

-- Extend get_pending_jobs / get_completed_jobs RPCs so the mobile app can decide whether to schedule reminders.
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
  site_photo_path text,
  service_id integer,
  safety_checkins_required boolean,
  safety_checkin_interval_min integer
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
    j.site_photo_path,
    q.service_id AS service_id,
    COALESCE(s.safety_checkins_required, false) AS safety_checkins_required,
    COALESCE(s.safety_checkin_interval_min, 60) AS safety_checkin_interval_min
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
  site_photo_path text,
  service_id integer,
  safety_checkins_required boolean,
  safety_checkin_interval_min integer
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
      j.site_photo_path AS j_site_photo,
      q.service_id AS sid,
      COALESCE(s.safety_checkins_required, false) AS s_safe,
      COALESCE(s.safety_checkin_interval_min, 60) AS s_int
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
    b.j_site_photo AS site_photo_path,
    b.sid AS service_id,
    b.s_safe AS safety_checkins_required,
    b.s_int AS safety_checkin_interval_min
  FROM base b
  LEFT JOIN my_rev mr ON mr.job_id = b.jid
  LEFT JOIN their_rev tr ON tr.job_id = b.jid;
$$;

GRANT EXECUTE ON FUNCTION public.get_pending_jobs() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_pending_jobs() TO service_role;
GRANT EXECUTE ON FUNCTION public.get_completed_jobs() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_completed_jobs() TO service_role;

