-- Twilio phone verification support: audit log + optional phone verification timestamp.

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS phone_verified_at timestamptz;

COMMENT ON COLUMN public.users.phone_verified_at IS 'Set when phone is verified via Twilio Verify (Edge function phone-verify).';

CREATE TABLE IF NOT EXISTS public.phone_verify_events (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  firebase_uid text NOT NULL,
  e164 text NOT NULL,
  event text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT phone_verify_events_event_chk CHECK (event IN ('start', 'check', 'check_failed'))
);

CREATE INDEX IF NOT EXISTS idx_phone_verify_events_e164_created
  ON public.phone_verify_events (e164, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_phone_verify_events_uid_created
  ON public.phone_verify_events (firebase_uid, created_at DESC);

ALTER TABLE public.phone_verify_events ENABLE ROW LEVEL SECURITY;

-- No GRANT to anon/authenticated: only service_role (Edge Functions) uses this table.
