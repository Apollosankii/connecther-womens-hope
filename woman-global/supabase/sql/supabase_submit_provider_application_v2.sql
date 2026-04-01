-- Updated submit_provider_application: inserts into provider_applications with service_ids.
-- Requires provider_applications table (run supabase_provider_applications.sql first).
-- App passes p_service_ids as JSON array string e.g. '[1,2,3]' or comma-separated '1,2,3'.

CREATE OR REPLACE FUNCTION public.submit_provider_application(
  p_gender text DEFAULT NULL,
  p_birth_date text DEFAULT NULL,
  p_country text DEFAULT NULL,
  p_county text DEFAULT NULL,
  p_area_name text DEFAULT NULL,
  p_nat_id text DEFAULT NULL,
  p_emm_cont_1 text DEFAULT NULL,
  p_emm_cont_2 text DEFAULT NULL,
  p_service_ids text DEFAULT NULL
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

  -- Resolve user
  SELECT id, user_id INTO v_user_pk, v_user_id_str
  FROM users WHERE clerk_user_id = v_clerk LIMIT 1;

  IF v_user_pk IS NULL THEN RETURN; END IF;

  -- Parse service_ids: '[1,2,3]' or '1,2,3'
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

  -- Update users with profile data
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
    provider_application_pending = true
  WHERE clerk_user_id = v_clerk;

  -- Insert into provider_applications (source of truth for tracking)
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
      'emm_cont_2', NULLIF(trim(p_emm_cont_2), '')
    )
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.submit_provider_application(text, text, text, text, text, text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.submit_provider_application(text, text, text, text, text, text, text, text, text) TO service_role;
