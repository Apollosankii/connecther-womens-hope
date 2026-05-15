-- Add optional location fields for GBV help requests and allow RPC params.

ALTER TABLE public.help_requests
  ADD COLUMN IF NOT EXISTS latitude double precision NULL;

ALTER TABLE public.help_requests
  ADD COLUMN IF NOT EXISTS longitude double precision NULL;

ALTER TABLE public.help_requests
  ADD COLUMN IF NOT EXISTS location_text text NULL;

COMMENT ON COLUMN public.help_requests.latitude IS 'Optional latitude captured during GBV panic.';
COMMENT ON COLUMN public.help_requests.longitude IS 'Optional longitude captured during GBV panic.';
COMMENT ON COLUMN public.help_requests.location_text IS 'Optional free-text location (e.g., \"lat,lng\" or user-friendly string).';

DROP FUNCTION IF EXISTS public.insert_my_help_request();

CREATE OR REPLACE FUNCTION public.insert_my_help_request(
  p_latitude double precision DEFAULT NULL,
  p_longitude double precision DEFAULT NULL,
  p_location_text text DEFAULT NULL
)
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
  INSERT INTO help_requests (user_id, clerk_user_id, latitude, longitude, location_text)
  VALUES (v_user_pk, v_clerk, p_latitude, p_longitude, NULLIF(trim(coalesce(p_location_text, '')), ''));
END;
$$;

GRANT EXECUTE ON FUNCTION public.insert_my_help_request(double precision, double precision, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.insert_my_help_request(double precision, double precision, text) TO service_role;

