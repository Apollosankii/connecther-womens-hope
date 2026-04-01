-- Provider Applications table: tracks service provider applications with status.
-- Admin Portal fetches from here. Replaces provider_application_pending on users for tracking.
-- Run after supabase_rls_admin.sql and supabase_rls_android_ext.sql.

-- =============================================================================
-- 1. Create provider_applications table
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.provider_applications (
  id SERIAL PRIMARY KEY,
  user_id INTEGER REFERENCES public.users(id) ON DELETE CASCADE,
  clerk_user_id TEXT NOT NULL,
  user_id_str TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  service_ids INTEGER[] DEFAULT '{}',
  application_data JSONB DEFAULT '{}',
  notes TEXT,
  reviewed_by TEXT,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT valid_app_status CHECK (status IN ('pending', 'approved', 'rejected'))
);

CREATE INDEX IF NOT EXISTS idx_provider_applications_status ON public.provider_applications(status);
CREATE INDEX IF NOT EXISTS idx_provider_applications_clerk_user_id ON public.provider_applications(clerk_user_id);
CREATE INDEX IF NOT EXISTS idx_provider_applications_created ON public.provider_applications(created_at DESC);

-- =============================================================================
-- 2. RLS
-- =============================================================================
ALTER TABLE public.provider_applications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_all_provider_applications" ON public.provider_applications;
CREATE POLICY "admin_all_provider_applications" ON public.provider_applications
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());

-- App users can insert their own application (clerk_user_id matches)
DROP POLICY IF EXISTS "app_insert_own_application" ON public.provider_applications;
CREATE POLICY "app_insert_own_application" ON public.provider_applications
  FOR INSERT WITH CHECK (clerk_user_id = auth.jwt()->>'sub');

-- App users can read their own applications
DROP POLICY IF EXISTS "app_select_own_application" ON public.provider_applications;
CREATE POLICY "app_select_own_application" ON public.provider_applications
  FOR SELECT USING (clerk_user_id = auth.jwt()->>'sub');

-- =============================================================================
-- 3. Grants
-- =============================================================================
GRANT SELECT, INSERT, UPDATE ON public.provider_applications TO authenticated;
GRANT USAGE, SELECT ON SEQUENCE public.provider_applications_id_seq TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.provider_applications TO anon;

-- =============================================================================
-- 4. Backfill: Migrate existing users with provider_application_pending=true
-- =============================================================================
INSERT INTO public.provider_applications (user_id, clerk_user_id, user_id_str, status, service_ids, application_data)
SELECT u.id, COALESCE(u.clerk_user_id, 'legacy_' || u.id::text), u.user_id, 'pending', '{}',
  jsonb_build_object(
    'gender', u.gender,
    'birth_date', u.birth_date,
    'country', u.country,
    'county', u.county,
    'area_name', u.area_name,
    'nat_id', u.nat_id,
    'emm_cont_1', u.emm_cont_1,
    'emm_cont_2', u.emm_cont_2
  )
FROM public.users u
WHERE u.provider_application_pending = true
  AND NOT EXISTS (
    SELECT 1 FROM public.provider_applications pa
    WHERE pa.user_id = u.id AND pa.status = 'pending'
  );
