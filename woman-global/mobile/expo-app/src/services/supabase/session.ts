import { exchangeFirebaseIdToken } from '@/services/authBridge';
import { getFirebaseAuth } from '@/services/firebaseAuth';
import { getSupabaseJwt, setSupabaseJwt, setUserSessionMeta } from '@/services/supabase/tokenStore';
import { isJwtExpiringSoon } from '@/utils/jwt';

const EXPIRY_BUFFER_SECONDS = 45;

/**
 * Ensures there is a valid bridge JWT in SecureStore.
 * If missing/expiring, refreshes by fetching a new Firebase ID token and exchanging via auth-bridge.
 */
export async function ensureSupabaseSession(): Promise<boolean> {
  const jwt = await getSupabaseJwt();
  if (jwt && !isJwtExpiringSoon(jwt, EXPIRY_BUFFER_SECONDS)) return true;
  return tryRefreshSupabaseJwt();
}

export async function tryRefreshSupabaseJwt(): Promise<boolean> {
  const auth = getFirebaseAuth();
  const user = auth.currentUser;
  if (!user) return false;

  const idToken = await user.getIdToken(true);
  const bridge = await exchangeFirebaseIdToken(idToken);

  await setSupabaseJwt(bridge.supabaseJwt);
  await setUserSessionMeta({ userId: bridge.userId, firebaseUid: bridge.firebaseUid });
  return true;
}

export async function clearSupabaseSession() {
  await setSupabaseJwt(null);
}

