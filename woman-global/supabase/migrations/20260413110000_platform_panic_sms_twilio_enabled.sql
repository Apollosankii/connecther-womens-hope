-- Admin toggle: when false, panic-sms rejects Twilio sends so subscribed users use device SMS from the app.

ALTER TABLE public.platform_settings
  ADD COLUMN IF NOT EXISTS panic_sms_twilio_enabled boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN public.platform_settings.panic_sms_twilio_enabled IS
  'When false, Edge function panic-sms returns TWILIO_DISABLED; subscribed users use the normal device SMS flow.';
