-- Admin-configurable limits for subscribed panic SMS (Edge function panic-sms) + audit IP + admin read for portal.

ALTER TABLE public.platform_settings
  ADD COLUMN IF NOT EXISTS panic_sms_max_dispatches_per_24h integer NOT NULL DEFAULT 6
    CHECK (panic_sms_max_dispatches_per_24h >= 1 AND panic_sms_max_dispatches_per_24h <= 200);

ALTER TABLE public.platform_settings
  ADD COLUMN IF NOT EXISTS panic_sms_min_seconds_between integer NOT NULL DEFAULT 180
    CHECK (panic_sms_min_seconds_between >= 0 AND panic_sms_min_seconds_between <= 86400);

ALTER TABLE public.platform_settings
  ADD COLUMN IF NOT EXISTS panic_sms_max_global_per_hour integer NOT NULL DEFAULT 200
    CHECK (panic_sms_max_global_per_hour >= 10 AND panic_sms_max_global_per_hour <= 100000);

COMMENT ON COLUMN public.platform_settings.panic_sms_max_dispatches_per_24h IS
  'Max successful panic-sms batches per Firebase UID per rolling 24h (subscribed Twilio path).';
COMMENT ON COLUMN public.platform_settings.panic_sms_min_seconds_between IS
  'Min seconds between two successful panic-sms dispatches for the same Firebase UID (0 = no cooldown).';
COMMENT ON COLUMN public.platform_settings.panic_sms_max_global_per_hour IS
  'Max successful panic-sms dispatches platform-wide per rolling hour (abuse fuse).';

ALTER TABLE public.panic_sms_dispatch
  ADD COLUMN IF NOT EXISTS request_ip text;

COMMENT ON COLUMN public.panic_sms_dispatch.request_ip IS 'Client IP from x-forwarded-for (first hop) when available; audit only.';

CREATE INDEX IF NOT EXISTS idx_panic_sms_dispatch_created_at
  ON public.panic_sms_dispatch (created_at DESC);

DROP POLICY IF EXISTS "admin_select_panic_sms_dispatch" ON public.panic_sms_dispatch;
CREATE POLICY "admin_select_panic_sms_dispatch" ON public.panic_sms_dispatch
  FOR SELECT TO authenticated
  USING (public.is_admin());

COMMENT ON TABLE public.platform_settings IS
  'Singleton app config: free_tier_connects (new users), panic_sms_* (subscribed panic SMS via Edge panic-sms).';
