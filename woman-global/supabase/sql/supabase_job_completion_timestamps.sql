-- Job lifecycle: record when completed; only the client (seeker) may mark a job complete.
-- Extends get_pending_jobs / get_completed_jobs with started_at + completed_at for app UI.
-- Apply after 20260401120000_job_reviews_bidirectional.sql (this replaces those RPC definitions).

ALTER TABLE public.jobs
  ADD COLUMN IF NOT EXISTS completed_at timestamptz;

COMMENT ON COLUMN public.jobs.completed_at IS 'Set when complete becomes true (seeker completes via complete_my_job).';
COMMENT ON COLUMN public.jobs.date IS 'When the job row was created (e.g. booking accepted).';

CREATE OR REPLACE FUNCTION public.complete_my_job(p_job_id integer)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE public.jobs j
  SET
    complete = true,
    completed_at = COALESCE(j.completed_at, now())
  FROM public.quotes q
  WHERE j.id = p_job_id
    AND j.quote_id = q.id
    AND q.client_id = current_user_pk()
    AND COALESCE(j.complete, false) = false;
END;
$$;

GRANT EXECUTE ON FUNCTION public.complete_my_job(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.complete_my_job(integer) TO service_role;

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
  i_am_client boolean
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
    (q.client_id = current_user_pk()) AS i_am_client
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
  i_am_client boolean
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
      (q.client_id = current_user_pk()) AS i_am_client
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
    b.i_am_client AS i_am_client
  FROM base b
  LEFT JOIN my_rev mr ON mr.job_id = b.jid
  LEFT JOIN their_rev tr ON tr.job_id = b.jid;
$$;

COMMENT ON FUNCTION public.complete_my_job(integer) IS 'Seeker (quote client_id) only: marks job complete and sets completed_at.';
COMMENT ON FUNCTION public.get_pending_jobs() IS 'Incomplete jobs for current user; i_am_client distinguishes seeker vs provider rows.';
COMMENT ON FUNCTION public.get_completed_jobs() IS 'Completed jobs for current user; i_am_client for UI (labels, rate flow).';
