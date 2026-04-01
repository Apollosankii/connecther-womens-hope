-- Fix profile photo + portfolio uploads from the Android app (Supabase Storage + documents table).
-- Run once in the Supabase SQL Editor after Clerk auth and core tables exist.
--
-- 1) Ensures public bucket `profpics` (same name as app PROFILE_BUCKET).
-- 2) Storage RLS: authenticated users may read/write only under profiles/{their user_id}/ and portfolio/{their user_id}/.
-- 3) Seeds a document type row if none exist (app portfolio inserts require doc_type_id).
-- 4) RPC get_my_user_pk() for reliable uploads tied to the signed-in user.

-- -----------------------------------------------------------------------------
-- Bucket (public URLs: /storage/v1/object/public/profpics/...)
-- -----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES ('profpics', 'profpics', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- -----------------------------------------------------------------------------
-- Storage policies (drop + recreate for idempotency)
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS "profpics_public_read" ON storage.objects;
CREATE POLICY "profpics_public_read"
  ON storage.objects FOR SELECT
  TO public
  USING (bucket_id = 'profpics');

DROP POLICY IF EXISTS "profpics_authenticated_insert_own_prefix" ON storage.objects;
CREATE POLICY "profpics_authenticated_insert_own_prefix"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'profpics'
    AND (storage.foldername(name))[1] IN ('profiles', 'portfolio')
    AND (storage.foldername(name))[2] = (
      SELECT u.user_id FROM public.users u
      WHERE u.clerk_user_id = auth.jwt()->>'sub'
      LIMIT 1
    )
  );

DROP POLICY IF EXISTS "profpics_authenticated_update_own_prefix" ON storage.objects;
CREATE POLICY "profpics_authenticated_update_own_prefix"
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'profpics'
    AND (storage.foldername(name))[1] IN ('profiles', 'portfolio')
    AND (storage.foldername(name))[2] = (
      SELECT u.user_id FROM public.users u
      WHERE u.clerk_user_id = auth.jwt()->>'sub'
      LIMIT 1
    )
  )
  WITH CHECK (
    bucket_id = 'profpics'
    AND (storage.foldername(name))[1] IN ('profiles', 'portfolio')
    AND (storage.foldername(name))[2] = (
      SELECT u.user_id FROM public.users u
      WHERE u.clerk_user_id = auth.jwt()->>'sub'
      LIMIT 1
    )
  );

DROP POLICY IF EXISTS "profpics_authenticated_delete_own_prefix" ON storage.objects;
CREATE POLICY "profpics_authenticated_delete_own_prefix"
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'profpics'
    AND (storage.foldername(name))[1] IN ('profiles', 'portfolio')
    AND (storage.foldername(name))[2] = (
      SELECT u.user_id FROM public.users u
      WHERE u.clerk_user_id = auth.jwt()->>'sub'
      LIMIT 1
    )
  );

-- -----------------------------------------------------------------------------
-- Portfolio inserts: doc_type_id is NOT NULL in many schemas; app uses first type
-- -----------------------------------------------------------------------------
INSERT INTO public.document_type (name)
SELECT 'Portfolio'
WHERE NOT EXISTS (SELECT 1 FROM public.document_type LIMIT 1);

-- App must read document types to pick an id (admin-only RLS blocks this otherwise).
DROP POLICY IF EXISTS "document_type_select_authenticated" ON public.document_type;
CREATE POLICY "document_type_select_authenticated" ON public.document_type
  FOR SELECT TO authenticated
  USING (true);

-- -----------------------------------------------------------------------------
-- RPC: integer primary key for current user (Clerk JWT sub -> users.id)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_my_user_pk()
RETURNS TABLE(pk integer)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT u.id AS pk
  FROM public.users u
  WHERE u.clerk_user_id = auth.jwt()->>'sub'
  LIMIT 1;
$$;

GRANT EXECUTE ON FUNCTION public.get_my_user_pk() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_user_pk() TO service_role;

-- -----------------------------------------------------------------------------
-- Profile pic RPC: return false when no row updated (wrong JWT / user row)
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.update_my_prof_pic(text);
CREATE OR REPLACE FUNCTION public.update_my_prof_pic(p_url text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF p_url IS NULL OR trim(p_url) = '' THEN
    RETURN false;
  END IF;
  UPDATE users SET prof_pic = p_url WHERE clerk_user_id = auth.jwt()->>'sub';
  IF NOT FOUND THEN
    RETURN false;
  END IF;
  RETURN true;
END;
$$;

GRANT EXECUTE ON FUNCTION public.update_my_prof_pic(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_my_prof_pic(text) TO service_role;
