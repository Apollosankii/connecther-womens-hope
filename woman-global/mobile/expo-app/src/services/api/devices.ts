import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';

export type UpsertDeviceResult = { ok: true } | { ok: false; error: string };

export async function upsertMyDevice(regToken: string, deviceId: string): Promise<UpsertDeviceResult> {
  const token = regToken.trim();
  if (!token) return { ok: false, error: 'empty token' };
  const ok = await ensureSupabaseSession();
  if (!ok) return { ok: false, error: 'no session' };
  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.rpc('upsert_my_device', {
    p_reg_token: token,
    p_device: (deviceId || 'default').trim(),
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true };
}
