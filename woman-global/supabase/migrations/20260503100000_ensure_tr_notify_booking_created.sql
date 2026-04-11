-- Provider push when a client creates a booking request (INSERT on booking_requests).
-- Historically this lived only in sql/supabase_app_push_notifications_booking_chat.sql; some DBs never had the trigger.

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

COMMENT ON FUNCTION public.tr_notify_booking_created() IS
  'Sends FCM to provider (NEW.provider_id) via notify_app_user when a booking request is inserted.';
