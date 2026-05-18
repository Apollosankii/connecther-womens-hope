# SQL Migrations

Run these in the Supabase SQL Editor **in order** when setting up a fresh Supabase project.

1. `supabase_rls_admin.sql` – Admin policies, is_admin()
2. `supabase_admin_clerk.sql` – Clerk support for admins
3. `supabase_rls_android.sql` – App user policies, current_user_pk()
4. `supabase_rls_android_ext.sql` – RPCs (get_conversations, upsert_live_location, etc.)
5. `supabase_profile_and_reports.sql` – update_my_profile, problem_reports, help_requests
6. `get_pending_jobs_rpc.sql` – Jobs screen RPCs

Other files (subscription_plans, provider_applications, etc.) as needed.

- `supabase_platform_training_program_url.sql` – `platform_settings.training_program_url` and `get_app_platform_config()` for the mobile home screen training button.

## Provider verification documents (approvals)

On **Approvals → Review**, thumbnails and **Open** use the same-origin route `GET /documents/<documents.id>/content`, which loads the file from your Supabase Storage (`documents.name` must be an `https://…/storage/v1/object/…` URL for this project). The server forwards **admin JWT + anon key** (or the service role, if set) to Storage so private `provider-docs` objects resolve. Previews open in an **in-page modal** (images and PDFs); **Download** still uses `?download=1`. Ensure `sql/supabase_rls_admin.sql` is applied so admins can `SELECT` from `documents`.

## Service task menu (`services.task_menu`)

Operators can edit this JSON from **Admin → Services → Edit service → Booking task menu (mobile)** (uploads go to Storage bucket `service_catalog` under `tasks/{service_id}/…`).

- **Schema:** same as [woman-global/supabase/migrations/20260513180000_services_task_menu.sql](../../woman-global/supabase/migrations/20260513180000_services_task_menu.sql) and Kotlin `ServiceTaskMenuParser`.
- **Root:** optional `banner_image_url` (https URL), required `rows` (array).
- **Row types:** `section` (title, optional subtitle), `quantity` (key, title, unit_label, unit_price, min, max, default_qty, optional image_url), `toggle` (key, title, unit_label, unit_price, default_checked, optional image_url).
- **Rule:** at least one `quantity` or `toggle` row or the mobile app will not show the task list.

Example:

```json
{
  "banner_image_url": "https://example.com/banner.jpg",
  "rows": [
    { "type": "section", "title": "Home cleaning", "subtitle": "Choose add-ons" },
    { "type": "quantity", "key": "bedrooms", "title": "Basic clean", "unit_label": "No of bedrooms", "unit_price": 500, "min": 0, "max": 20, "default_qty": 0 },
    { "type": "toggle", "key": "gear", "title": "Equipment", "unit_label": "", "unit_price": 650, "default_checked": false }
  ]
}
```

