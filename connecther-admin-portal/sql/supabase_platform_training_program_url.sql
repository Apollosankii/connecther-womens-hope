-- Admin-configurable URL for "Join our training program" (mobile home screen).
-- Mirror: woman-global/supabase/migrations/20260620162000_platform_training_program_url.sql

ALTER TABLE public.platform_settings
  ADD COLUMN IF NOT EXISTS training_program_url text;

COMMENT ON COLUMN public.platform_settings.training_program_url IS
  'Optional HTTPS link opened when users tap Join our training program on the app home screen.';

CREATE OR REPLACE FUNCTION public.get_app_platform_config()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT jsonb_build_object(
    'training_program_url',
    nullif(trim(coalesce(ps.training_program_url, '')), '')
  )
  FROM public.platform_settings ps
  WHERE ps.id = 1
  LIMIT 1;
$$;

GRANT EXECUTE ON FUNCTION public.get_app_platform_config() TO anon;
GRANT EXECUTE ON FUNCTION public.get_app_platform_config() TO authenticated;
