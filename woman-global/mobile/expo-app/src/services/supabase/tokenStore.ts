import * as SecureStore from 'expo-secure-store';

const Keys = {
  supabaseJwt: 'supabase_jwt',
  userId: 'user_id',
  firebaseUid: 'firebase_uid',
  profilePicVersion: 'profile_pic_version',
} as const;

type SessionListener = () => void;
const sessionListeners = new Set<SessionListener>();

export function subscribeToSessionChanges(listener: SessionListener) {
  sessionListeners.add(listener);
  return () => {
    sessionListeners.delete(listener);
  };
}

function notifySessionChanged() {
  for (const l of sessionListeners) {
    try {
      l();
    } catch {
      // ignore listener failures
    }
  }
}

export async function setSupabaseJwt(jwt: string | null) {
  if (!jwt) {
    await SecureStore.deleteItemAsync(Keys.supabaseJwt);
    notifySessionChanged();
    return;
  }
  await SecureStore.setItemAsync(Keys.supabaseJwt, jwt);
  notifySessionChanged();
}

export async function getSupabaseJwt(): Promise<string | null> {
  return SecureStore.getItemAsync(Keys.supabaseJwt);
}

export async function clearSession() {
  await Promise.all([
    SecureStore.deleteItemAsync(Keys.supabaseJwt),
    SecureStore.deleteItemAsync(Keys.userId),
    SecureStore.deleteItemAsync(Keys.firebaseUid),
  ]);
  notifySessionChanged();
}

export async function setUserSessionMeta(meta: { userId: string; firebaseUid: string }) {
  await Promise.all([
    SecureStore.setItemAsync(Keys.userId, meta.userId),
    SecureStore.setItemAsync(Keys.firebaseUid, meta.firebaseUid),
  ]);
  notifySessionChanged();
}

export async function getUserId(): Promise<string | null> {
  return SecureStore.getItemAsync(Keys.userId);
}

export async function getFirebaseUid(): Promise<string | null> {
  return SecureStore.getItemAsync(Keys.firebaseUid);
}

export async function bumpProfilePicVersion(): Promise<number> {
  const v = Date.now();
  await SecureStore.setItemAsync(Keys.profilePicVersion, String(v));
  return v;
}

export async function getProfilePicVersion(): Promise<number> {
  const raw = await SecureStore.getItemAsync(Keys.profilePicVersion);
  const n = raw ? Number(raw) : 0;
  return Number.isFinite(n) ? n : 0;
}

