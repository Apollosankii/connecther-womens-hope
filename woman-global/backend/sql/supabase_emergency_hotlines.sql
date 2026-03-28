-- Emergency hotlines: admin-managed rows shown on the app Emergency Contacts screen.
-- Run in Supabase SQL Editor after supabase_rls_admin.sql (is_admin).

CREATE TABLE IF NOT EXISTS public.emergency_hotlines (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT,
  phone TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_emergency_hotlines_active_sort
  ON public.emergency_hotlines (is_active, sort_order, id);

COMMENT ON TABLE public.emergency_hotlines IS 'Dialable helplines; app reads active rows; admin portal CRUD.';

ALTER TABLE public.emergency_hotlines ENABLE ROW LEVEL SECURITY;

-- App + anon: only active hotlines
DROP POLICY IF EXISTS "emergency_hotlines_public_select_active" ON public.emergency_hotlines;
CREATE POLICY "emergency_hotlines_public_select_active" ON public.emergency_hotlines
  FOR SELECT TO anon, authenticated
  USING (is_active = true);

-- Admins: full access
DROP POLICY IF EXISTS "emergency_hotlines_admin_all" ON public.emergency_hotlines;
CREATE POLICY "emergency_hotlines_admin_all" ON public.emergency_hotlines
  FOR ALL TO authenticated
  USING (public.is_admin())
  WITH CHECK (public.is_admin());

-- Optional seed (only if table empty)
INSERT INTO public.emergency_hotlines (name, description, phone, sort_order, is_active)
SELECT
  'Nairobi Women''s GBV hotline',
  'Tap to call — 24/7 support',
  '0800720565',
  0,
  true
WHERE NOT EXISTS (SELECT 1 FROM public.emergency_hotlines LIMIT 1);

GRANT SELECT ON public.emergency_hotlines TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.emergency_hotlines TO authenticated;
GRANT USAGE, SELECT ON SEQUENCE public.emergency_hotlines_id_seq TO authenticated;
