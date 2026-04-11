-- Push notifications: make pg_net calls reachable + fix booking accepted / job completed copy & chat resolution.
-- 1) Edge Function notify-app-user must be deployed with verify_jwt = false (see supabase/config.toml).
-- 2) Set notify URL (once per project): INSERT ... ON CONFLICT below, or UPDATE the row.

CREATE TABLE IF NOT EXISTS public.push_notification_settings (
  id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  notify_app_user_url text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.push_notification_settings IS
  'Row id=1: full HTTPS URL to notify-app-user (e.g. https://YOUR_REF.supabase.co/functions/v1/notify-app-user).';

ALTER TABLE public.push_notification_settings ENABLE ROW LEVEL SECURITY;

-- No policies: only table owner / service_role (bypasses RLS) can manage; not exposed to anon/authenticated app users.

INSERT INTO public.push_notification_settings (id, notify_app_user_url)
VALUES (
  1,
  'https://nlemxqlnvurjkunxcnwu.supabase.co/functions/v1/notify-app-user'
)
ON CONFLICT (id) DO NOTHING;

CREATE OR REPLACE FUNCTION public.notify_app_user(
  p_user_id integer,
  p_title text,
  p_body text,
  p_type text,
  p_data jsonb DEFAULT '{}'::jsonb
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_url text;
BEGIN
  IF p_user_id IS NULL OR p_user_id <= 0 THEN
    RETURN;
  END IF;

  SELECT s.notify_app_user_url INTO v_url
  FROM public.push_notification_settings s
  WHERE s.id = 1
  LIMIT 1;

  IF v_url IS NULL OR length(trim(v_url)) = 0 THEN
    RAISE WARNING 'notify_app_user: push_notification_settings.notify_app_user_url is empty';
    RETURN;
  END IF;

  PERFORM net.http_post(
    url := trim(v_url),
    headers := '{"Content-Type": "application/json"}'::jsonb,
    body := jsonb_build_object(
      'user_id', p_user_id,
      'title', COALESCE(p_title, 'ConnectHer'),
      'body', COALESCE(p_body, ''),
      'data', jsonb_build_object('type', COALESCE(p_type, 'general')) || COALESCE(p_data, '{}'::jsonb)
    )
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.notify_app_user(integer, text, text, text, jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.notify_app_user(integer, text, text, text, jsonb) TO service_role;

-- Booking accepted: use quote_id from this row (correct chat) instead of guessing by service + parties.
CREATE OR REPLACE FUNCTION public.tr_notify_booking_accepted()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_chat_code text;
  v_quote_code text;
BEGIN
  IF NEW.status <> 'accepted' OR OLD.status = 'accepted' THEN
    RETURN NEW;
  END IF;

  IF NEW.quote_id IS NOT NULL THEN
    SELECT c.chat_code, q.quote_code
    INTO v_chat_code, v_quote_code
    FROM public.quotes q
    INNER JOIN public.chats c ON c.quote_id = q.id
    WHERE q.id = NEW.quote_id
    LIMIT 1;
  ELSE
    SELECT c.chat_code, q.quote_code
    INTO v_chat_code, v_quote_code
    FROM public.quotes q
    INNER JOIN public.chats c ON c.quote_id = q.id
    WHERE q.client_id = NEW.client_id
      AND q.provider_id = NEW.provider_id
      AND q.service_id = NEW.service_id
    ORDER BY q.id DESC
    LIMIT 1;
  END IF;

  PERFORM public.notify_app_user(
    NEW.client_id,
    'Booking accepted',
    'Your booking request has been accepted. Tap to open chat.',
    'booking_accepted',
    jsonb_build_object(
      'chat_code', COALESCE(v_chat_code, ''),
      'quote_id', COALESCE(v_quote_code, '')
    )
  );

  RETURN NEW;
END;
$$;

-- Seeker completes via complete_my_job RPC — message must not imply provider completed it.
CREATE OR REPLACE FUNCTION public.tr_notify_job_completed()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_client_id integer;
  v_chat_code text;
  v_quote_code text;
BEGIN
  IF NEW.complete IS NOT TRUE OR COALESCE(OLD.complete, false) IS TRUE THEN
    RETURN NEW;
  END IF;

  SELECT q.client_id, c.chat_code, q.quote_code
  INTO v_client_id, v_chat_code, v_quote_code
  FROM public.quotes q
  LEFT JOIN public.chats c ON c.quote_id = q.id
  WHERE q.id = NEW.quote_id
  LIMIT 1;

  IF v_client_id IS NOT NULL THEN
    PERFORM public.notify_app_user(
      v_client_id,
      'Job completed',
      'Your booking has been marked complete. Tap to open chat.',
      'job_completed',
      jsonb_build_object(
        'chat_code', COALESCE(v_chat_code, ''),
        'quote_id', COALESCE(v_quote_code, ''),
        'job_id', NEW.id::text
      )
    );
  END IF;

  RETURN NEW;
END;
$$;
