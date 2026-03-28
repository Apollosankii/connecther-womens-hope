# notify-app-user – Secrets Setup

Set these in **Supabase Dashboard → Edge Functions → notify-app-user → Secrets**.

## Required secrets

| Secret name | Value (from your Firebase service account JSON) |
|-------------|-------------------------------------------------|
| `FIREBASE_PROJECT_ID` | `project_id` (must match the Firebase project used by the Android app; see note below) |
| `FIREBASE_CLIENT_EMAIL` | `client_email` |
| `FIREBASE_PRIVATE_KEY` | Full `private_key` string: include `-----BEGIN PRIVATE KEY-----` and `-----END PRIVATE KEY-----`, and keep the `\n` line breaks (paste as-is from the JSON, or use literal newlines). |

## Project: connecther-d6e1a (Option B)

We use **Option B**: the Android app is switched to Firebase project **connecther-d6e1a** so the same project’s service account can send FCM.

- Set **FIREBASE_PROJECT_ID** = `connecther-d6e1a`.
- The app’s **google-services.json** must be the one downloaded from project **connecther-d6e1a** (see `mobile/android/FCM_SETUP.md`). Replace `app/google-services.json` with that file.

## Optional

- **`NOTIFY_SECRET`** – If set, callers must send the header `x-notify-secret` with this value (e.g. set the same value in the Admin Portal `.env`).

## After saving

Secrets take effect on the next invocation. No need to redeploy the function.
