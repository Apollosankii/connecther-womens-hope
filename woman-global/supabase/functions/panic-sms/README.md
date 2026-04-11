# panic-sms

Sends GBV panic alert SMS via **Twilio Programmable Messaging** for users with an **active** `user_plan_subscriptions` row (`status = active`, `started_at` ≤ today ≤ `expires_at`). Uses the same gateway auth pattern as `phone-verify`: **Supabase anon** in `Authorization` / `apikey`, **Firebase ID token** in `X-Firebase-Id-Token`.

## Request

`POST` JSON:

```json
{
  "recipients": ["+254712345678"],
  "latitude": -1.28,
  "longitude": 36.82
}
```

- `recipients`: 1–5 strings, **E.164** only (`+[country][number]`).
- `latitude` / `longitude`: optional; included as a Maps link in the body.

## Response

- `200` — `{ "ok": true, "sent_count": N }`
- `403` — `{ "code": "NOT_SUBSCRIBED", ... }` (no user row or no active subscription window)
- `429` — `{ "code": "RATE_LIMIT", ... }` (max dispatches per Firebase UID per 24h)
- `502` — `{ "code": "TWILIO_ERROR", ... }`

## Edge secrets

| Secret | Required | Notes |
|--------|----------|--------|
| `SUPABASE_URL` | yes | Auto |
| `SUPABASE_SERVICE_ROLE_KEY` | yes | Auto |
| `FIREBASE_PROJECT_ID` | yes | Same as `phone-verify` |
| `TWILIO_ACCOUNT_SID` | yes | Same account as Verify |
| `TWILIO_AUTH_TOKEN` | yes | |
| `TWILIO_MESSAGING_SERVICE_SID` | one of two | Preferred: Messaging Service SID (`MG…`) |
| `TWILIO_PANIC_FROM_NUMBER` | one of two | E.164 sender if not using a Messaging Service |

**Do not** use `TWILIO_VERIFY_SERVICE_SID` (`VA…`) here — Verify is for OTP only.

## Rate limits (server-side)

Limits are read from **`platform_settings`** (singleton `id = 1`), editable in the **admin portal → Subscription → Free trial connects / platform settings**:

| Column | Meaning |
|--------|---------|
| `panic_sms_max_dispatches_per_24h` | Max successful **batches** per Firebase user per rolling 24h (each batch can text up to 5 numbers). |
| `panic_sms_min_seconds_between` | Minimum seconds between two successful batches for the same user (`0` = no cooldown). |
| `panic_sms_max_global_per_hour` | Max successful batches **platform-wide** per rolling hour (abuse fuse). |

If settings are missing, defaults are **6 / 180s / 200**. Responses: `429` with `RATE_LIMIT`, `RATE_LIMIT_COOLDOWN`, or `RATE_LIMIT_GLOBAL`.

Each dispatch row stores **`request_ip`** (first `x-forwarded-for` hop when present) for audit.

## Database

Migrations: `panic_sms_dispatch` (audit + per-user limits), `platform_settings` panic columns. Edge uses the **service role**; admins can **SELECT** `panic_sms_dispatch` for support (RLS `is_admin()`).
