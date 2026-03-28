-- Connect limits for service-seeker subscription plans (admin-configurable).
-- Run after supabase_subscription_plans.sql (and optional supabase_mpesa_stk_subscription.sql).
-- Enforcement in app RPCs can use: connects_limit_enabled, connects_per_period, connects_period_rule on plans;
-- connects_granted / connects_used / connects_period_started_at on user_plan_subscriptions.

-- -----------------------------------------------------------------------------
-- subscription_plans: admin defines whether to cap connects, how many, reset rule
-- -----------------------------------------------------------------------------
ALTER TABLE public.subscription_plans
  ADD COLUMN IF NOT EXISTS connects_limit_enabled BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE public.subscription_plans
  ADD COLUMN IF NOT EXISTS connects_per_period INTEGER NULL;

ALTER TABLE public.subscription_plans
  ADD COLUMN IF NOT EXISTS connects_period_rule TEXT NOT NULL DEFAULT 'subscription_term';

ALTER TABLE public.subscription_plans DROP CONSTRAINT IF EXISTS subscription_plans_connects_period_rule_check;
ALTER TABLE public.subscription_plans
  ADD CONSTRAINT subscription_plans_connects_period_rule_check
  CHECK (connects_period_rule IN ('subscription_term', 'calendar_month', 'calendar_year'));

ALTER TABLE public.subscription_plans DROP CONSTRAINT IF EXISTS subscription_plans_connects_per_period_nonneg_check;
ALTER TABLE public.subscription_plans
  ADD CONSTRAINT subscription_plans_connects_per_period_nonneg_check
  CHECK (connects_per_period IS NULL OR connects_per_period >= 0);

COMMENT ON COLUMN public.subscription_plans.connects_limit_enabled IS 'If true, seekers on this plan get a capped number of connects (see connects_per_period).';
COMMENT ON COLUMN public.subscription_plans.connects_per_period IS 'Max connects per reset period when connects_limit_enabled; NULL means not configured yet.';
COMMENT ON COLUMN public.subscription_plans.connects_period_rule IS 'subscription_term: bucket matches plan start→expiry; calendar_month/year: resets on calendar boundaries (enforcement logic uses this).';

-- -----------------------------------------------------------------------------
-- user_plan_subscriptions: balance for current period (NULL granted = unlimited / not capped)
-- -----------------------------------------------------------------------------
ALTER TABLE public.user_plan_subscriptions
  ADD COLUMN IF NOT EXISTS connects_granted INTEGER NULL;

ALTER TABLE public.user_plan_subscriptions
  ADD COLUMN IF NOT EXISTS connects_used INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.user_plan_subscriptions
  ADD COLUMN IF NOT EXISTS connects_period_started_at DATE NULL;

ALTER TABLE public.user_plan_subscriptions DROP CONSTRAINT IF EXISTS user_plan_subscriptions_connects_used_nonneg_check;
ALTER TABLE public.user_plan_subscriptions
  ADD CONSTRAINT user_plan_subscriptions_connects_used_nonneg_check
  CHECK (connects_used >= 0);

ALTER TABLE public.user_plan_subscriptions DROP CONSTRAINT IF EXISTS user_plan_subscriptions_connects_granted_nonneg_check;
ALTER TABLE public.user_plan_subscriptions
  ADD CONSTRAINT user_plan_subscriptions_connects_granted_nonneg_check
  CHECK (connects_granted IS NULL OR connects_granted >= 0);

COMMENT ON COLUMN public.user_plan_subscriptions.connects_granted IS 'Connects allocated for current bucket; NULL = unlimited for this row.';
COMMENT ON COLUMN public.user_plan_subscriptions.connects_used IS 'Connects consumed in current bucket.';
COMMENT ON COLUMN public.user_plan_subscriptions.connects_period_started_at IS 'Start date of current connect period (subscription start or calendar period anchor).';
