-- Audit + rate limiting for GBV panic SMS sent via Edge function panic-sms (Twilio Messaging).
-- Written only by service_role from Edge; no client policies.

CREATE TABLE IF NOT EXISTS public.panic_sms_dispatch (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  firebase_uid text NOT NULL,
  recipient_count integer NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT panic_sms_dispatch_recipient_count_chk
    CHECK (recipient_count >= 1 AND recipient_count <= 5)
);

COMMENT ON TABLE public.panic_sms_dispatch IS 'One row per successful panic-sms Edge dispatch (Twilio); used for rate limits and audit.';

CREATE INDEX IF NOT EXISTS idx_panic_sms_dispatch_uid_created
  ON public.panic_sms_dispatch (firebase_uid, created_at DESC);

ALTER TABLE public.panic_sms_dispatch ENABLE ROW LEVEL SECURITY;
