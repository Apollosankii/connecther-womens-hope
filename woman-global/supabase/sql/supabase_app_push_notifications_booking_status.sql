-- Additional FCM triggers: booking declined / expired / cancelled + job completed.
-- Depends on: supabase_devices_fcm.sql (devices, upsert_my_device), supabase_app_push_notifications_booking_chat.sql (notify_app_user).
-- Deploy notify-app-user Edge Function and ensure pg_net HTTP can reach it (see README in that function).

-- Booking lifecycle updates (beyond INSERT + accepted, which are in supabase_app_push_notifications_booking_chat.sql)
CREATE OR REPLACE FUNCTION public.tr_notify_booking_status_changes()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF TG_OP <> 'UPDATE' THEN
    RETURN NEW;
  END IF;
  IF NEW.status IS NOT DISTINCT FROM OLD.status THEN
    RETURN NEW;
  END IF;

  IF NEW.status = 'declined' AND OLD.status IS DISTINCT FROM 'declined' THEN
    PERFORM public.notify_app_user(
      NEW.client_id,
      'Booking declined',
      'A provider declined your booking request.',
      'booking_declined',
      jsonb_build_object(
        'request_id', NEW.id::text,
        'service_id', NEW.service_id::text
      )
    );
  END IF;

  IF NEW.status = 'expired' AND OLD.status IS DISTINCT FROM 'expired' THEN
    PERFORM public.notify_app_user(
      NEW.client_id,
      'Booking expired',
      'A booking request expired before it was accepted.',
      'booking_expired',
      jsonb_build_object('request_id', NEW.id::text)
    );
  END IF;

  IF NEW.status = 'cancelled' AND OLD.status IS DISTINCT FROM 'cancelled' THEN
    PERFORM public.notify_app_user(
      NEW.provider_id,
      'Booking cancelled',
      'A pending booking was cancelled.',
      'booking_cancelled',
      jsonb_build_object(
        'request_id', NEW.id::text,
        'service_id', NEW.service_id::text
      )
    );
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_notify_booking_status_changes ON public.booking_requests;
CREATE TRIGGER tr_notify_booking_status_changes
AFTER UPDATE ON public.booking_requests
FOR EACH ROW
EXECUTE FUNCTION public.tr_notify_booking_status_changes();

-- Provider marks job complete → notify client (open chat via payload)
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

DROP TRIGGER IF EXISTS tr_notify_job_completed ON public.jobs;
CREATE TRIGGER tr_notify_job_completed
AFTER UPDATE ON public.jobs
FOR EACH ROW
EXECUTE FUNCTION public.tr_notify_job_completed();
