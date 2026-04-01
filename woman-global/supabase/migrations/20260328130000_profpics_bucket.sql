-- Public bucket for profile (and portfolio) uploads; RLS in backend/sql/supabase_storage_profpics_and_upload_fix.sql
INSERT INTO storage.buckets (id, name, public)
VALUES ('profpics', 'profpics', true)
ON CONFLICT (id) DO UPDATE SET public = true;
