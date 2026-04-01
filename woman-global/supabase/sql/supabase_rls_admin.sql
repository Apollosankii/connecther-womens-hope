-- Admin Portal RLS: Run this in Supabase SQL Editor
-- Prerequisite: Tables must exist in the same Supabase project used for Auth
-- Admins sign in with Supabase Auth; their JWT email must match administrators.email

-- 1. Helper: check if current JWT belongs to an administrator
-- Prerequisite: administrators must have clerk_user_id column (see supabase_admin_clerk.sql)
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM administrators
    WHERE LOWER(TRIM(email)) = LOWER(TRIM(COALESCE(auth.jwt()->>'email', '')))
      OR clerk_user_id = auth.jwt()->>'sub'
  );
$$;

-- 2. administrators: admins can read their own row; can insert when email or clerk_user_id matches JWT
ALTER TABLE administrators ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_read_self" ON administrators;
CREATE POLICY "admin_read_self" ON administrators
  FOR SELECT USING (
    LOWER(TRIM(email)) = LOWER(TRIM(COALESCE(auth.jwt()->>'email', '')))
    OR clerk_user_id = auth.jwt()->>'sub'
  );
DROP POLICY IF EXISTS "admin_insert_self" ON administrators;
CREATE POLICY "admin_insert_self" ON administrators
  FOR INSERT WITH CHECK (
    LOWER(TRIM(email)) = LOWER(TRIM(COALESCE(auth.jwt()->>'email', '')))
    OR clerk_user_id = auth.jwt()->>'sub'
  );

-- 3. users: admins can read/write (anon key + admin JWT; no service role needed)
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_users" ON users;
CREATE POLICY "admin_all_users" ON users
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());

-- 4. services: admins can read/write
ALTER TABLE services ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_services" ON services;
CREATE POLICY "admin_all_services" ON services
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());

-- 5. document_type: admins can read/write
ALTER TABLE document_type ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_doc_type" ON document_type;
CREATE POLICY "admin_all_doc_type" ON document_type
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());

-- 6. documents: admins can read/write
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_documents" ON documents;
CREATE POLICY "admin_all_documents" ON documents
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());

-- 7. live_location: admins can read/write
ALTER TABLE live_location ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_live_location" ON live_location;
CREATE POLICY "admin_all_live_location" ON live_location
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());

-- 8. subscriptions: admins can read/write
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_subscriptions" ON subscriptions;
CREATE POLICY "admin_all_subscriptions" ON subscriptions
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());

-- 9. quotes: admins need for jobs
ALTER TABLE quotes ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_quotes" ON quotes;
CREATE POLICY "admin_all_quotes" ON quotes
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());

-- 10. jobs: admins can read
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_jobs" ON jobs;
CREATE POLICY "admin_all_jobs" ON jobs
  FOR ALL USING (is_admin());

-- 11. chats (if Admin Portal needs)
ALTER TABLE chats ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_chats" ON chats;
CREATE POLICY "admin_all_chats" ON chats
  FOR ALL USING (is_admin());

-- 12. messages (if Admin Portal needs)
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_messages" ON messages;
CREATE POLICY "admin_all_messages" ON messages
  FOR ALL USING (is_admin());

-- 13. job_score_card: admins can read/write
ALTER TABLE job_score_card ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "admin_all_job_score_card" ON job_score_card;
CREATE POLICY "admin_all_job_score_card" ON job_score_card
  FOR ALL USING (is_admin()) WITH CHECK (is_admin());
