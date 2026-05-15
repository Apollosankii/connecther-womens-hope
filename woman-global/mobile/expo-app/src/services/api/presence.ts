import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';

export async function upsertLiveLocation(latitude: number, longitude: number): Promise<boolean> {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return false;
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.rpc('upsert_live_location', { p_lat: latitude, p_lon: longitude });
  return !error;
}

