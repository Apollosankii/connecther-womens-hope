-- Paystack subscription payment tracking (Edge Functions use service_role).
-- Run after supabase_subscription_plans.sql and supabase_subscription_connects.sql (so user_plan_subscriptions has connects columns).

CREATE TABLE IF NOT EXISTS public.paystack_transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id INTEGER NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  plan_id INTEGER NOT NULL REFERENCES public.subscription_plans(id) ON DELETE RESTRICT,
  amount_kobo INTEGER NOT NULL CHECK (amount_kobo > 0),
  currency TEXT NOT NULL DEFAULT 'KES',
  email TEXT NOT NULL,
  reference TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'success', 'failed', 'abandoned', 'refunded')),
  -- Webhook/initialization payload for audit/debugging
  raw_webhook JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_paystack_transactions_user_created
  ON public.paystack_transactions(user_id, created_at DESC);

ALTER TABLE public.paystack_transactions ENABLE ROW LEVEL SECURITY;

-- No public policies: only service_role (Edge Functions) should access this table.
-- (RLS stays in place; service_role bypasses it.)

COMMENT ON TABLE public.paystack_transactions IS 'Paystack checkout/charge tracking for subscription activation.';

