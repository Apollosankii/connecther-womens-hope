-- Fix: when admin unsuspends a provider, also restore available_for_booking = true
-- so the provider is immediately bookable after unsuspension.

CREATE OR REPLACE FUNCTION public.admin_set_provider_suspended(
  p_user_id text,
  p_suspended boolean
)
RETURNS TABLE (ok boolean, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_provider_id integer;
  br RECORD;
  v_old_status text;
BEGIN
  IF NOT public.is_admin() THEN
    RETURN QUERY SELECT false, 'not_admin'::text;
    RETURN;
  END IF;

  SELECT u.id INTO v_provider_id
  FROM public.users u
  WHERE u.user_id = p_user_id
    AND COALESCE(u.service_provider, false) = true
  LIMIT 1;

  IF v_provider_id IS NULL THEN
    RETURN QUERY SELECT false, 'provider_not_found'::text;
    RETURN;
  END IF;

  IF COALESCE(p_suspended, false) THEN
  FOR br IN
    SELECT br.id, br.client_id, br.provider_id, br.status, br.job_id, br.service_id
    FROM public.booking_requests br
    WHERE br.provider_id = v_provider_id
      AND br.status IN ('pending', 'accepted')
    FOR UPDATE
  LOOP
    v_old_status := br.status;

    IF v_old_status = 'pending' THEN
      PERFORM public.refund_booking_connect(br.id);
    END IF;

    IF br.job_id IS NOT NULL THEN
      UPDATE public.jobs j
      SET complete = true,
          completed_at = COALESCE(j.completed_at, now())
      WHERE j.id = br.job_id
        AND COALESCE(j.complete, false) = false;
    END IF;

    UPDATE public.booking_requests
    SET status = 'cancelled'
    WHERE id = br.id;

    -- Trigger notifies provider; pending cancellations do not notify client — notify here.
    IF v_old_status = 'pending' THEN
      PERFORM public.notify_app_user(
        br.client_id,
        'Booking cancelled',
        'This booking was cancelled because the provider account was suspended.',
        'booking_cancelled',
        jsonb_build_object(
          'request_id', br.id::text,
          'service_id', br.service_id::text,
          'job_id', COALESCE(br.job_id::text, '')
        )
      );
    END IF;
  END LOOP;

    UPDATE public.users
    SET provider_suspended = true,
        provider_suspended_at = now(),
        available_for_booking = false
    WHERE id = v_provider_id;
  ELSE
    UPDATE public.users
    SET provider_suspended = false,
        provider_suspended_at = NULL,
        available_for_booking = true
    WHERE id = v_provider_id;
  END IF;

  RETURN QUERY SELECT true, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.admin_set_provider_suspended(text, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_set_provider_suspended(text, boolean) TO service_role;
