-- Start or resume a client↔provider conversation (quote + chat).
-- Run in Supabase SQL Editor after supabase_rls_android.sql / supabase_rls_android_ext.sql.
--
-- Prerequisites (typical schema — adjust column names if yours differ):
--   quotes: id, client_id, provider_id, service_id, quote_code, converted
--   chats: id, quote_id, chat_code
--
-- After applying: enable Realtime for table `messages` (Dashboard → Database → Publications)
-- so clients can use live subscriptions; polling in the app still works without it.

CREATE OR REPLACE FUNCTION public.start_conversation_with_provider(
  p_provider_ref text,
  p_service_id integer
)
RETURNS TABLE(quote_id text, chat_code text, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_client int;
  v_provider int;
  v_quote_id int;
  v_chat_code text;
BEGIN
  v_client := current_user_pk();
  IF v_client IS NULL THEN
    RETURN QUERY SELECT NULL::text, NULL::text, 'not_authenticated'::text;
    RETURN;
  END IF;

  SELECT u.id INTO v_provider
  FROM users u
  WHERE u.service_provider = true
    AND (u.user_id = p_provider_ref OR u.id::text = p_provider_ref)
  LIMIT 1;

  IF v_provider IS NULL THEN
    RETURN QUERY SELECT NULL::text, NULL::text, 'provider_not_found'::text;
    RETURN;
  END IF;

  IF v_provider = v_client THEN
    RETURN QUERY SELECT NULL::text, NULL::text, 'cannot_chat_with_self'::text;
    RETURN;
  END IF;

  -- Reuse open quote + chat for same client, provider, service
  SELECT q.id, c.chat_code INTO v_quote_id, v_chat_code
  FROM quotes q
  JOIN chats c ON c.quote_id = q.id
  WHERE q.client_id = v_client
    AND q.provider_id = v_provider
    AND q.service_id = p_service_id
    AND q.converted = false
    AND c.chat_code IS NOT NULL
  LIMIT 1;

  IF v_quote_id IS NOT NULL AND v_chat_code IS NOT NULL THEN
    RETURN QUERY SELECT v_quote_id::text, v_chat_code, NULL::text;
    RETURN;
  END IF;

  INSERT INTO quotes (client_id, provider_id, service_id, quote_code, converted)
  VALUES (
    v_client,
    v_provider,
    p_service_id,
    'Q' || substr(md5(random()::text || clock_timestamp()::text), 1, 12),
    false
  )
  RETURNING id INTO v_quote_id;

  v_chat_code := 'CH' || substr(md5(random()::text || clock_timestamp()::text), 1, 14);

  INSERT INTO chats (quote_id, chat_code) VALUES (v_quote_id, v_chat_code);

  RETURN QUERY SELECT v_quote_id::text, v_chat_code, NULL::text;
END;
$$;

GRANT EXECUTE ON FUNCTION public.start_conversation_with_provider(text, integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.start_conversation_with_provider(text, integer) TO service_role;
