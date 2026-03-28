# SQL Migrations

Run these in the Supabase SQL Editor **in order** when setting up a fresh Supabase project.

1. `supabase_rls_admin.sql` – Admin policies, is_admin()
2. `supabase_admin_clerk.sql` – Clerk support for admins
3. `supabase_rls_android.sql` – App user policies, current_user_pk()
4. `supabase_rls_android_ext.sql` – RPCs (get_conversations, upsert_live_location, etc.)
5. `supabase_profile_and_reports.sql` – update_my_profile, problem_reports, help_requests
6. `get_pending_jobs_rpc.sql` – Jobs screen RPCs

Other files (subscription_plans, provider_applications, etc.) as needed.
