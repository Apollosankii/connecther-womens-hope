import { exchangeFirebaseIdToken } from '@/services/authBridge';
import { setSupabaseJwt, setUserSessionMeta } from '@/services/supabase/tokenStore';

export async function runAuthBridge(firebaseIdToken: string) {
  const bridge = await exchangeFirebaseIdToken(firebaseIdToken);
  await setSupabaseJwt(bridge.supabaseJwt);
  await setUserSessionMeta({ userId: bridge.userId, firebaseUid: bridge.firebaseUid });
  return bridge;
}

