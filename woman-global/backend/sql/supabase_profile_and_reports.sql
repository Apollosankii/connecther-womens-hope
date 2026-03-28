-- Profile update and problem reports for ConnectHer app.
-- Run in Supabase SQL Editor after supabase_rls_android_ext.sql.

-- =============================================================================
-- 1. RPC: update_my_profile(p_first_name, p_last_name, p_phone, p_email, p_occupation)
-- Updates current user's profile. Uses auth context (Clerk JWT).
-- =============================================================================
CREATE OR REPLACE FUNCTION public.update_my_profile(
  p_first_name text DEFAULT NULL,
  p_last_name text DEFAULT NULL,
  p_phone text DEFAULT NULL,
  p_email text DEFAULT NULL,
  p_occupation text DEFAULT NULL
)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  UPDATE users
  SET
    first_name = COALESCE(NULLIF(TRIM(p_first_name), ''), first_name),
    last_name = COALESCE(NULLIF(TRIM(p_last_name), ''), last_name),
    phone = COALESCE(NULLIF(TRIM(p_phone), ''), phone),
    email = COALESCE(NULLIF(TRIM(p_email), ''), email),
    occupation = COALESCE(NULLIF(TRIM(p_occupation), ''), occupation)
  WHERE clerk_user_id = auth.jwt()->>'sub';
$$;

GRANT EXECUTE ON FUNCTION public.update_my_profile(text, text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_my_profile(text, text, text, text, text) TO service_role;

-- =============================================================================
-- 2. Table: problem_reports
-- Stores user-submitted problem reports.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.problem_reports (
  id SERIAL PRIMARY KEY,
  user_id INTEGER REFERENCES public.users(id) ON DELETE SET NULL,
  clerk_user_id TEXT,
  description TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE problem_reports ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "users_insert_own_reports" ON problem_reports;
CREATE POLICY "users_insert_own_reports" ON problem_reports FOR INSERT
  WITH CHECK (clerk_user_id = auth.jwt()->>'sub' OR user_id = current_user_pk());

DROP POLICY IF EXISTS "service_role_all_reports" ON problem_reports;
CREATE POLICY "service_role_all_reports" ON problem_reports FOR ALL
  USING (true);

-- =============================================================================
-- 3. RPC: insert_my_problem_report(p_description text)
-- Inserts a problem report for the current user.
-- =============================================================================
CREATE OR REPLACE FUNCTION public.insert_my_problem_report(p_description text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_pk integer;
  v_clerk text;
BEGIN
  v_clerk := auth.jwt()->>'sub';
  v_user_pk := current_user_pk();
  INSERT INTO problem_reports (user_id, clerk_user_id, description)
  VALUES (v_user_pk, v_clerk, TRIM(p_description));
END;
$$;

GRANT EXECUTE ON FUNCTION public.insert_my_problem_report(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.insert_my_problem_report(text) TO service_role;

-- =============================================================================
-- 4. Table: help_requests + RPC: insert_my_help_request()
-- For GBV hotline / Get Help button.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.help_requests (
  id SERIAL PRIMARY KEY,
  user_id INTEGER REFERENCES public.users(id) ON DELETE SET NULL,
  clerk_user_id TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE help_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "users_insert_own_help" ON help_requests;
CREATE POLICY "users_insert_own_help" ON help_requests FOR INSERT
  WITH CHECK (clerk_user_id = auth.jwt()->>'sub');

CREATE OR REPLACE FUNCTION public.insert_my_help_request()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_pk integer;
  v_clerk text;
BEGIN
  v_clerk := auth.jwt()->>'sub';
  v_user_pk := current_user_pk();
  INSERT INTO help_requests (user_id, clerk_user_id) VALUES (v_user_pk, v_clerk);
END;
$$;

GRANT EXECUTE ON FUNCTION public.insert_my_help_request() TO authenticated;
GRANT EXECUTE ON FUNCTION public.insert_my_help_request() TO service_role;
