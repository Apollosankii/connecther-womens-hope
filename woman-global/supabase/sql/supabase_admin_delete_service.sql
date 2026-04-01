-- Admin Portal: delete a service and all rows that reference it (FK-safe order).
-- Run in Supabase SQL Editor after core tables exist (quotes, jobs, chats, messages,
-- booking_requests, subscriptions, services) and supabase_rls_admin.sql (is_admin).
--
-- PostgREST: POST /rest/v1/rpc/admin_delete_service  body: {"p_service_id": <int>}

CREATE OR REPLACE FUNCTION public.admin_delete_service(p_service_id integer)
RETURNS TABLE(ok boolean, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_admin() THEN
    RETURN QUERY SELECT false, 'not_admin'::text;
    RETURN;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.services WHERE id = p_service_id) THEN
    RETURN QUERY SELECT false, 'not_found'::text;
    RETURN;
  END IF;

  DELETE FROM public.job_score_card jsc
  USING public.jobs j
  WHERE jsc.job_id = j.id
    AND j.quote_id IN (SELECT q.id FROM public.quotes q WHERE q.service_id = p_service_id);

  DELETE FROM public.messages m
  USING public.chats c
  WHERE m.chat_id = c.id
    AND c.quote_id IN (SELECT q.id FROM public.quotes q WHERE q.service_id = p_service_id);

  DELETE FROM public.jobs j
  WHERE j.quote_id IN (SELECT q.id FROM public.quotes q WHERE q.service_id = p_service_id);

  DELETE FROM public.chats c
  WHERE c.quote_id IN (SELECT q.id FROM public.quotes q WHERE q.service_id = p_service_id);

  DELETE FROM public.quotes WHERE service_id = p_service_id;

  DELETE FROM public.booking_requests WHERE service_id = p_service_id;

  DELETE FROM public.subscriptions WHERE service_id = p_service_id;

  DELETE FROM public.services WHERE id = p_service_id;

  RETURN QUERY SELECT true, NULL::text;
  RETURN;

EXCEPTION
  WHEN OTHERS THEN
    RETURN QUERY SELECT false, SQLERRM;
END;
$$;

COMMENT ON FUNCTION public.admin_delete_service(integer) IS
  'Admin JWT only: removes service_id and dependent quotes/jobs/chats/messages/booking rows/subscriptions.';

GRANT EXECUTE ON FUNCTION public.admin_delete_service(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_delete_service(integer) TO service_role;
