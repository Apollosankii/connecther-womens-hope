-- Fix: allow a new booking with the same provider after the prior job is marked complete.
-- Previously any booking_requests row with status 'accepted' blocked forever, even though accept_booking_request
-- leaves status as 'accepted' after the job completes.

CREATE OR REPLACE FUNCTION public.create_booking_request(
  p_provider_ref text,
  p_service_id integer,
  p_proposed_price double precision,
  p_location_text text DEFAULT NULL,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_message text DEFAULT NULL
)
RETURNS TABLE (request_id bigint, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_client int;
  v_provider int;
  v_new_id bigint;
  v_conn_sub int;
  v_conn_err text;
BEGIN
  PERFORM public.expire_stale_booking_requests();

  v_client := current_user_pk();
  IF v_client IS NULL THEN
    RETURN QUERY SELECT NULL::bigint, 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT u.id INTO v_provider
  FROM public.users u
  WHERE u.service_provider = true
    AND COALESCE(u.available_for_booking, true) = true
    AND (u.user_id = p_provider_ref OR u.id::text = p_provider_ref)
  LIMIT 1;

  IF v_provider IS NULL THEN
    RETURN QUERY SELECT NULL::bigint, 'provider_not_found_or_unavailable'::text;
    RETURN;
  END IF;

  IF v_provider = v_client THEN
    RETURN QUERY SELECT NULL::bigint, 'cannot_book_self'::text;
    RETURN;
  END IF;

  IF public.provider_has_incomplete_job(v_provider) THEN
    RETURN QUERY SELECT NULL::bigint, 'provider_busy'::text;
    RETURN;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.subscriptions s
    WHERE s.user_id = v_provider AND s.service_id = p_service_id
  ) THEN
    RETURN QUERY SELECT NULL::bigint, 'provider_not_subscribed_to_service'::text;
    RETURN;
  END IF;

  -- Pending request with this provider, OR accepted request whose linked job is still open.
  IF EXISTS (
    SELECT 1
    FROM public.booking_requests br
    LEFT JOIN public.jobs j ON j.id = br.job_id
    WHERE br.client_id = v_client
      AND br.provider_id = v_provider
      AND (
        br.status = 'pending'
        OR (
          br.status = 'accepted'
          AND br.job_id IS NOT NULL
          AND COALESCE(j.complete, false) = false
        )
      )
  ) THEN
    RETURN QUERY SELECT NULL::bigint, 'duplicate_booking_same_provider'::text;
    RETURN;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.jobs j
    JOIN public.quotes q ON q.id = j.quote_id
    WHERE q.client_id = v_client
      AND q.provider_id = v_provider
      AND COALESCE(j.complete, false) = false
  ) THEN
    RETURN QUERY SELECT NULL::bigint, 'duplicate_booking_same_provider'::text;
    RETURN;
  END IF;

  SELECT c.o_sub_id, c.o_err INTO v_conn_sub, v_conn_err
  FROM public.consume_client_subscription_connect(v_client) AS c;

  IF v_conn_err IS NOT NULL THEN
    RETURN QUERY SELECT NULL::bigint, v_conn_err;
    RETURN;
  END IF;

  INSERT INTO public.booking_requests (
    client_id, provider_id, service_id, proposed_price,
    location_text, latitude, longitude, message,
    status, expires_at,
    connect_consumed, connect_subscription_id
  )
  VALUES (
    v_client, v_provider, p_service_id, p_proposed_price,
    NULLIF(trim(p_location_text), ''), p_lat, p_lng, NULLIF(trim(p_message), ''),
    'pending', now() + interval '30 minutes',
    (v_conn_sub IS NOT NULL), v_conn_sub
  )
  RETURNING id INTO v_new_id;

  RETURN QUERY SELECT v_new_id, NULL::text;
END;
$$;

COMMENT ON FUNCTION public.create_booking_request(text, integer, double precision, text, double precision, double precision, text) IS
  'Creates a booking request; blocks duplicate only for pending requests or accepted requests with an incomplete job (rebook allowed after complete).';
