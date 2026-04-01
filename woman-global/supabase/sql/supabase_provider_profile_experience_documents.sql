-- Provider profile: extended update_my_profile, submit_provider_application (title + experience),
-- get_providers_for_service includes occupation, documents INSERT/UPDATE/SELECT own row policies.
-- Run after supabase_booking_working_hours_providers_map.sql and supabase_profile_and_reports.sql.

-- =============================================================================
-- 1. update_my_profile — add title, working_hours, available_for_booking (nullable = skip)
-- =============================================================================
DROP FUNCTION IF EXISTS public.update_my_profile(text, text, text, text, text);

CREATE OR REPLACE FUNCTION public.update_my_profile(
  p_first_name text DEFAULT NULL,
  p_last_name text DEFAULT NULL,
  p_phone text DEFAULT NULL,
  p_email text DEFAULT NULL,
  p_occupation text DEFAULT NULL,
  p_title text DEFAULT NULL,
  p_working_hours text DEFAULT NULL,
  p_available_for_booking boolean DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_clerk text;
BEGIN
  v_clerk := auth.jwt()->>'sub';
  IF v_clerk IS NULL OR v_clerk = '' THEN RETURN; END IF;

  UPDATE users SET
    first_name = COALESCE(NULLIF(TRIM(p_first_name), ''), first_name),
    last_name = COALESCE(NULLIF(TRIM(p_last_name), ''), last_name),
    phone = COALESCE(NULLIF(TRIM(p_phone), ''), phone),
    email = COALESCE(NULLIF(TRIM(p_email), ''), email),
    occupation = COALESCE(NULLIF(TRIM(p_occupation), ''), occupation),
    title = COALESCE(NULLIF(TRIM(p_title), ''), title),
    working_hours = CASE
      WHEN p_working_hours IS NULL THEN working_hours
      ELSE NULLIF(TRIM(p_working_hours), '')
    END,
    available_for_booking = COALESCE(p_available_for_booking, available_for_booking)
  WHERE clerk_user_id = v_clerk;
END;
$$;

GRANT EXECUTE ON FUNCTION public.update_my_profile(text, text, text, text, text, text, text, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_my_profile(text, text, text, text, text, text, text, boolean) TO service_role;

-- =============================================================================
-- 2. get_providers_for_service — include occupation (experience)
-- =============================================================================
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
    AND COALESCE(u.available_for_booking, true) = true
    AND NOT public.provider_has_incomplete_job(u.id);
$$;

GRANT EXECUTE ON FUNCTION public.get_providers_for_service(integer) TO anon;
GRANT EXECUTE ON FUNCTION public.get_providers_for_service(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_providers_for_service(integer) TO service_role;

-- =============================================================================
-- 3. submit_provider_application — add professional title + experience (occupation)
-- =============================================================================
DROP FUNCTION IF EXISTS public.submit_provider_application(text, text, text, text, text, text, text, text, text, text);

CREATE OR REPLACE FUNCTION public.submit_provider_application(
  p_gender text DEFAULT NULL,
  p_birth_date text DEFAULT NULL,
  p_country text DEFAULT NULL,
  p_county text DEFAULT NULL,
  p_area_name text DEFAULT NULL,
  p_nat_id text DEFAULT NULL,
  p_emm_cont_1 text DEFAULT NULL,
  p_emm_cont_2 text DEFAULT NULL,
  p_service_ids text DEFAULT NULL,
  p_working_hours text DEFAULT NULL,
  p_professional_title text DEFAULT NULL,
  p_experience text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_clerk text;
  v_user_pk int;
  v_user_id_str text;
  v_service_ids int[];
BEGIN
  v_clerk := auth.jwt()->>'sub';
  IF v_clerk IS NULL OR v_clerk = '' THEN RETURN; END IF;

  SELECT id, user_id INTO v_user_pk, v_user_id_str
  FROM users WHERE clerk_user_id = v_clerk LIMIT 1;

  IF v_user_pk IS NULL THEN RETURN; END IF;

  IF p_service_ids IS NOT NULL AND trim(p_service_ids) != '' THEN
    BEGIN
      IF p_service_ids ~ '^\[.*\]$' THEN
        SELECT array_agg(elem::int) INTO v_service_ids
        FROM json_array_elements_text(p_service_ids::json) AS elem;
      ELSE
        SELECT array_agg(x::int) INTO v_service_ids
        FROM unnest(string_to_array(trim(p_service_ids), ',')) AS x
        WHERE x ~ '^\s*\d+\s*$';
      END IF;
    EXCEPTION WHEN OTHERS THEN
      v_service_ids := '{}';
    END;
  END IF;

  UPDATE users SET
    gender = NULLIF(trim(p_gender), ''),
    birth_date = CASE WHEN NULLIF(trim(p_birth_date), '') IS NOT NULL AND NULLIF(trim(p_birth_date), '') ~ '^\d{4}-\d{2}-\d{2}$'
      THEN (NULLIF(trim(p_birth_date), ''))::date ELSE birth_date END,
    country = NULLIF(trim(p_country), ''),
    county = NULLIF(trim(p_county), ''),
    area_name = NULLIF(trim(p_area_name), ''),
    nat_id = NULLIF(trim(p_nat_id), ''),
    emm_cont_1 = NULLIF(trim(p_emm_cont_1), ''),
    emm_cont_2 = NULLIF(trim(p_emm_cont_2), ''),
    working_hours = NULLIF(trim(p_working_hours), ''),
    title = COALESCE(NULLIF(trim(p_professional_title), ''), title),
    occupation = COALESCE(NULLIF(trim(p_experience), ''), occupation),
    provider_application_pending = true
  WHERE clerk_user_id = v_clerk;

  INSERT INTO provider_applications (user_id, clerk_user_id, user_id_str, status, service_ids, application_data)
  VALUES (
    v_user_pk,
    v_clerk,
    v_user_id_str,
    'pending',
    COALESCE(v_service_ids, '{}'),
    jsonb_build_object(
      'gender', NULLIF(trim(p_gender), ''),
      'birth_date', NULLIF(trim(p_birth_date), ''),
      'country', NULLIF(trim(p_country), ''),
      'county', NULLIF(trim(p_county), ''),
      'area_name', NULLIF(trim(p_area_name), ''),
      'nat_id', NULLIF(trim(p_nat_id), ''),
      'emm_cont_1', NULLIF(trim(p_emm_cont_1), ''),
      'emm_cont_2', NULLIF(trim(p_emm_cont_2), ''),
      'working_hours', NULLIF(trim(p_working_hours), ''),
      'professional_title', NULLIF(trim(p_professional_title), ''),
      'experience', NULLIF(trim(p_experience), '')
    )
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.submit_provider_application(text, text, text, text, text, text, text, text, text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.submit_provider_application(text, text, text, text, text, text, text, text, text, text, text, text) TO service_role;

-- =============================================================================
-- 4. documents — providers can manage their own rows (upload from app)
-- =============================================================================
DROP POLICY IF EXISTS "documents_insert_own" ON public.documents;
CREATE POLICY "documents_insert_own" ON public.documents
  FOR INSERT TO authenticated
  WITH CHECK (user_id = current_user_pk());

DROP POLICY IF EXISTS "documents_update_own" ON public.documents;
CREATE POLICY "documents_update_own" ON public.documents
  FOR UPDATE TO authenticated
  USING (user_id = current_user_pk());

DROP POLICY IF EXISTS "documents_select_own" ON public.documents;
CREATE POLICY "documents_select_own" ON public.documents
  FOR SELECT TO authenticated
  USING (user_id = current_user_pk());
