-- Enforce one normalized email per user; dedupe existing rows (keeps best row per email).
-- Applied on hosted project via MCP; keep this file for local/CI migrations.

WITH ranked AS (
  SELECT id,
    row_number() OVER (
      PARTITION BY lower(trim(both from email))
      ORDER BY
        (clerk_user_id IS NOT NULL) DESC,
        id ASC
    ) AS rn
  FROM public.users
  WHERE email IS NOT NULL AND trim(both from email) <> ''
)
UPDATE public.users u
SET email = NULL
FROM ranked r
WHERE u.id = r.id AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_unique
ON public.users (lower(trim(both from email)))
WHERE email IS NOT NULL AND trim(both from email) <> '';

COMMENT ON INDEX public.users_email_lower_unique IS 'One sign-in email per app user (case/spacing insensitive).';
