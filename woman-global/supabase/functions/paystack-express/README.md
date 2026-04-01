# `paystack-express` (single Edge Function)

This function handles **Paystack subscription payments** for ConnectHer.

It supports 3 modes:
1. **App initialize (POST)**: app calls `/functions/v1/paystack-express` with `{ plan_id, email }`. Function:
   - validates the signed-in Clerk user,
   - creates a `paystack_transactions` row (status `pending`),
   - calls Paystack `/transaction/initialize`,
   - returns `{ access_code, authorization_url, reference }` to the app.
     (`access_code` is required for the **paystack-ui PaymentSheet** on Android; `authorization_url` is the hosted checkout link.)

2. **Paystack webhook (POST)**: Paystack sends `{ event, data }` to the same URL (webhook URL configured in Paystack dashboard).
   - function verifies `x-paystack-signature` using `PAYSTACK_WEBHOOK_SECRET` or, if unset, **`PAYSTACK_SECRET_KEY`** (same `sk_` key Paystack documents for HMAC),
   - on `charge.success`, it activates a new row in `user_plan_subscriptions` and expires the previous active one.

3. **Customer redirect page (GET)**: Paystack redirects the user after payment to
   `.../functions/v1/paystack-express?redirect=1`. Function returns a simple HTML page.
   Subscription activation happens via webhook.

---

## Required Edge Secrets / Env vars
- `PAYSTACK_SECRET_KEY`: Paystack **secret key** (`sk_live_…` / `sk_test_…`, server only). Used for `/transaction/initialize` and (by Paystack’s docs) for signing webhook payloads.
- `PAYSTACK_WEBHOOK_SECRET` (optional): If set, used to verify `x-paystack-signature` on webhooks. If **omitted**, the function uses **`PAYSTACK_SECRET_KEY`** instead — which matches [Paystack’s webhook docs](https://paystack.com/docs/payments/webhooks/) (HMAC-SHA512 with your **secret key**). There is typically **no separate “webhook secret”** field in the dashboard; use the same `sk_` key you use for the API.

Optional:
- `PAYSTACK_BASE_URL`: default `https://api.paystack.co`
- `PAYSTACK_CHANNELS`: comma-separated or JSON array of channels for **Initialize Transaction**, e.g. `card,ussd,bank_transfer,mobile_money` or `["card","bank"]`. Valid: `card`, `bank`, `ussd`, `qr`, `mobile_money`, `bank_transfer`. If omitted, all valid channels are requested (Paystack still only offers methods enabled for your account/currency).

### Android: “access code not found” after switching to **live**

Paystack ties each `access_code` to the **secret key** used in `/transaction/initialize`. The mobile SDK resolves that code with your **public key**. If those keys are not from the **same mode and same Paystack account**, the SDK fails with **access code not found** (or similar).

1. **Supabase** (Edge Function secrets): set `PAYSTACK_SECRET_KEY` to **`sk_live_…`** (not `sk_test_…`). Redeploy is not always required after a secret change, but verify secrets saved: Dashboard → Project → Edge Functions → Secrets, or `supabase secrets list`.
2. **Android**: set `PAYSTACK_PUBLIC_KEY` to **`pk_live_…`** from the **same** Paystack business. It is baked into `BuildConfig` via `woman-global/.env` / `gradle.properties` → **clean + rebuild** the app (e.g. `./gradlew clean assembleDebug`) so old `pk_test` is not cached.
3. **Paystack dashboard**: switch to **Live** when going live; webhook signatures still use your **live secret key** (`sk_live_…`), not a separate webhook-only field.
4. Confirm function logs after init: `paystack initialize ok` with `paystackMode: "live"` — if it says `test` while the app uses `pk_live`, fix the secret key on Supabase.

**Common gotcha (this repo):** The Android app used to embed a **hardcoded `pk_test_` fallback** in Gradle when `PAYSTACK_PUBLIC_KEY` was missing from `.env`. That produces **exactly** `Access code not found` after you move the server to **`sk_live_*`**. Fix: set `PAYSTACK_PUBLIC_KEY=pk_live_…` in **`woman-global/.env`** (or `gradle.properties`), then **Sync / clean rebuild** and watch logcat: `PaystackNativePay` should log `mode=LIVE`, and the Gradle build should print `PAYSTACK_PUBLIC_KEY loaded (pk_live)`.

### Webhook checklist (manual verification)

1. In [Paystack Dashboard → Settings → API & Webhooks](https://dashboard.paystack.com/#/settings/developer), set **Webhook URL** to:
   - `https://<YOUR_PROJECT_REF>.supabase.co/functions/v1/paystack-express`
2. Webhook verification uses your **secret key** (`sk_…`). Either set **`PAYSTACK_WEBHOOK_SECRET`** to that same key or rely on **`PAYSTACK_SECRET_KEY`** only (the function falls back automatically).
3. Deploy this function with `verify_jwt = false` so Paystack’s unsigned server-to-server POST is accepted.
4. After a **successful** test payment (any channel), confirm in Paystack **Logs** a `charge.success` webhook and in Supabase that **`paystack_transactions.status`** becomes `success` and **`user_plan_subscriptions`** has a new **active** row.

---

## Database
Run:
- `backend/sql/supabase_paystack_subscription.sql`

The webhook activates `user_plan_subscriptions` using existing `subscription_plans.duration_*` and `connects_*` columns.

---

## Deploy

`index.ts` imports **`./helpers.ts`**, **`./initialize.ts`**, **`./webhook.ts`**, **`./verify.ts`**, and (transitively) **`finalize_paystack_transaction.ts`**, **`supabase_rest.ts`**, **`paystack_channels.ts`**, plus **`deno.json`**.

If the Supabase bundler reports **`Module not found ".../helpers.ts"`**, only `index.ts` was uploaded or synced (common with dashboard paste or an incomplete archive). The bundler must see **every** local module.

### Recommended: Supabase CLI (uploads the whole folder)

From `woman-global/` (after `supabase link`):

```bash
supabase functions deploy paystack-express --no-verify-jwt
```

If the Android app calls **`paystack-checkout`** instead, deploy under that name (or keep one slug and point webhooks/URLs to it):

```bash
supabase functions deploy paystack-checkout --no-verify-jwt
```

If `paystack-checkout` is a **separate** function folder in your project, copy/sync the same sources into that folder before deploying.

Then in `supabase/config.toml`, ensure:

```toml
[functions.paystack-express]
verify_jwt = false
```

### Dashboard / single-file workflow

In `supabase/functions/paystack-express/`:

```bash
npm install
npm run bundle
```

Open **`index.standalone.ts`** and paste its full contents **over** the dashboard’s **`index.ts`** (one self-contained file, no `./helpers.ts` imports). Keep **`deno.json`** in the function bundle if the UI asks for it. Alternatively, upload **all** of these source files together:  
`index.ts`, `helpers.ts`, `initialize.ts`, `webhook.ts`, `verify.ts`, `finalize_paystack_transaction.ts`, `supabase_rest.ts`, `paystack_channels.ts`, `deno.json`.

---

## Paystack webhook URL
Configure Paystack webhook to point to:
- `https://<YOUR_PROJECT_REF>.supabase.co/functions/v1/paystack-express`

---

## Reference
`paystack_transactions.reference` stores the same Paystack transaction reference.

