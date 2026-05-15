-- iOS App Store / RevenueCat + shared store transaction tracking

ALTER TABLE public.subscription_plans
  ADD COLUMN IF NOT EXISTS apple_product_id TEXT,
  ADD COLUMN IF NOT EXISTS revenuecat_entitlement_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS subscription_plans_apple_product_id_key
  ON public.subscription_plans (apple_product_id)
  WHERE apple_product_id IS NOT NULL AND apple_product_id <> '';

COMMENT ON COLUMN public.subscription_plans.apple_product_id IS 'App Store product id (RevenueCat / StoreKit).';
COMMENT ON COLUMN public.subscription_plans.revenuecat_entitlement_id IS 'Optional RevenueCat entitlement identifier; defaults handled in app.';

CREATE TABLE IF NOT EXISTS public.store_transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id INTEGER NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  plan_id INTEGER NOT NULL REFERENCES public.subscription_plans(id) ON DELETE RESTRICT,
  platform TEXT NOT NULL CHECK (platform IN ('ios', 'android')),
  store_transaction_id TEXT NOT NULL,
  original_transaction_id TEXT,
  revenuecat_event_id TEXT,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'success', 'failed', 'cancelled')),
  raw_payload JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT store_transactions_platform_tx_unique UNIQUE (platform, store_transaction_id)
);

CREATE INDEX IF NOT EXISTS idx_store_transactions_user_created
  ON public.store_transactions(user_id, created_at DESC);

ALTER TABLE public.store_transactions ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE public.store_transactions IS 'App Store / Play billing events (RevenueCat webhook + client sync). Service role only.';

-- Example product ids (update in admin to match App Store Connect)
UPDATE public.subscription_plans SET apple_product_id = 'connecther_basic_monthly'
  WHERE code = 'basic' AND (apple_product_id IS NULL OR apple_product_id = '');
UPDATE public.subscription_plans SET apple_product_id = 'connecther_premium_monthly'
  WHERE code = 'premium' AND (apple_product_id IS NULL OR apple_product_id = '');
UPDATE public.subscription_plans SET apple_product_id = 'connecther_yearly'
  WHERE code = 'yearly' AND (apple_product_id IS NULL OR apple_product_id = '');
