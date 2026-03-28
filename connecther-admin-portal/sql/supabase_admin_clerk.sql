-- Admin Portal: Support Clerk authentication alongside Supabase Auth
-- Run in Supabase SQL Editor. Adds clerk_user_id to administrators and updates is_admin().

-- 1. Add clerk_user_id to administrators (Clerk user ID from JWT sub)
ALTER TABLE administrators ADD COLUMN IF NOT EXISTS clerk_user_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_administrators_clerk_user_id
  ON administrators(clerk_user_id) WHERE clerk_user_id IS NOT NULL;

-- 2. Update is_admin(): match by email (Supabase Auth) OR clerk_user_id (Clerk)
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM administrators
    WHERE
      LOWER(TRIM(email)) = LOWER(TRIM(COALESCE(auth.jwt()->>'email', '')))
      OR clerk_user_id = auth.jwt()->>'sub'
  );
$$;

-- 3. Update admin_read_self: admins can read own row via email or clerk_user_id
DROP POLICY IF EXISTS "admin_read_self" ON administrators;
CREATE POLICY "admin_read_self" ON administrators
  FOR SELECT USING (
    LOWER(TRIM(email)) = LOWER(TRIM(COALESCE(auth.jwt()->>'email', '')))
    OR clerk_user_id = auth.jwt()->>'sub'
  );
