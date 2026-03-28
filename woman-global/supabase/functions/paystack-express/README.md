# `paystack-express` (single Edge Function)

This function handles **Paystack subscription payments** for ConnectHer.

It supports 3 modes:
1. **App initialize (POST)**: app calls `/functions/v1/paystack-express` with `{ plan_id, email }`. Function:
   - validates the signed-in Clerk user,
   - creates a `paystack_transactions` row (status `pending`),
   - calls Paystack `/transaction/initialize`,
   - returns `{ authorization_url, reference }` to the app.

2. **Paystack webhook (POST)**: Paystack sends `{ event, data }` to the same URL (webhook URL configured in Paystack dashboard).
   - function verifies `x-paystack-signature` using `PAYSTACK_WEBHOOK_SECRET`,
   - on `charge.success`, it activates a new row in `user_plan_subscriptions` and expires the previous active one.

3. **Customer redirect page (GET)**: Paystack redirects the user after payment to
   `.../functions/v1/paystack-express?redirect=1`. Function returns a simple HTML page.
   Subscription activation happens via webhook.

---

## Required Edge Secrets / Env vars
- `PAYSTACK_SECRET_KEY`: Paystack secret key (server side only)
- `PAYSTACK_WEBHOOK_SECRET`: Paystack webhook secret for signature verification

Optional:
- `PAYSTACK_BASE_URL`: default `https://api.paystack.co`
- `PAYSTACK_CHANNELS`: comma-separated or JSON array of channels for **Initialize Transaction**, e.g. `card,ussd,bank_transfer,mobile_money` or `["card","bank"]`. Valid: `card`, `bank`, `ussd`, `qr`, `mobile_money`, `bank_transfer`. If omitted, all valid channels are requested (Paystack still only offers methods enabled for your account/currency).

### Webhook checklist (manual verification)

1. In [Paystack Dashboard → Settings → API & Webhooks](https://dashboard.paystack.com/#/settings/developer), set **Webhook URL** to:
   - `https://<YOUR_PROJECT_REF>.supabase.co/functions/v1/paystack-express`
2. Copy the **webhook signing secret** into Supabase secret **`PAYSTACK_WEBHOOK_SECRET`** (must match exactly).
3. Deploy this function with `verify_jwt = false` so Paystack’s unsigned server-to-server POST is accepted.
4. After a **successful** test payment (any channel), confirm in Paystack **Logs** a `charge.success` webhook and in Supabase that **`paystack_transactions.status`** becomes `success` and **`user_plan_subscriptions`** has a new **active** row.

---

## Database
Run:
- `backend/sql/supabase_paystack_subscription.sql`

The webhook activates `user_plan_subscriptions` using existing `subscription_plans.duration_*` and `connects_*` columns.

---

## Deploy
From `woman-global/`:

```bash
supabase functions deploy paystack-express --no-verify-jwt
```

Then in `supabase/config.toml`, ensure:

```toml
[functions.paystack-express]
verify_jwt = false
```

---

## Paystack webhook URL
Configure Paystack webhook to point to:
- `https://<YOUR_PROJECT_REF>.supabase.co/functions/v1/paystack-express`

---

## Reference
`paystack_transactions.reference` stores the same Paystack transaction reference.

