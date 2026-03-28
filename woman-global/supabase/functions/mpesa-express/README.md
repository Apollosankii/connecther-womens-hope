# `mpesa-express` (single Edge Function)

One function handles **both**:

1. **Your app** — starts an **M-Pesa Express** STK Push (subscription amount from `subscription_plans`).
2. **Safaricom** — receives the **STK callback** (`Body.stkCallback`) on the **same URL**.

Daraja treats this product as **Lipa Na M-Pesa Online** / **STK Push**. Sandbox and production use the same paths; only the host and credentials change.

---

## Daraja API (authoritative flow)

| Step | Sandbox URL | Method | Auth |
|------|-------------|--------|------|
| **OAuth** | `https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials` | GET | `Authorization: Basic` Base64(`consumer_key:consumer_secret`) |
| **STK Push** | `https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest` | POST | `Authorization: Bearer <access_token>` |

Production: host `https://api.safaricom.co.ke` (same paths).  
Tokens expire in about **3600s**; this function **caches** OAuth and refreshes **~5 minutes before expiry**.

---

## Sandbox credentials (shared test till)

From Daraja **My Apps** you always use **your** app’s **Consumer Key** and **Consumer Secret** for OAuth.

For **M-Pesa Express** in sandbox, Safaricom documents a **shared** shortcode and passkey (not your own Paybill):

| Secret | Typical sandbox value |
|--------|------------------------|
| `MPESA_SHORTCODE` | `174379` |
| `MPESA_PASSKEY` | `bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919` |

Use **`DARAJA_BASE_URL`** = `https://sandbox.safaricom.co.ke` (or omit; it’s the default).

---

## Deploy

From the repo folder that contains **`supabase/config.toml`** (here: `woman-global/`):

```bash
cd woman-global
supabase functions deploy mpesa-express --no-verify-jwt
```

`supabase/config.toml` sets **`verify_jwt = false`** for this function so deploys stay correct even if you omit the flag (CLI versions may vary — keep using **`--no-verify-jwt`** to be sure).

### If the app gets **401** with Clerk signed in

1. **Gateway JWT check** — In **Dashboard → Edge Functions → `mpesa-express` → Details**, ensure **“Verify JWT with legacy secret”** / enforce JWT is **OFF**.  
   When it’s **ON**, Supabase expects a **Supabase Auth** JWT, not Clerk → **401** (~56-byte JSON) **before** your code runs.
2. Redeploy with **`--no-verify-jwt`** (or toggle off in Dashboard) after any change.
3. The app sends **`Authorization: Bearer <Clerk>`** and **`X-Clerk-Authorization`** (duplicate); the function reads either.

The code imports Supabase via **`npm:@supabase/supabase-js@…`** so Supabase’s bundler can resolve it.

**Safaricom** callbacks have **no** Clerk JWT; routing uses **`Body.stkCallback`** and **`?token=`**.

**Remove old functions** from the project if you still have them deployed:

- `mpesa-stk-subscribe`
- `mpesa-stk-callback`

---

## Callback URL

Each STK request sends a **full** `CallBackURL` to Daraja, for example:

`https://<PROJECT_REF>.supabase.co/functions/v1/mpesa-express?token=<verifier>`

You usually **do not** paste a different URL in the portal for every OAuth rotation — the URL (including `token`) is supplied **per `processrequest`**.

---

## App request

`POST /functions/v1/mpesa-express`

Headers: `Authorization: Bearer <Clerk JWT>`, `apikey: <anon>`, `Content-Type: application/json`

Body:

```json
{ "plan_id": 1, "phone": "2547XXXXXXXX" }
```

Success: `{ "ok": true, "customerMessage": "...", "checkoutRequestId": "..." }`

---

## Edge Function secrets

| Secret | Required | Notes |
|--------|----------|--------|
| `MPESA_CONSUMER_KEY` | Yes | Daraja app |
| `MPESA_CONSUMER_SECRET` | Yes | Daraja app |
| `MPESA_SHORTCODE` | Yes | Sandbox: often `174379` |
| `MPESA_PASSKEY` | Yes | Sandbox: see table above |
| `MPESA_CALLBACK_TOKEN` | No | If set, stable `?token=` for callbacks. If omitted, current **OAuth access_token** is used and stored in `mpesa_stk_payments.callback_verifier`. |
| `DARAJA_BASE_URL` | No | Default sandbox host |
| `SUPABASE_URL` | Usually auto | Don’t override with wrong value |
| `SUPABASE_SERVICE_ROLE_KEY` | Usually auto | Must be **service_role**, never anon |

---

## Production (go live)

**Code path is the same** — only **Edge secrets** and **Safaricom onboarding** change.

1. **Safaricom / Daraja** — Complete **live** onboarding for **Lipa Na M-Pesa Online** (STK) on your real **Paybill or Till**. You will receive production **shortcode**, **passkey**, and a **production** Daraja app **consumer key + secret** (not the sandbox pair, not `174379`).

2. **Set Edge secrets (production values)**  
   - `DARAJA_BASE_URL` = `https://api.safaricom.co.ke`  
   - `MPESA_CONSUMER_KEY` / `MPESA_CONSUMER_SECRET` = **production** Daraja app  
   - `MPESA_SHORTCODE` / `MPESA_PASSKEY` = **production** (from Safaricom)

3. **`MPESA_CALLBACK_TOKEN` (recommended for live)** — Set a **long random** static secret. Callbacks use `?token=…`; if you omit this, the function uses the **OAuth access token** as the verifier (it **rotates** ~hourly), which is fragile in production. With a static token, Daraja always calls the same query pattern; your rows still store `callback_verifier` for validation.

4. **Callback URL** — The function already sends  
   `https://<PROJECT_REF>.supabase.co/functions/v1/mpesa-express?token=<verifier>`.  
   If Safaricom or your bank asks for a **registered** callback URL, register that **base** URL (they may require **HTTPS**; Supabase provides it). Per-request `token` values still work as today.

5. **App & Supabase** — Point the mobile app at your **production** Supabase project URL and anon key if you use a separate project; Clerk production instance should match.

6. **After go-live** — Monitor **Daraja logs**, **Supabase Edge logs**, and `mpesa_stk_payments` / subscription rows; reconcile with M-Pesa statements.

---

## Database

Run **`backend/sql/supabase_mpesa_stk_subscription.sql`** — table **`mpesa_stk_payments`** (includes **`callback_verifier`**). See **`backend/sql/README.md`**.

For **connect balances** on new subscriptions, run **`backend/sql/supabase_subscription_connects.sql`** so **`subscription_plans`** and **`user_plan_subscriptions`** have the connect columns the callback writes.

---

## Troubleshooting

- **404** — Deploy `mpesa-express`; app must call `/functions/v1/mpesa-express`.
- **401** on callback — `?token=` must match static secret or the row’s `callback_verifier`.
- **500 DB / permission denied** — If you overrode `SUPABASE_SERVICE_ROLE_KEY` in Edge secrets with the **anon** key, remove the override or set the real **service_role** JWT. The function no longer pre-checks key shape (same as other functions).
- **500 DB** — Ensure `mpesa_stk_payments` exists and RLS is only bypassed via **service_role**.
- **502 `DARAJA_OAUTH_FAILED` / “Could not reach M-Pesa (auth)”** — The Edge Function could not complete Safaricom OAuth (`GET .../oauth/v1/generate`). Check the JSON body: **`daraja_http_status`** / **`daraja_detail`**.
  - **401** — Wrong consumer key/secret, or **sandbox keys** used with **`DARAJA_BASE_URL=https://api.safaricom.co.ke`** (production), or the opposite. For sandbox, leave `DARAJA_BASE_URL` unset or set `https://sandbox.safaricom.co.ke`.
  - **Network / timeout** — Retry; rare regional/TLS issues from the Edge region to Safaricom.
  - Secrets must be the **Daraja** app’s consumer key and **consumer secret** (trimmed, no extra quotes).
