# ConnectHer Admin Portal

Flask admin panel that connects **directly to Supabase** (no FastAPI). Uses Clerk or Supabase Auth for sign-in.

- **Deploy:** [VERCEL_DEPLOY.md](VERCEL_DEPLOY.md)
- **Clerk setup:** [CLERK_ADMIN_SETUP.md](CLERK_ADMIN_SETUP.md)
- **App + Admin harmony:** [ADMIN_APP_HARMONY.md](../../ADMIN_APP_HARMONY.md)

## Run locally

```powershell
cd woman-global\backend\AdminPortal
pip install -r requirements.txt
.\run.ps1
```

Open **http://127.0.0.1:5020**. You’ll be redirected to `/login`.

## Configuration

Copy `.env.example` to `.env` and set:

| Variable | Required | Notes |
|----------|----------|-------|
| `SUPABASE_URL` | ✓ | Supabase project URL |
| `SUPABASE_ANON_KEY` | ✓ | Supabase anon key (RLS policies allow admin read/write when logged in) |
| `FLASK_SECRET_KEY` | ✓ (production) | Session cookie secret |
| `CLERK_PUBLISHABLE_KEY` | Optional | Enable Clerk sign-in |

## SQL migrations

Run these in the Supabase SQL Editor (in order):

All migrations are in `backend/sql/`. Run in order: see [backend/sql/README.md](../sql/README.md).  
For **connect quotas** on plans, run **`supabase_subscription_connects.sql`** after `supabase_subscription_plans.sql`.

See [ADMIN_APP_HARMONY.md](../../ADMIN_APP_HARMONY.md) for the full list.

## Sign-in

- **Supabase Auth:** Email + password. Must have a row in `administrators` with the same email.
- **Clerk:** If `CLERK_PUBLISHABLE_KEY` is set, “Sign in with Clerk” is shown. Requires `administrators.clerk_user_id` or `administrators.email` to match.

## Main routes

| Route | Description |
|-------|-------------|
| `/login` | Sign-in (Supabase + optional Clerk) |
| `/dash` | Dashboard |
| `/providers` | Service providers |
| `/provider/approvals` | Pending provider applications |
| `/app_users` | App users |
| `/services` | Services list |
| `/subscription/plans` | Subscription plans + **connect limits** (quota + calendar/term reset rule) |
| `/subscription/free-tier` | **Free connects** every new account gets before subscribing |
| `/subscription/user-subscriptions` | User plan rows, **connect balance**, grant / adjust |
| `/jobs/pending`, `/jobs/complete` | Jobs |

## Troubleshooting (HTTP 500)

1. Run from the `AdminPortal` folder so Flask finds `templates/` and `static/`.
2. Enable debug to see traceback: `$env:FLASK_DEBUG = "1"` then restart.
