# phone-verify (Twilio Verify)

Sends and validates SMS codes via **Twilio Verify** (not Firebase Phone Auth). Requires a valid **Firebase ID token** (see headers below), including **anonymous** users used during registration.

**Firebase:** turn on **Anonymous** under Authentication → Sign-in method. If it is disabled, the Android app’s `signInAnonymously()` step fails before Twilio runs (often shown as “restricted” / `ERROR_ADMIN_RESTRICTED_OPERATION`).

## Supabase secrets

Set in Dashboard → Edge Functions → `phone-verify` → Secrets:

| Secret | Description |
|--------|-------------|
| `TWILIO_ACCOUNT_SID` | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | Twilio Auth Token |
| `TWILIO_VERIFY_SERVICE_SID` | Verify Service SID (`VA...`) |
| `FIREBASE_PROJECT_ID` | Same as auth-bridge |
| `SUPABASE_URL` | Auto |
| `SUPABASE_SERVICE_ROLE_KEY` | Auto |

## Request

`POST /functions/v1/phone-verify`  
Headers:

- **`X-Firebase-Id-Token`**: Firebase ID token (required for verification inside the function).
- **`Authorization`**: `Bearer <Supabase anon key>` and **`apikey`**: same anon key — so the Supabase gateway accepts the call. (Putting only the Firebase token in `Authorization` can cause **401** at the gateway because it is not a Supabase JWT.)
- **Legacy:** `Authorization: Bearer <Firebase ID token>` only, if the project gateway does not enforce JWT on this function (`verify_jwt = false`).

`Content-Type: application/json`

Body:

```json
{ "action": "start", "phone": "+254712345678" }
```

```json
{ "action": "check", "phone": "+254712345678", "code": "123456" }
```

## Response

`{ "ok": true }` or `{ "ok": true, "verified": true }` on successful check.

Errors: `401` invalid Firebase token, `400` validation (or `WRONG_AUTH_HEADER` if the Firebase token was not sent in `X-Firebase-Id-Token`), `409` phone already on another account, `429` rate limit, `502` Twilio API rejected the send/check.

### HTTP 500 with `SERVER_MISCONFIG` / “Twilio not configured”

The function returns **500** when **Twilio secrets are not set** on this Edge Function (Dashboard → `phone-verify` → Secrets). Set all three: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_VERIFY_SERVICE_SID` (`VA…` from Twilio Verify → Services).

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are usually injected automatically; if those are missing you also get `SERVER_MISCONFIG`.
