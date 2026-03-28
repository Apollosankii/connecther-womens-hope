# send-push Edge Function

Sends **Web Push** notifications using VAPID keys. Deployed to Supabase; call via your project’s Edge Function URL.

## Configure secrets (required to send)

1. Open [Supabase Dashboard](https://supabase.com/dashboard) → your project.
2. Go to **Edge Functions** → **send-push** → **Secrets** (or **Settings** → **Edge Function Secrets**).
3. Add **one** of these, depending on which key you have:

- **If you have the private key (for sending):**  
  **`WEBPUSH_VAPID_PRIVATE_KEY`** = your private VAPID key.  
  The function already has a default **public** key in code (`BJC1VoTYWSv1...`). If that is not the pair for this private key, also set **`WEBPUSH_VAPID_PUBLIC_KEY`** to the matching public key.

- **If you only have the public key:**  
  The public key `BJC1VoTYWSv1EZ7TeJq5uYsOpj0qAXU_BDcyRCXt6OprmMPpGy-7j0sDi91j9FXkqLdFU77xUyh325EmqLhuG9A` is already configured. Generate a **matching private** key (e.g. `npx web-push generate-vapid-keys`) and set **`WEBPUSH_VAPID_PRIVATE_KEY`** to that private key.

VAPID keys always come as a **pair**. The **public** key is used in the browser for `pushManager.subscribe()`; the **private** key must be kept secret and is used only in this function to send pushes.

## Invoke the function

**URL:** `https://<project-ref>.supabase.co/functions/v1/send-push`

**Method:** `POST`

**Headers:** `Content-Type: application/json` and (if required by your project) `Authorization: Bearer <anon-or-service-key>`.

**Body:**

```json
{
  "subscription": {
    "endpoint": "https://fcm.googleapis.com/...",
    "keys": {
      "p256dh": "<base64url client public key>",
      "auth": "<base64url auth secret>"
    }
  },
  "title": "Optional title",
  "body": "Notification body",
  "data": { "url": "/some-path" }
}
```

`subscription` is the object from `pushManager.subscribe()` in the browser. `title`, `body`, and `data` are optional.

## Migrations applied via MCP

- **create_devices_table** – `public.devices` for FCM tokens.
- **rls_android_app** – RLS and `current_user_pk()` for app/Clerk.
- **devices_fcm_upsert_rpc** – `upsert_my_device(p_reg_token, p_device)` for storing tokens.

Run these in Supabase if not already applied (e.g. via MCP `apply_migration`).
