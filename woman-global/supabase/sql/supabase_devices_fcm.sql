-- Devices table for FCM tokens. Run after supabase_rls_android.sql (current_user_pk exists).
-- Enables app to store/update FCM token per device; backend can read tokens to send push via Firebase Admin.

-- 1. Create devices table if not present (common schema: user_id, reg_token, device id)
CREATE TABLE IF NOT EXISTS public.devices (
  id BIGSERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  reg_token TEXT NOT NULL,
  device TEXT NOT NULL DEFAULT '',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, device)
);

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON public.devices(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_reg_token ON public.devices(reg_token);

COMMENT ON TABLE public.devices IS 'FCM tokens per device for push notifications; RLS restricts to current user.';

-- 2. RLS (if not already applied by supabase_rls_android.sql)
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "devices_app_user" ON public.devices;
CREATE POLICY "devices_app_user" ON public.devices
  FOR ALL USING (user_id = current_user_pk());

-- 3. RPC: upsert FCM token for the current user (Clerk JWT → current_user_pk())
CREATE OR REPLACE FUNCTION public.upsert_my_device(p_reg_token TEXT, p_device TEXT DEFAULT '')
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_pk INTEGER;
BEGIN
  v_user_pk := current_user_pk();
  IF v_user_pk IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;
  INSERT INTO public.devices (user_id, reg_token, device, updated_at)
  VALUES (v_user_pk, p_reg_token, COALESCE(NULLIF(TRIM(p_device), ''), 'default'), NOW())
  ON CONFLICT (user_id, device)
  DO UPDATE SET reg_token = EXCLUDED.reg_token, updated_at = NOW();
END;
$$;

GRANT EXECUTE ON FUNCTION public.upsert_my_device(TEXT, TEXT) TO anon;
GRANT EXECUTE ON FUNCTION public.upsert_my_device(TEXT, TEXT) TO authenticated;
