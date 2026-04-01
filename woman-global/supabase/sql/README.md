# Supabase SQL migrations

Run these in the **Supabase SQL Editor** in order:

1. **supabase_rls_admin.sql** – Admin Portal policies (administrators, admin_all_users, etc.)
2. **supabase_admin_clerk.sql** – Clerk integration for Admin
3. **supabase_rls_android.sql** – App RLS + `clerk_user_id`, `current_user_id()`
4. **supabase_rls_android_ext.sql** – RPCs: `get_providers_for_service`, `submit_provider_application`, etc.
5. **supabase_get_pending_approvals_rpc.sql** – RPC for Admin Portal approvals
6. **supabase_provider_applications.sql** – `provider_applications` table (tracks status, service_ids)
7. **supabase_submit_provider_application_v2.sql** – Updated RPC with `p_service_ids`
8. **supabase_subscription_plans.sql** – Subscription tables and plans
9. **supabase_auth_users_sync.sql** – Optional: Auth users sync
10. **seed_services.sql** – Seed services table (if empty)
11. **supabase_start_conversation_rpc.sql** – **`start_conversation_with_provider`** so the Android app can open **Engage → Chat** on Supabase (creates `quotes` + `chats` rows).
12. **supabase_booking_working_hours_providers_map.sql** – **Booking requests** (30‑min expiry), `users.working_hours` / `available_for_booking`, **`get_providers_for_service`** with **lat/lng** + busy filter, **`create_booking_request` / `accept_booking_request` / `decline` / `cancel` / `get_my_booking_requests`**, seekers can **SELECT** provider **documents**, and **`submit_provider_application`** gains **`p_working_hours`**.  
    - Run after jobs RPCs (e.g. `get_pending_jobs` / `complete_my_job` from `connecther-admin-portal/sql/get_pending_jobs_rpc.sql`) so `jobs` / `quotes` exist.  
    - If you already ran **supabase_submit_provider_application_v2.sql**, this file **replaces** that RPC with a 10‑parameter version.

13. **supabase_provider_profile_experience_documents.sql** – Extends **`update_my_profile`** with **`p_title`**, **`p_working_hours`**, **`p_available_for_booking`** (8‑arg RPC; drops the old 5‑arg overload). Replaces **`get_providers_for_service`** to include **`occupation`**. Replaces **`submit_provider_application`** with **12** parameters (**`p_professional_title`**, **`p_experience`**). Adds **`documents`** RLS: **`documents_insert_own`**, **`documents_update_own`**, **`documents_select_own`** so providers can upload portfolio rows (app stores public URLs in **`documents.name`** under Storage bucket **`avatars`**, path **`portfolio/{uid}/…`**).  
    - Run **after** step 12. Ensure Storage policies allow authenticated uploads to **`avatars`** (same bucket as profile photos).

14. **supabase_mpesa_stk_subscription.sql** – Table **`mpesa_stk_payments`** for M‑Pesa STK subscription attempts (Edge Functions use **service_role**). Pair with **`supabase/functions/mpesa-express`** (single function: STK + callback; see its README).

15. **supabase_paystack_subscription.sql** – Table **`paystack_transactions`** for Paystack subscription checkout tracking + webhook activation.

15b. **supabase_user_plan_subscriptions_select_policy.sql** – Lets the mobile app **SELECT** its own **`user_plan_subscriptions`** rows (for post–Paystack **activation polling**). Run after step 15.

16. **supabase_subscription_connects.sql** – **Service-seeker connects:** columns on **`subscription_plans`** (`connects_limit_enabled`, `connects_per_period`, `connects_period_rule`) and **`user_plan_subscriptions`** (`connects_granted`, `connects_used`, `connects_period_started_at`). Admin Portal can configure quotas and reset rules; payment callbacks seed balances when a plan has limits enabled.

17. **supabase_booking_connects_enforcement.sql** – **Enforcement:** `booking_requests.connect_consumed` / `connect_subscription_id`; **`consume_client_subscription_connect`**, **`refund_booking_connect`**, replaces **`create_booking_request`**, **`decline_booking_request`** / **`cancel_booking_request`** / **`expire_stale_booking_requests`**, **`get_my_connect_balance`**. Run after step 12 and step 16.

18. **supabase_free_tier_connects.sql** – **`platform_settings.free_tier_connects`** (admin-editable default), **`users.free_connects_granted` / `free_connects_used`**, trigger on **`users` INSERT**, **`booking_requests.connect_source`**. Updates consume/refund/create_booking/get_balance: **subscription first**, else **free tier**; error **`free_tier_exhausted`**. Run after step 17.

**Android maps:** the app opens **Google Maps** via `https://www.google.com/maps/search` intents (no Maps SDK key required).

### In-app messaging (Supabase)

- After step 11, **Engage** uses the RPC; messages use existing **`get_conversations`**, **`messages`** reads/writes, and RLS from **supabase_rls_android.sql**.
- Ensure **`messages`** has a default for your timestamp column (e.g. `time` or `created_at` defaults to `now()`), or inserts from the app will fail.
- **Optional (lower latency):** Supabase Dashboard → **Database** → **Publications** → enable **`supabase_realtime`** for table **`messages`** (the app currently refreshes the thread every ~1.5s; you can later wire `selectAsFlow` + Realtime).

**If registration or provider application fails:** Run **supabase_rls_clerk_fix.sql** to ensure schema and RLS are correct. See `mobile/android/TROUBLESHOOTING_CLERK_SUPABASE.md`.
