-- App users could not read their own subscription rows: only admin_all_user_plan_subscriptions
-- (is_admin()) existed, so PostgREST returned 0 rows for seekers and the Subscriptions screen stayed empty.
-- JWT sub = Firebase UID in users.clerk_user_id (auth-bridge minted HS256 JWT).

DROP POLICY IF EXISTS "user_plan_subscriptions_select_own" ON public.user_plan_subscriptions;
CREATE POLICY "user_plan_subscriptions_select_own" ON public.user_plan_subscriptions
  FOR SELECT TO authenticated
  USING (
    user_id IN (
      SELECT id FROM public.users WHERE clerk_user_id = (auth.jwt()->>'sub')
    )
  );
