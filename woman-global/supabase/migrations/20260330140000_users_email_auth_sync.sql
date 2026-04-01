-- Align public.users emails with Supabase auth.users where clerk_user_id = auth user UUID.
-- Backfill public.firebase_auth_users from auth.users for admin / future tooling.
-- Clean obvious invalid email strings so unique index + auth-bridge behave predictably.

-- 1) Trim existing emails
UPDATE public.users
SET email = trim(both from email)
WHERE email IS NOT NULL;

-- 2) Drop values that cannot be real login emails (fixes bad data e.g. single character)
UPDATE public.users
SET email = NULL
WHERE email IS NOT NULL
  AND (
    length(trim(both from email)) < 5
    OR position('@' in trim(both from email)) < 2
    OR trim(both from email) NOT LIKE '%@%.%'
  );

-- 3) Authoritative email for rows linked to Supabase Auth (UUID in clerk_user_id)
UPDATE public.users u
SET email = trim(both from au.email)
FROM auth.users au
WHERE u.clerk_user_id = au.id::text
  AND au.email IS NOT NULL
  AND trim(both from au.email) <> '';

-- 4) Mirror auth.users into firebase_auth_users (ids as text; supports ops / future sync)
INSERT INTO public.firebase_auth_users (id, email, created_at)
SELECT
  au.id::text,
  trim(both from au.email),
  COALESCE(au.created_at, now())
FROM auth.users au
WHERE au.email IS NOT NULL
  AND trim(both from au.email) <> ''
ON CONFLICT (id) DO UPDATE SET
  email = EXCLUDED.email,
  created_at = LEAST(firebase_auth_users.created_at, EXCLUDED.created_at);

COMMENT ON TABLE public.firebase_auth_users IS 'Mirror of auth.users (Supabase Auth) for email/UID reference; app may also write Firebase identities.';
