-- RPC: get_users_for_admin() - Returns all users for admin when is_admin().
-- Use when REST /users returns empty due to RLS. Admin Portal calls this for list_providers/list_app_users.

CREATE OR REPLACE FUNCTION public.get_users_for_admin()
RETURNS TABLE (
  id integer,
  user_id text,
  first_name text,
  last_name text,
  email text,
  phone text,
  nat_id text,
  wh_badge boolean,
  service_provider boolean
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT u.id, u.user_id, u.first_name, u.last_name, u.email, u.phone, u.nat_id,
         COALESCE(u."WH_badge", false) AS wh_badge,
         COALESCE(u.service_provider, false) AS service_provider
  FROM users u
  WHERE is_admin();
$$;

GRANT EXECUTE ON FUNCTION public.get_users_for_admin() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_users_for_admin() TO anon;
GRANT EXECUTE ON FUNCTION public.get_users_for_admin() TO service_role;
