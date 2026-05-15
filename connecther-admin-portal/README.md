# ConnectHer admin portal

Python + Jinja templates and SQL snippets for operating ConnectHer data in Supabase.

## Related — product design & mobile parity

- **Figma (reference UI):** [Connecther file](https://www.figma.com/design/kSvTvj1QCNO5Xcj6LVMQsz/Connecther?node-id=0-1)
- **Mobile matrix (what ships in apps vs Figma):** [woman-global/mobile/FIGMA_CODE_MATRIX.md](../woman-global/mobile/FIGMA_CODE_MATRIX.md)
- **Backend contract (v1, no line-item schema):** [woman-global/supabase/BACKEND_CONTRACT_V1.md](../woman-global/supabase/BACKEND_CONTRACT_V1.md)

When you add **new tables or RPCs**, update `supabase_data.py`, templates under `templates/`, and SQL under `sql/` in the **same release** as the Supabase migration so operators stay aligned with Kotlin and Expo.

See [sql/README.md](sql/README.md) for SQL maintenance notes and **service task menu (`task_menu`)** operator documentation.

## Supabase keys (admin portal)

- **Required:** `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and a signed-in admin session (see `sql/supabase_rls_admin.sql` for `is_admin()`).
- **Optional:** `SUPABASE_SERVICE_ROLE_KEY`. Without it, **service catalog image uploads** still work for signed-in admins via JWT + the `service_catalog_admin_all` storage policy (migration `woman-global/supabase/migrations/20260430140000_service_catalog_storage_bucket.sql`). The service role is mainly a convenience when JWT/RLS is hard to debug, or for signing **private** Storage objects. The **provider verification document** viewer (`GET /documents/<id>/content`) uses your admin JWT when fetching from the `provider-docs` bucket so previews work without opening Supabase URLs in a new tab; signed URLs are still used when the service role is configured.
