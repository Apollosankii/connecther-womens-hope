-- Ensures incomplete-job = busy workflow and adds RPCs for app profile UI.
-- Incomplete job: jobs.join quotes where provider_id = user and complete = false.

CREATE OR REPLACE FUNCTION public.provider_has_incomplete_job(p_provider_id integer)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.jobs j
    JOIN public.quotes q ON q.id = j.quote_id
    WHERE q.provider_id = p_provider_id
      AND COALESCE(j.complete, false) = false
  );
$$;

GRANT EXECUTE ON FUNCTION public.provider_has_incomplete_job(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.provider_has_incomplete_job(integer) TO service_role;

-- True when provider can appear in marketplace and accept new bookings (manual toggle + not busy).
CREATE OR REPLACE FUNCTION public.provider_is_bookable(p_user_id integer)
RETURNS TABLE (bookable boolean)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.users u
    WHERE u.id = p_user_id
      AND COALESCE(u.service_provider, false) = true
      AND COALESCE(u.available_for_booking, true) = true
      AND NOT public.provider_has_incomplete_job(u.id)
  ) AS bookable;
$$;

GRANT EXECUTE ON FUNCTION public.provider_is_bookable(integer) TO anon;
GRANT EXECUTE ON FUNCTION public.provider_is_bookable(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.provider_is_bookable(integer) TO service_role;

-- Current user (provider): has at least one incomplete job as quote.provider_id.
CREATE OR REPLACE FUNCTION public.my_provider_has_active_job()
RETURNS TABLE (busy boolean)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    CASE
      WHEN current_user_pk() IS NULL THEN false
      ELSE public.provider_has_incomplete_job(current_user_pk())
    END AS busy;
$$;

GRANT EXECUTE ON FUNCTION public.my_provider_has_active_job() TO authenticated;
GRANT EXECUTE ON FUNCTION public.my_provider_has_active_job() TO service_role;

COMMENT ON FUNCTION public.provider_has_incomplete_job(integer) IS 'True when provider has an accepted job not yet marked complete (hidden from new bookings).';
