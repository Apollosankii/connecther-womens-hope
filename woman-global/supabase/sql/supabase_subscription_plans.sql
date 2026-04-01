-- Subscription plans & user plan subscriptions (SUBSCRIPTION_ADMIN_PLAN)
-- Run in Supabase SQL Editor. Requires supabase_rls_admin.sql (is_admin).

-- =============================================================================
-- 1. subscription_plans
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.subscription_plans (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  code VARCHAR(64) UNIQUE,
  description TEXT,
  price DECIMAL(10,2) NOT NULL DEFAULT 0,
  currency VARCHAR(8) NOT NULL DEFAULT 'KES',
  duration_type VARCHAR(16) NOT NULL DEFAULT 'month',
  duration_value INTEGER NOT NULL DEFAULT 1,
  features JSONB DEFAULT '[]',
  is_active BOOLEAN NOT NULL DEFAULT true,
  is_popular BOOLEAN NOT NULL DEFAULT false,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.subscription_plans ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_all_subscription_plans" ON public.subscription_plans;
CREATE POLICY "admin_all_subscription_plans" ON public.subscription_plans
  FOR ALL USING (is_admin());

-- Public read for active plans (Android app fetches these)
DROP POLICY IF EXISTS "anon_select_active_plans" ON public.subscription_plans;
CREATE POLICY "anon_select_active_plans" ON public.subscription_plans
  FOR SELECT USING (is_active = true);

-- =============================================================================
-- 2. user_plan_subscriptions
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.user_plan_subscriptions (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  plan_id INTEGER NOT NULL REFERENCES public.subscription_plans(id) ON DELETE RESTRICT,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  started_at DATE NOT NULL,
  expires_at DATE NOT NULL,
  cancelled_at DATE,
  payment_reference VARCHAR(255),
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT valid_status CHECK (status IN ('active', 'cancelled', 'expired', 'pending_payment'))
);

CREATE INDEX IF NOT EXISTS idx_user_plan_sub_user ON public.user_plan_subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_plan_sub_plan ON public.user_plan_subscriptions(plan_id);
CREATE INDEX IF NOT EXISTS idx_user_plan_sub_status ON public.user_plan_subscriptions(status);

ALTER TABLE public.user_plan_subscriptions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_all_user_plan_subscriptions" ON public.user_plan_subscriptions;
CREATE POLICY "admin_all_user_plan_subscriptions" ON public.user_plan_subscriptions
  FOR ALL USING (is_admin());

-- =============================================================================
-- 3. Seed default plans (Basic, Premium, Yearly) if empty
-- =============================================================================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.subscription_plans LIMIT 1) THEN
    INSERT INTO public.subscription_plans (name, code, description, price, currency, duration_type, duration_value, features, is_active, is_popular, sort_order)
    VALUES
      ('Basic', 'basic', 'Essential access to connect with caregivers and basic support.', 499, 'KES', 'month', 1, '["Up to 5 consultations per month","Chat with verified caregivers","Basic helpline access"]'::jsonb, true, false, 1),
      ('Premium', 'premium', 'Full access to all features, priority support, and exclusive content.', 999, 'KES', 'month', 1, '["Unlimited consultations","24/7 priority support","Discount on booked services","Early access to new features"]'::jsonb, true, true, 2),
      ('Yearly', 'yearly', 'Best value: pay annually and save. All Premium benefits.', 9999, 'KES', 'year', 1, '["Everything in Premium","2 months free (save 17%)","Unlimited consultations","24/7 priority support"]'::jsonb, true, false, 3);
  END IF;
END $$;
