import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import type { ChatMessage, Conversation } from '@/types/models';

export async function listConversations(): Promise<Conversation[]> {
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('get_conversations');
  if (error) throw new Error(error.message);
  return (data ?? []) as Conversation[];
}

export async function listChatMessages(chatCode: string): Promise<ChatMessage[]> {
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('get_chat_messages', { p_chat_code: chatCode });
  if (error) throw new Error(error.message);
  return (data ?? []) as ChatMessage[];
}

export async function sendChatMessage(chatCode: string, content: string): Promise<void> {
  const ok = await ensureSupabaseSession();
  if (!ok) throw new Error('Auth required');
  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.rpc('send_chat_message', { p_chat_code: chatCode, p_content: content });
  if (error) throw new Error(error.message);
}

