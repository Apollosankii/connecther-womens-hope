-- Sync auth.users → public.users
-- When a user signs up via Supabase Auth (or any provider that creates auth.users),
-- automatically insert a corresponding row in public.users so they appear in the Admin Portal.
-- Run in Supabase SQL Editor or via MCP.

-- =============================================================================
-- 1. Ensure clerk_user_id exists (supabase_rls_android.sql adds it)
-- =============================================================================
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS clerk_user_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_clerk_user_id
  ON public.users(clerk_user_id) WHERE clerk_user_id IS NOT NULL;

-- =============================================================================
-- 2. Trigger: Insert into public.users when auth.users gets a new row
-- =============================================================================
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id text;
  v_first text;
  v_last text;
  v_phone text;
  v_email text;
  v_password text;
BEGIN
  v_user_id := COALESCE(
    NEW.raw_user_meta_data->>'user_id',
    'u_' || substr(replace(NEW.id::text, '-', ''), 1, 12)
  );
  v_first := COALESCE(
    NULLIF(TRIM(NEW.raw_user_meta_data->>'first_name'), ''),
    NULLIF(TRIM(split_part(COALESCE(NEW.raw_user_meta_data->>'full_name', ''), ' ', 1)), ''),
    'User'
  );
  v_last := COALESCE(
    NULLIF(TRIM(NEW.raw_user_meta_data->>'last_name'), ''),
    NULLIF(TRIM(substring(COALESCE(NEW.raw_user_meta_data->>'full_name', '') from position(' ' in COALESCE(NEW.raw_user_meta_data->>'full_name', ' ') + 1)), ''),
    ''
  );
  v_phone := COALESCE(
    NULLIF(TRIM(NEW.phone), ''),
    NULLIF(TRIM(NEW.raw_user_meta_data->>'phone'), ''),
    'not_provided'
  );
  v_email := COALESCE(NULLIF(TRIM(NEW.email), ''), 'noemail@placeholder.local');
  v_password := COALESCE(NEW.encrypted_password, 'auth_migrated');

  INSERT INTO public.users (
    user_id,
    clerk_user_id,
    first_name,
    last_name,
    title,
    phone,
    email,
    password,
    service_provider
  )
  VALUES (
    v_user_id,
    NEW.id::text,
    v_first,
    v_last,
    COALESCE(NULLIF(TRIM(NEW.raw_user_meta_data->>'title'), ''), 'User'),
    v_phone,
    v_email,
    v_password,
    false
  );

  RETURN NEW;
EXCEPTION
  WHEN unique_violation THEN
    RETURN NEW;
  WHEN OTHERS THEN
    RETURN NEW;
END;
$$;

-- =============================================================================
-- 3. Drop existing trigger if any, then create
-- =============================================================================
DROP TRIGGER IF EXISTS on_auth_user_created_sync_public_users ON auth.users;
CREATE TRIGGER on_auth_user_created_sync_public_users
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_auth_user();

-- =============================================================================
-- 4. Backfill: Copy existing auth.users that are not yet in public.users
-- =============================================================================
INSERT INTO public.users (
  user_id,
  clerk_user_id,
  first_name,
  last_name,
  title,
  phone,
  email,
  password,
  service_provider
)
SELECT
  COALESCE(au.raw_user_meta_data->>'user_id', 'u_' || substr(replace(au.id::text, '-', ''), 1, 12)),
  au.id::text,
  COALESCE(NULLIF(TRIM(au.raw_user_meta_data->>'first_name'), ''), NULLIF(TRIM(split_part(COALESCE(au.raw_user_meta_data->>'full_name', ''), ' ', 1)), ''), 'User'),
  COALESCE(NULLIF(TRIM(au.raw_user_meta_data->>'last_name'), ''), ''),
  COALESCE(NULLIF(TRIM(au.raw_user_meta_data->>'title'), ''), 'User'),
  COALESCE(NULLIF(TRIM(au.phone), ''), NULLIF(TRIM(au.raw_user_meta_data->>'phone'), ''), 'not_provided'),
  COALESCE(NULLIF(TRIM(au.email), ''), 'noemail@placeholder.local'),
  COALESCE(au.encrypted_password, 'auth_migrated'),
  false
FROM auth.users au
WHERE NOT EXISTS (
  SELECT 1 FROM public.users pu
  WHERE pu.clerk_user_id = au.id::text
)
ON CONFLICT (clerk_user_id) DO NOTHING;
