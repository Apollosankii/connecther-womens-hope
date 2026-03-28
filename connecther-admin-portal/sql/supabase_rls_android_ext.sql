-- Extension to supabase_rls_android.sql: RPCs, Storage, subscriptions RLS.
-- Run after supabase_rls_android.sql.

-- =============================================================================
-- 1. Subscriptions: App users can read (for provider discovery)
-- =============================================================================
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "subscriptions_select_authenticated" ON subscriptions;
CREATE POLICY "subscriptions_select_authenticated" ON subscriptions FOR SELECT
  USING (true);  -- Any authenticated user can see who is subscribed to what

-- =============================================================================
-- 2. RPC: get_providers_for_service(service_id int)
-- Returns users (provider profile shape) who are service_provider and subscribed to service_id.
-- Simplified: no PostGIS "near me" for now; returns all providers for the service.
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
    'id', u.id
  )
  FROM users u
  JOIN subscriptions s ON s.user_id = u.id AND s.service_id = p_service_id
  WHERE u.service_provider = true;
$$;

GRANT EXECUTE ON FUNCTION public.get_providers_for_service(integer) TO anon;
GRANT EXECUTE ON FUNCTION public.get_providers_for_service(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_providers_for_service(integer) TO service_role;

-- =============================================================================
-- 3. RPC: get_conversations()
-- Returns list of conversations (quote_code, chat_id, provider, client, service, text, time).
-- Requires auth (uses current_user_pk()).
-- =============================================================================
CREATE OR REPLACE FUNCTION public.get_conversations()
RETURNS TABLE (quote_code text, chat_id text, provider text, client text, service text, msg_text text, msg_time text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT q.quote_code, c.chat_code, prov.first_name || ' ' || prov.last_name, cli.first_name || ' ' || cli.last_name, s.name,
    COALESCE((SELECT m.content FROM messages m WHERE m.chat_id = c.id ORDER BY m.time DESC LIMIT 1), 'Start Chat..'),
    COALESCE((SELECT to_char(m.time, 'HH12:MI AM') FROM messages m WHERE m.chat_id = c.id ORDER BY m.time DESC LIMIT 1), '')
  FROM quotes q JOIN chats c ON c.quote_id = q.id JOIN users prov ON prov.id = q.provider_id
  JOIN users cli ON cli.id = q.client_id JOIN services s ON s.id = q.service_id
  WHERE (q.client_id = current_user_pk() OR q.provider_id = current_user_pk()) AND q.converted = false AND c.chat_code IS NOT NULL
  ORDER BY q.id DESC;
$$;

GRANT EXECUTE ON FUNCTION public.get_conversations() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_conversations() TO service_role;

-- =============================================================================
-- 4. RPC: update_my_prof_pic(p_url text)
-- Updates current user's prof_pic. Uses auth context.
-- =============================================================================
CREATE OR REPLACE FUNCTION public.update_my_prof_pic(p_url text)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  UPDATE users SET prof_pic = p_url WHERE clerk_user_id = auth.jwt()->>'sub';
$$;

GRANT EXECUTE ON FUNCTION public.update_my_prof_pic(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_my_prof_pic(text) TO service_role;

-- Helper for RPC: returns current user_id as single-row table (PostgREST needs table for decodeList)
CREATE OR REPLACE FUNCTION public.get_my_user_id()
RETURNS TABLE(uid text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$ SELECT current_user_id() AS uid; $$;

GRANT EXECUTE ON FUNCTION public.get_my_user_id() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_user_id() TO service_role;

-- =============================================================================
-- 5. RPC: upsert_live_location(p_lat double precision, p_lon double precision)
-- Upserts current user's live_location with PostGIS point.
-- =============================================================================
CREATE OR REPLACE FUNCTION public.upsert_live_location(p_lat double precision, p_lon double precision)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_pk integer;
  v_exists integer;
BEGIN
  v_user_pk := current_user_pk();
  IF v_user_pk IS NULL THEN RETURN; END IF;

  SELECT id INTO v_exists FROM live_location WHERE user_id = v_user_pk LIMIT 1;

  IF v_exists IS NULL THEN
    INSERT INTO live_location (user_id, latitude, longitude, geo)
    VALUES (v_user_pk, p_lat, p_lon, ST_SetSRID(ST_MakePoint(p_lon, p_lat), 4326)::geometry);
  ELSE
    UPDATE live_location
    SET latitude = p_lat, longitude = p_lon, geo = ST_SetSRID(ST_MakePoint(p_lon, p_lat), 4326)::geometry
    WHERE user_id = v_user_pk;
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.upsert_live_location(double precision, double precision) TO authenticated;
GRANT EXECUTE ON FUNCTION public.upsert_live_location(double precision, double precision) TO service_role;

-- =============================================================================
-- 6. RPC: submit_provider_application(...)
-- App user applies to become a provider. Updates own row and sets provider_application_pending=true.
-- Admin will approve and assign service subscriptions.
-- =============================================================================
CREATE OR REPLACE FUNCTION public.submit_provider_application(
  p_gender text DEFAULT NULL,
  p_birth_date text DEFAULT NULL,
  p_country text DEFAULT NULL,
  p_county text DEFAULT NULL,
  p_area_name text DEFAULT NULL,
  p_nat_id text DEFAULT NULL,
  p_emm_cont_1 text DEFAULT NULL,
  p_emm_cont_2 text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE users
  SET
    gender = NULLIF(TRIM(p_gender), ''),
    birth_date = CASE WHEN NULLIF(TRIM(p_birth_date), '') IS NOT NULL AND NULLIF(TRIM(p_birth_date), '') ~ '^\d{4}-\d{2}-\d{2}$'
      THEN (NULLIF(TRIM(p_birth_date), ''))::date ELSE birth_date END,
    country = NULLIF(TRIM(p_country), ''),
    county = NULLIF(TRIM(p_county), ''),
    area_name = NULLIF(TRIM(p_area_name), ''),
    nat_id = NULLIF(TRIM(p_nat_id), ''),
    emm_cont_1 = NULLIF(TRIM(p_emm_cont_1), ''),
    emm_cont_2 = NULLIF(TRIM(p_emm_cont_2), ''),
    provider_application_pending = true
  WHERE clerk_user_id = auth.jwt()->>'sub';
END;
$$;

GRANT EXECUTE ON FUNCTION public.submit_provider_application(text, text, text, text, text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.submit_provider_application(text, text, text, text, text, text, text, text) TO service_role;
