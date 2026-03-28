# notify-app-user Edge Function

Sends FCM (Firebase Cloud Messaging) push notifications to an app user by looking up their device tokens in Supabase `public.devices` (keyed by `users.id`).

## When it’s used

- **Admin Portal:** After an admin approves a provider, the portal calls this function so the provider gets an “Application approved” notification on their device.

## Request

- **Method:** POST  
- **Headers:**  
  - `Content-Type: application/json`  
  - `Authorization: Bearer <SUPABASE_ANON_KEY>` (or valid JWT)  
  - If `NOTIFY_SECRET` is set: `x-notify-secret: <NOTIFY_SECRET>`
- **Body:**
  ```json
  {
    "user_id": 123,
    "title": "Application approved",
    "body": "Your provider application has been approved.",
    "data": { "type": "provider_approved" }
  }
  ```
  - `user_id` (number, required): `public.users.id` (internal PK).  
  - `title`, `body` (optional): notification title and body.  
  - `data` (optional): key-value map (values stringified) for FCM data payload; use `type` for app routing (e.g. `message`, `provider_approved`, `job`).

## Secrets (Supabase Dashboard → Edge Functions → notify-app-user → Secrets)

| Secret | Required | Description |
|--------|----------|-------------|
| `FIREBASE_PROJECT_ID` | Yes | Firebase project ID (e.g. from `google-services.json`). |
| `FIREBASE_CLIENT_EMAIL` | Yes | From Firebase Console → Project settings → Service accounts → Generate new key → JSON → `client_email`. |
| `FIREBASE_PRIVATE_KEY` | Yes | Same JSON → `private_key`. Paste the full PEM; keep `\n` as literal or use real newlines. |
| `NOTIFY_SECRET` | No | If set, caller must send header `x-notify-secret` with this value. |

Supabase sets `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` for the function (used to read `devices`).

## Response

- **200:** `{ "success": true, "sent": 1, "total": 1 }` (or `sent: 0` if no devices).  
- **4xx/5xx:** `{ "error": "...", "detail": "..." }`.

## Deploy

From repo root (with Supabase CLI linked to your project):

```bash
supabase functions deploy notify-app-user
```

Then set the secrets in the Dashboard (or via CLI).
