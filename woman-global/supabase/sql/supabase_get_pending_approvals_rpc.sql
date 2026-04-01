-- RPC: get_pending_approvals() - Returns pending provider applications for admins.
-- Fetches from provider_applications joined with users. Falls back to users for legacy.
-- Run after supabase_provider_applications.sql.

CREATE OR REPLACE FUNCTION public.get_pending_approvals()
RETURNS TABLE (
  first_name text,
  last_name text,
  wh_badge boolean,
  email text,
  phone text,
  nat_id text,
  user_id text,
  id integer
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT u.first_name, u.last_name, COALESCE(u."WH_badge", false) AS wh_badge, u.email, u.phone, u.nat_id, COALESCE(pa.user_id_str, u.user_id) AS user_id, u.id
  FROM provider_applications pa
  JOIN users u ON u.id = pa.user_id
  WHERE pa.status = 'pending' AND is_admin()
  UNION ALL
  SELECT u.first_name, u.last_name, COALESCE(u."WH_badge", false), u.email, u.phone, u.nat_id, u.user_id, u.id
  FROM users u
  WHERE u.provider_application_pending = true AND is_admin()
  AND NOT EXISTS (SELECT 1 FROM provider_applications pa2 WHERE pa2.user_id = u.id AND pa2.status = 'pending');
$$;

GRANT EXECUTE ON FUNCTION public.get_pending_approvals() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_pending_approvals() TO anon;
GRANT EXECUTE ON FUNCTION public.get_pending_approvals() TO service_role;
