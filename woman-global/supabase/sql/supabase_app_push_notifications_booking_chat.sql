-- Push notifications for app: new chat messages + booking accepted.
-- Requires Edge Function `notify-app-user` deployed.
-- Run in Supabase SQL Editor (project owner role).

-- Ensure pg_net is available for async HTTP from DB.
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Single-row config: full URL to notify-app-user (must match your Supabase project ref).
CREATE TABLE IF NOT EXISTS public.push_notification_settings (
  id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  notify_app_user_url text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.push_notification_settings ENABLE ROW LEVEL SECURITY;

INSERT INTO public.push_notification_settings (id, notify_app_user_url)
VALUES (1, 'https://nlemxqlnvurjkunxcnwu.supabase.co/functions/v1/notify-app-user')
ON CONFLICT (id) DO NOTHING;
-- Then in SQL Editor: UPDATE public.push_notification_settings SET notify_app_user_url = 'https://<ref>.supabase.co/functions/v1/notify-app-user' WHERE id = 1;

-- Helper: send payload to notify-app-user (deploy with verify_jwt = false; see supabase/config.toml).
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
    RAISE WARNING 'notify_app_user: set push_notification_settings.notify_app_user_url';
    RETURN;
  END IF;

  PERFORM net.http_post(
    url := trim(v_url),
    headers := '{"Content-Type":"application/json"}'::jsonb,
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

-- Trigger: notify receiver when a new message is inserted.
CREATE OR REPLACE FUNCTION public.tr_notify_new_message()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_client_id integer;
  v_provider_id integer;
  v_target_user integer;
  v_chat_code text;
  v_quote_code text;
BEGIN
  SELECT q.client_id, q.provider_id, c.chat_code, q.quote_code
  INTO v_client_id, v_provider_id, v_chat_code, v_quote_code
  FROM public.chats c
  JOIN public.quotes q ON q.id = c.quote_id
  WHERE c.id = NEW.chat_id
  LIMIT 1;

  IF v_client_id IS NULL OR v_provider_id IS NULL THEN
    RETURN NEW;
  END IF;

  IF NEW.sender_id = (SELECT u.user_id FROM public.users u WHERE u.id = v_client_id LIMIT 1) THEN
    v_target_user := v_provider_id;
  ELSE
    v_target_user := v_client_id;
  END IF;

  PERFORM public.notify_app_user(
    v_target_user,
    'New message',
    COALESCE(NEW.content, 'You have a new chat message.'),
    'message',
    jsonb_build_object(
      'chat_code', COALESCE(v_chat_code, ''),
      'quote_id', COALESCE(v_quote_code, '')
    )
  );

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_notify_new_message ON public.messages;
CREATE TRIGGER tr_notify_new_message
AFTER INSERT ON public.messages
FOR EACH ROW
EXECUTE FUNCTION public.tr_notify_new_message();

-- Trigger: notify client when booking is accepted.
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

DROP TRIGGER IF EXISTS tr_notify_booking_accepted ON public.booking_requests;
CREATE TRIGGER tr_notify_booking_accepted
AFTER UPDATE ON public.booking_requests
FOR EACH ROW
EXECUTE FUNCTION public.tr_notify_booking_accepted();

-- Trigger: notify provider when a new booking request is created.
CREATE OR REPLACE FUNCTION public.tr_notify_booking_created()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public.notify_app_user(
    NEW.provider_id,
    'New booking request',
    'You have a new booking request. Open Jobs to review it.',
    'booking_created',
    jsonb_build_object(
      'request_id', NEW.id::text,
      'service_id', NEW.service_id::text
    )
  );
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_notify_booking_created ON public.booking_requests;
CREATE TRIGGER tr_notify_booking_created
AFTER INSERT ON public.booking_requests
FOR EACH ROW
EXECUTE FUNCTION public.tr_notify_booking_created();
