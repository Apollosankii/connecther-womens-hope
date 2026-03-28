# Deploy Admin Portal on Vercel (PWA)

The Admin Portal is set up to deploy on **Vercel** as a **Progressive Web App (PWA)**.

## Production vs local: environment variables

- **Local:** The app reads from a **`.env` file** in `AdminPortal` (via `load_dotenv()`). Use `.env` for `SUPABASE_URL`, `SUPABASE_ANON_KEY`, etc. (service role not needed; anon + RLS used) Do **not** commit `.env` (it’s in `.gitignore`); use `.env.example` as a template.
- **Production (e.g. Vercel):** There is **no `.env` file**. You set the **same variable names** in the host’s dashboard (Vercel → Project → Settings → Environment Variables). The app uses `os.environ.get('SUPABASE_URL')` etc., so it works the same whether values come from `.env` (local) or from the platform (production).

So in production you **do not** upload or use the same `.env` file; you configure the same keys in Vercel (or your host) instead.

## What’s included

- **Vercel:** `vercel.json` and `build_vercel.py` (copies `static/` → `public/static/` so the CDN can serve assets).
- **PWA:** `public/manifest.json`, `public/sw.js`, and in `base.html`: manifest link, theme-color, and service worker registration. Users can “Add to Home Screen” and get an app-like experience.

## Deploy steps

1. **Use the AdminPortal folder as the project root** (Vercel’s root must be the folder that contains `app.py`).

   - If your repo root is `connecther/` and the app is in `woman-global/backend/AdminPortal/`, either:
     - **Option A:** In Vercel, set **Root Directory** to `woman-global/backend/AdminPortal`, or  
     - **Option B:** From your machine, deploy only that folder:
       ```bash
       cd woman-global/backend/AdminPortal
       npx vercel
       ```

2. **Environment variables (Vercel project → Settings → Environment Variables):**

   - **`SUPABASE_URL`** – Supabase project URL. **`SUPABASE_ANON_KEY`** – Supabase anon key. Both required for login and signup.
   - **`FLASK_SECRET_KEY`** – Random secret for session cookies (e.g. `openssl rand -hex 32`). Required for secure sessions in production.
   - Optional: **`CLERK_PUBLISHABLE_KEY`** – If you use Clerk, set this so “Sign in with Clerk” appears.

3. **Deploy:**

   - Push to Git and let Vercel build from the repo, or run `npx vercel --prod` from `woman-global/backend/AdminPortal`.

4. **After deploy:**

   - Open the Vercel URL (e.g. `https://your-project.vercel.app`). You’ll be redirected to `/login`.
   - Sign in with **Supabase Auth** (email + password). The email must match a row in your Supabase `administrators` table.
   - On supported browsers, you can install the app (e.g. “Add to Home Screen”) for the PWA experience.

## Limitations (Vercel serverless)

- **No persistent disk** – Only `/tmp` is writable and it’s ephemeral.
- **Request timeout** – ~30s; long-running requests may fail.
- **Cold starts** – First request after idle can be slower.
- The portal is **stateless** (session in cookie); it connects **directly to Supabase** for data. No FastAPI or separate backend needed.

## Local PWA check

Run the app locally, then in Chrome DevTools → Application → Manifest / Service Workers you can verify the manifest and that the service worker is registered.
