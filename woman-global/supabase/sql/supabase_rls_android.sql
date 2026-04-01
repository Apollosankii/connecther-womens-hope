-- Android App RLS: Run this in Supabase SQL Editor AFTER configuring Clerk as third-party auth.
-- Prerequisites:
--   1. Clerk registered as third-party auth provider in Supabase Dashboard
--   2. supabase_rls_admin.sql already applied (admin policies)
--   3. users table has clerk_user_id column (add if missing below)

-- =============================================================================
-- 1. Schema: Ensure clerk_user_id exists on users
-- =============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS clerk_user_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_clerk_user_id ON users(clerk_user_id) WHERE clerk_user_id IS NOT NULL;

-- =============================================================================
-- 2. Helper: Resolve Clerk JWT sub (clerk_user_id) to app user_id (string)
-- =============================================================================
CREATE OR REPLACE FUNCTION public.current_user_id()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT user_id FROM users WHERE clerk_user_id = auth.jwt()->>'sub' LIMIT 1;
$$;

-- Helper: Resolve to users.id (integer) for FK comparisons
CREATE OR REPLACE FUNCTION public.current_user_pk()
RETURNS INTEGER
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT id FROM users WHERE clerk_user_id = auth.jwt()->>'sub' LIMIT 1;
$$;

-- =============================================================================
-- 3. Services: Public read (app users need to browse services)
-- =============================================================================
ALTER TABLE services ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "services_select_all" ON services;
CREATE POLICY "services_select_all" ON services FOR SELECT USING (true);

-- =============================================================================
-- 4. Users: App user policies (additive to admin policies)
-- =============================================================================
DROP POLICY IF EXISTS "users_select_own" ON users;
CREATE POLICY "users_select_own" ON users FOR SELECT
  USING (clerk_user_id = auth.jwt()->>'sub');

DROP POLICY IF EXISTS "users_update_own" ON users;
CREATE POLICY "users_update_own" ON users FOR UPDATE
  USING (clerk_user_id = auth.jwt()->>'sub');

-- Users can insert when linking clerk_user_id (onboarding)
DROP POLICY IF EXISTS "users_insert_own" ON users;
CREATE POLICY "users_insert_own" ON users FOR INSERT
  WITH CHECK (clerk_user_id = auth.jwt()->>'sub');

-- Providers visible to authenticated users for discovery
DROP POLICY IF EXISTS "users_select_providers" ON users;
CREATE POLICY "users_select_providers" ON users FOR SELECT
  USING (service_provider = true);

-- =============================================================================
-- 5. Quotes: Client or provider
-- =============================================================================
DROP POLICY IF EXISTS "quotes_app_user" ON quotes;
CREATE POLICY "quotes_app_user" ON quotes FOR ALL
  USING (
    client_id = current_user_pk() OR provider_id = current_user_pk()
  );

-- =============================================================================
-- 6. Chats: Via quote participant
-- =============================================================================
DROP POLICY IF EXISTS "chats_app_user" ON chats;
CREATE POLICY "chats_app_user" ON chats FOR ALL
  USING (
    quote_id IN (
      SELECT id FROM quotes
      WHERE client_id = current_user_pk() OR provider_id = current_user_pk()
    )
  );

-- =============================================================================
-- 7. Messages: Via chat -> quote participant
-- =============================================================================
DROP POLICY IF EXISTS "messages_app_user" ON messages;
CREATE POLICY "messages_app_user" ON messages FOR ALL
  USING (
    chat_id IN (
      SELECT c.id FROM chats c
      JOIN quotes q ON q.id = c.quote_id
      WHERE q.client_id = current_user_pk() OR q.provider_id = current_user_pk()
    )
  );

-- =============================================================================
-- 8. Jobs: Via quote (client/provider)
-- =============================================================================
DROP POLICY IF EXISTS "jobs_app_user" ON jobs;
CREATE POLICY "jobs_app_user" ON jobs FOR ALL
  USING (
    quote_id IN (
      SELECT id FROM quotes
      WHERE client_id = current_user_pk() OR provider_id = current_user_pk()
    )
  );

-- =============================================================================
-- 9. Live location: Own row only
-- =============================================================================
DROP POLICY IF EXISTS "live_location_app_user" ON live_location;
CREATE POLICY "live_location_app_user" ON live_location FOR ALL
  USING (user_id = current_user_pk());

-- =============================================================================
-- 10. Devices: Own FCM tokens (for updateDeviceToken)
-- =============================================================================
-- devices table: user_id (int FK to users.id)
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "devices_app_user" ON devices;
CREATE POLICY "devices_app_user" ON devices FOR ALL
  USING (user_id = current_user_pk());
