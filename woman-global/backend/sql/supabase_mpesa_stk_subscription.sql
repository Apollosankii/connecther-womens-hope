-- M-Pesa Daraja STK Push tracking for subscription payments.
-- Run after supabase_subscription_plans.sql. Edge Functions use service_role to read/write.

CREATE TABLE IF NOT EXISTS public.mpesa_stk_payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id INTEGER NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  plan_id INTEGER NOT NULL REFERENCES public.subscription_plans(id) ON DELETE RESTRICT,
  amount INTEGER NOT NULL CHECK (amount > 0),
  phone_normalized TEXT NOT NULL,
  checkout_request_id TEXT,
  merchant_request_id TEXT,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'completed', 'failed', 'cancelled')),
  result_code INTEGER,
  result_desc TEXT,
  mpesa_receipt_number TEXT,
  raw_callback JSONB,
  -- Value placed in CallBackURL ?token= (static MPESA_CALLBACK_TOKEN or Daraja OAuth access_token when secret omitted)
  callback_verifier TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Existing DBs created before callback_verifier:
ALTER TABLE public.mpesa_stk_payments ADD COLUMN IF NOT EXISTS callback_verifier TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mpesa_stk_checkout ON public.mpesa_stk_payments(checkout_request_id)
  WHERE checkout_request_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_mpesa_stk_user_created ON public.mpesa_stk_payments(user_id, created_at DESC);

ALTER TABLE public.mpesa_stk_payments ENABLE ROW LEVEL SECURITY;

-- No public policies: only service_role (Edge Functions) accesses this table.

COMMENT ON TABLE public.mpesa_stk_payments IS 'STK Push subscription attempts; callback Edge Function completes rows and activates user_plan_subscriptions.';
COMMENT ON COLUMN public.mpesa_stk_payments.callback_verifier IS 'Exact ?token= sent on CallBackURL: static MPESA_CALLBACK_TOKEN or OAuth access_token if secret omitted.';
