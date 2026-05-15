import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import type { ChatMessage, Conversation } from '@/types/models';

export async function listConversations(): Promise<Conversation[]> {
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('get_conversations');
  if (error) throw new Error(error.message);
  const rows = (data ?? []) as any[];
  return rows.map((r) => ({
    chat_code: (r.chat_code ?? r.chat_id ?? '').toString(),
    other_user_name: (r.peer_name ?? r.other_user_name ?? '').toString() || null,
    other_user_pic: (r.peer_pic ?? r.other_user_pic ?? null) as string | null,
    last_message: (r.msg_text ?? r.last_message ?? '').toString() || null,
    updated_at: (r.msg_time ?? r.updated_at ?? null) as string | null,
  })) as Conversation[];
}

async function resolveChatCode(supabase: ReturnType<typeof getSupabaseAuthedClient>, raw: string): Promise<string> {
  const trimmed = raw.trim();
  if (!trimmed) return '';
  const { data: asCode } = await supabase.from('chats').select('id').eq('chat_code', trimmed).limit(1);
  if (asCode?.length) return trimmed;
  const id = parseInt(trimmed, 10);
  if (!Number.isFinite(id)) return trimmed;
  const { data: asId } = await supabase.from('chats').select('chat_code').eq('id', id).limit(1);
  const code = asId?.[0]?.chat_code;
  return typeof code === 'string' && code.trim() ? code.trim() : trimmed;
}

export type ChatHeader = { peer_name: string | null; peer_pic: string | null; service_name: string | null };

export async function getChatHeader(chatCode: string): Promise<ChatHeader | null> {
  const ok = await ensureSupabaseSession();
  if (!ok) return null;
  const supabase = getSupabaseAuthedClient();
  const code = await resolveChatCode(supabase, chatCode);
  if (!code) return null;
  const { data, error } = await supabase.rpc('get_chat_header', { p_chat_code: code });
  if (error) return null;
  const row = ((data ?? []) as Array<{ peer_name?: string | null; peer_pic?: string | null; service_name?: string | null }>)[0];
  if (!row) return null;
  return {
    peer_name: row.peer_name ?? null,
    peer_pic: row.peer_pic ?? null,
    service_name: row.service_name ?? null,
  };
}

export async function listChatMessages(chatCode: string): Promise<ChatMessage[]> {
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const supabase = getSupabaseAuthedClient();
  const code = await resolveChatCode(supabase, chatCode);
  if (!code) return [];

  const { data: chatRows, error: chatErr } = await supabase.from('chats').select('id, chat_code').eq('chat_code', code).limit(1);
  if (chatErr) throw new Error(chatErr.message);
  const chatId = chatRows?.[0]?.id;
  if (chatId == null) return [];

  const { data: msgRows, error: msgErr } = await supabase
    .from('messages')
    .select('id, content, time, sender_id')
    .eq('chat_id', chatId)
    .order('time', { ascending: true });
  if (msgErr) throw new Error(msgErr.message);

  return (msgRows ?? []).map((m: { id: number; content?: string | null; time?: string | null; sender_id?: string | null }) => ({
    id: m.id,
    chat_code: code,
    content: m.content ?? '',
    created_at: m.time ?? null,
    sender_id: m.sender_id ?? null,
  }));
}

export async function getMyChatSenderId(): Promise<string | null> {
  const ok = await ensureSupabaseSession();
  if (!ok) return null;
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('get_my_user_id');
  if (error) return null;
  const row = ((data ?? []) as Array<{ uid?: string | null }>)[0];
  return row?.uid?.trim() || null;
}

export async function sendChatMessage(chatCode: string, content: string): Promise<void> {
  const ok = await ensureSupabaseSession();
  if (!ok) throw new Error('Auth required');
  const supabase = getSupabaseAuthedClient();
  const code = await resolveChatCode(supabase, chatCode);
  const trimmed = content.trim();
  if (!trimmed) return;

  const { data: chatRows, error: chatErr } = await supabase.from('chats').select('id').eq('chat_code', code).limit(1);
  if (chatErr) throw new Error(chatErr.message);
  const chatId = chatRows?.[0]?.id;
  if (chatId == null) throw new Error('Chat not found');

  const { data: uidRows, error: uidErr } = await supabase.rpc('get_my_user_id');
  if (uidErr) throw new Error(uidErr.message);
  const senderId = ((uidRows ?? []) as Array<{ uid?: string | null }>)[0]?.uid?.trim();
  if (!senderId) throw new Error('Could not resolve sender');

  const { error } = await supabase.from('messages').insert({
    chat_id: chatId,
    sender_id: senderId,
    content: trimmed,
  });
  if (error) throw new Error(error.message);
}
