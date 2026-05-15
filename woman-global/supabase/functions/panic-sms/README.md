# panic-sms

Sends GBV panic alert SMS via **Africa's Talking SMS** for users with an **active** `user_plan_subscriptions` row (`status = active`, `started_at` ≤ today ≤ `expires_at`). Uses the same gateway auth pattern as `phone-verify`: **Supabase anon** in `Authorization` / `apikey`, **Firebase ID token** in `X-Firebase-Id-Token`.

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

- `200` — `{ "ok": true, "sent_count": N }` — Sends via Africa's Talking using **one bulk** request (`to` comma-separated, `bulkSMSMode=1`) matching the official SDK; per-recipient fallback only if bulk HTTP/parse fails.
- `200` (partial) — `{ "ok": true, "sent_count": N, "partial": true, "failed_recipients": [...] }` when at least one number succeeded but another failed (e.g. Africa's Talking `UserInBlacklist`).
- `403` — `{ "code": "NOT_SUBSCRIBED", ... }` (no user row or no active subscription window)
- `403` — `{ "code": "TWILIO_DISABLED", ... }` when **`platform_settings.panic_sms_twilio_enabled`** is false (admin portal); the app should fall back to on-device SMS for subscribers. (Flag name is legacy; it now controls Africa's Talking sends.)
- `429` — `{ "code": "RATE_LIMIT", ... }` (max dispatches per Firebase UID per 24h)
- `502` — `{ "code": "TWILIO_ERROR", ... }` — generic Africa's Talking / transport failure.
- `502` — `{ "code": "AT_RECIPIENT_BLACKLISTED", ... }` when **every** recipient failed with `UserInBlacklist` (common in **sandbox**: add the number as a test recipient or remove it from the SMS blacklist in the Africa's Talking dashboard).

### `UserInBlacklist` (statusCode 406)

Africa's Talking is **refusing to deliver to that exact MSISDN** from your app/sender. It is **not** an E.164 formatting bug (the number is already normalized as `+254…`).

**Sandbox:** add the number as a test recipient / remove from SMS blacklist in the AT dashboard.

**Production:** common causes include: the subscriber **opted out** of your messages, the number is on an **internal or carrier block list**, your **sender ID / shortcode** is not permitted for that route, or AT support must **unblock** the MSISDN for your account. Open a ticket with [Africa's Talking support](https://help.africastalking.com/) with the failing `number`, `status`, `statusCode`, and your app username. Until AT clears it, use a **different** emergency contact number that successfully receives a test SMS from the same sender.

If **every** number you try returns `UserInBlacklist`, check: (1) you are not using **`username=sandbox`** with the **production** API host — use your **live** app username and API key; (2) both handsets have not globally blocked promo SMS (Kenya: dial `*456*9#` → marketing → activate per [AT help](https://help.africastalking.com/en/articles/5209677-userinblacklist)); (3) **`AFRICASTALKING_FROM`** — if set, try unsetting it in secrets (this function retries once without `from` after a blacklist when `from` was set).

## Edge secrets

| Secret | Required | Notes |
|--------|----------|--------|
| `SUPABASE_URL` | yes | Auto |
| `SUPABASE_SERVICE_ROLE_KEY` | yes | Auto |
| `FIREBASE_PROJECT_ID` | yes | Same as `phone-verify` |
| `AFRICASTALKING_USERNAME` | when SMS on | **Production:** your live app username from the Africa's Talking dashboard (not the literal `sandbox` user unless you really use sandbox). |
| `AFRICASTALKING_API_KEY` | when SMS on | **Production:** live API key for that app. |
| `AFRICASTALKING_FROM` | optional | Sender ID / shortcode (depends on your AT account + country rules). |
| `AFRICASTALKING_USE_SANDBOX` | optional | If `true` / `1` / `yes`, SMS is sent via **`https://api.sandbox.africastalking.com/version1/messaging`**. **Omitted or false = production** (`https://api.africastalking.com/version1/messaging`). |
| `AFRICASTALKING_MESSAGING_URL` | optional | Full override URL (including path), e.g. `https://api.africastalking.com/version1/messaging`. If set, it wins over `AFRICASTALKING_USE_SANDBOX`. |
| `AFRICASTALKING_BULK_SMS_MODE` | optional | Form field `bulkSMSMode` (default **`1`**). Official AT Node SDK sends `1` for normal SMS on `/version1/messaging`; omitting it can produce **misleading** recipient statuses. Set `0` only if AT support asks for premium / non-bulk behaviour. |

Twilio secrets are no longer used by this function (Twilio remains used for `phone-verify` OTP unless you migrate that too).

## Rate limits (server-side)

Limits are read from **`platform_settings`** (singleton `id = 1`), editable in the **admin portal → Subscription → Free trial connects / platform settings**:

| Column | Meaning |
|--------|---------|
| `panic_sms_max_dispatches_per_24h` | Max successful **batches** per Firebase user per rolling 24h (each batch can text up to 5 numbers). |
| `panic_sms_min_seconds_between` | Minimum seconds between two successful batches for the same user (`0` = no cooldown). |
| `panic_sms_max_global_per_hour` | Max successful batches **platform-wide** per rolling hour (abuse fuse). |
| `panic_sms_twilio_enabled` | When **false**, the function returns **`TWILIO_DISABLED`** (after subscription checks); no Twilio send and no `panic_sms_dispatch` row. Subscribers use device SMS in the app. |

If settings are missing, defaults are **6 / 180s / 200**, and Twilio remains **enabled**. Responses: `429` with `RATE_LIMIT`, `RATE_LIMIT_COOLDOWN`, or `RATE_LIMIT_GLOBAL`.

Each dispatch row stores **`request_ip`** (first `x-forwarded-for` hop when present) for audit.

## Database

Migrations: `panic_sms_dispatch` (audit + per-user limits), `platform_settings` panic columns. Edge uses the **service role**; admins can **SELECT** `panic_sms_dispatch` for support (RLS `is_admin()`).
