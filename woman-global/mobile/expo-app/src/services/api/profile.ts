import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import { getFirebaseUid } from '@/services/supabase/tokenStore';
import type { UserProfile } from '@/types/models';

export async function getMyUserProfile(): Promise<UserProfile | null> {
  const ok = await ensureSupabaseSession();
  if (!ok) return null;
  const firebaseUid = await getFirebaseUid();
  if (!firebaseUid) return null;

  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase
    .from('users')
    .select('*')
    .eq('clerk_user_id', firebaseUid)
    .limit(1);
  if (error) throw new Error(error.message);
  return ((data ?? [])[0] ?? null) as UserProfile | null;
}

