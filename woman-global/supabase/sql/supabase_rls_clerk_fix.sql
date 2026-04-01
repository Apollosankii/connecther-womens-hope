-- Clerk + Supabase RLS Fix: Run this in Supabase SQL Editor if registration or provider application fails.
-- Prerequisites: Clerk must be configured as Third-Party Auth in Supabase (Dashboard → Auth → Third-Party).
-- The Clerk JWT must include role: "authenticated" (configure via dashboard.clerk.com/setup/supabase).

-- =============================================================================
-- 1. Schema: Ensure required columns exist on users
-- =============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS clerk_user_id TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_application_pending BOOLEAN DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_clerk_user_id ON users(clerk_user_id) WHERE clerk_user_id IS NOT NULL;

-- =============================================================================
-- 2. Grants: Ensure authenticated role can insert/update users (RLS still applies)
-- =============================================================================
GRANT SELECT, INSERT, UPDATE ON users TO authenticated;
GRANT USAGE ON SCHEMA public TO authenticated;

-- =============================================================================
-- 3. Users RLS: Recreate app-user policies (must allow INSERT for onboarding)
-- =============================================================================
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- Drop and recreate to avoid conflicts
DROP POLICY IF EXISTS "users_insert_own" ON users;
CREATE POLICY "users_insert_own" ON users FOR INSERT
  WITH CHECK (clerk_user_id IS NOT NULL AND clerk_user_id = auth.jwt()->>'sub');

DROP POLICY IF EXISTS "users_update_own" ON users;
CREATE POLICY "users_update_own" ON users FOR UPDATE
  USING (clerk_user_id = auth.jwt()->>'sub');

DROP POLICY IF EXISTS "users_select_own" ON users;
CREATE POLICY "users_select_own" ON users FOR SELECT
  USING (clerk_user_id = auth.jwt()->>'sub');

DROP POLICY IF EXISTS "users_select_providers" ON users;
CREATE POLICY "users_select_providers" ON users FOR SELECT
  USING (service_provider = true);

-- =============================================================================
-- 4. submit_provider_application RPC: Recreate (SECURITY DEFINER bypasses RLS)
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
