-- Allow authenticated users to read their own subscription rows (for post-payment polling in the app).
-- JWT sub = Firebase UID stored in users.clerk_user_id.

DROP POLICY IF EXISTS "user_plan_subscriptions_select_own" ON public.user_plan_subscriptions;
CREATE POLICY "user_plan_subscriptions_select_own" ON public.user_plan_subscriptions
  FOR SELECT TO authenticated
  USING (
    user_id IN (
      SELECT id FROM public.users WHERE clerk_user_id = (auth.jwt()->>'sub')
    )
  );
