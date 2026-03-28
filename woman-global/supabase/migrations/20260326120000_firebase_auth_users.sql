-- Sync table for Firebase Authentication (Android app inserts after sign-in).
-- RLS uses Firebase JWT sub claim (= Firebase UID) when Third-Party Auth is enabled in Supabase.

create table if not exists public.firebase_auth_users (
  id text primary key,
  email text not null default '',
  created_at timestamptz not null default now()
);

alter table public.firebase_auth_users enable row level security;

-- Allow authenticated role (Firebase ID token with role claim) to read/write own row only.
create policy "firebase_auth_users_select_own"
  on public.firebase_auth_users for select
  to authenticated
  using ((auth.jwt()->>'sub') = id);

create policy "firebase_auth_users_insert_own"
  on public.firebase_auth_users for insert
  to authenticated
  with check ((auth.jwt()->>'sub') = id);

create policy "firebase_auth_users_update_own"
  on public.firebase_auth_users for update
  to authenticated
  using ((auth.jwt()->>'sub') = id)
  with check ((auth.jwt()->>'sub') = id);
