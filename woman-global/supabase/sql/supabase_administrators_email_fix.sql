-- Fix administrators_email_check constraint that may reject valid emails
-- Run in Supabase SQL Editor or via MCP (user-supabase execute_sql)
-- Error: "new row for relation administrators violates check constraint administrators_email_check"

-- Drop the overly strict constraint (if it exists)
ALTER TABLE administrators DROP CONSTRAINT IF EXISTS administrators_email_check;

-- Add permissive constraint: allow null, empty, or standard email format (includes clerk placeholder)
ALTER TABLE administrators ADD CONSTRAINT administrators_email_check CHECK (
  email IS NULL
  OR email = ''
  OR (email ~* '^[^@]+@[^@]+\.[^@]+$')
);
