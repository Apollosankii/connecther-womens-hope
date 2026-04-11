import * as SecureStore from 'expo-secure-store';

const Keys = {
  supabaseJwt: 'supabase_jwt',
  userId: 'user_id',
  firebaseUid: 'firebase_uid',
} as const;

export async function setSupabaseJwt(jwt: string | null) {
  if (!jwt) {
    await SecureStore.deleteItemAsync(Keys.supabaseJwt);
    return;
  }
  await SecureStore.setItemAsync(Keys.supabaseJwt, jwt);
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
}

export async function setUserSessionMeta(meta: { userId: string; firebaseUid: string }) {
  await Promise.all([
    SecureStore.setItemAsync(Keys.userId, meta.userId),
    SecureStore.setItemAsync(Keys.firebaseUid, meta.firebaseUid),
  ]);
}

export async function getUserId(): Promise<string | null> {
  return SecureStore.getItemAsync(Keys.userId);
}

export async function getFirebaseUid(): Promise<string | null> {
  return SecureStore.getItemAsync(Keys.firebaseUid);
}

