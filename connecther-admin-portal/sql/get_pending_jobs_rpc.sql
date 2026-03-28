-- RPCs for Jobs screen in ConnectHer app (pending + completed + complete).
-- Run after supabase_rls_android.sql (current_user_pk exists).

CREATE OR REPLACE FUNCTION public.get_pending_jobs()
RETURNS TABLE (
  client text,
  provider text,
  "Service" text,
  "Price" double precision,
  location text,
  job_id integer,
  rated boolean,
  score real
)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT
    COALESCE(cli.first_name || ' ' || cli.last_name, ''),
    COALESCE(prov.first_name || ' ' || prov.last_name, ''),
    COALESCE(s.name, ''),
    COALESCE(j.price, 0)::double precision,
    COALESCE(j.location, ''),
    j.id,
    EXISTS(SELECT 1 FROM job_score_card sc WHERE sc.job_id = j.id),
    COALESCE((SELECT sc.rate::real FROM job_score_card sc WHERE sc.job_id = j.id LIMIT 1), 0)
  FROM jobs j
  JOIN quotes q ON q.id = j.quote_id
  JOIN users cli ON cli.id = q.client_id
  JOIN users prov ON prov.id = q.provider_id
  JOIN services s ON s.id = q.service_id
  WHERE (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
    AND COALESCE(j.complete, false) = false;
$$;

CREATE OR REPLACE FUNCTION public.get_completed_jobs()
RETURNS TABLE (
  client text,
  provider text,
  "Service" text,
  "Price" double precision,
  location text,
  job_id integer,
  rated boolean,
  score real
)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT
    COALESCE(cli.first_name || ' ' || cli.last_name, ''),
    COALESCE(prov.first_name || ' ' || prov.last_name, ''),
    COALESCE(s.name, ''),
    COALESCE(j.price, 0)::double precision,
    COALESCE(j.location, ''),
    j.id,
    EXISTS(SELECT 1 FROM job_score_card sc WHERE sc.job_id = j.id),
    COALESCE((SELECT sc.rate::real FROM job_score_card sc WHERE sc.job_id = j.id LIMIT 1), 0)
  FROM jobs j
  JOIN quotes q ON q.id = j.quote_id
  JOIN users cli ON cli.id = q.client_id
  JOIN users prov ON prov.id = q.provider_id
  JOIN services s ON s.id = q.service_id
  WHERE (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
    AND COALESCE(j.complete, false) = true;
$$;

CREATE OR REPLACE FUNCTION public.complete_my_job(p_job_id integer)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE jobs SET complete = true
  WHERE id = p_job_id
    AND quote_id IN (
      SELECT id FROM quotes
      WHERE client_id = current_user_pk() OR provider_id = current_user_pk()
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_pending_jobs() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_completed_jobs() TO authenticated;
GRANT EXECUTE ON FUNCTION public.complete_my_job(integer) TO authenticated;
