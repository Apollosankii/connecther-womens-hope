import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import { AppConfig } from '@/services/config';
import { bumpProfilePicVersion, getFirebaseUid } from '@/services/supabase/tokenStore';
import type { UserProfile } from '@/types/models';
import { EncodingType, readAsStringAsync } from 'expo-file-system/legacy';

const PROFILE_PHOTOS_BUCKET = 'profpics';

function base64ToUint8Array(base64: string): Uint8Array {
  // atob is available in RN/JSC/Hermes environments; this avoids adding a Buffer polyfill.
  const binary = globalThis.atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

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

export async function updateMyProviderProfile(params: {
  headline: string;
  experience: string;
  workingHours: string;
  availableForBooking: boolean;
}): Promise<boolean> {
  const current = await getMyUserProfile();
  if (!current) return false;
  return updateMyProfile({
    firstName: current.first_name ?? null,
    lastName: current.last_name ?? null,
    phone: current.phone ?? null,
    email: current.email ?? null,
    title: params.headline,
    occupation: params.experience,
    workingHours: params.workingHours,
    availableForBooking: params.availableForBooking,
  });
}

export async function updateMyProfile(params: {
  firstName?: string | null;
  lastName?: string | null;
  phone?: string | null;
  email?: string | null;
  occupation?: string | null;
  title?: string | null;
  workingHours?: string | null;
  availableForBooking?: boolean | null;
}) {
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.rpc('update_my_profile', {
    p_first_name: params.firstName ?? null,
    p_last_name: params.lastName ?? null,
    p_phone: params.phone ?? null,
    p_email: params.email ?? null,
    p_occupation: params.occupation ?? null,
    p_title: params.title ?? null,
    p_working_hours: params.workingHours ?? null,
    p_available_for_booking: typeof params.availableForBooking === 'boolean' ? params.availableForBooking : null,
  });
  return !error;
}

export async function getMyUserId(): Promise<string | null> {
  const ok = await ensureSupabaseSession();
  if (!ok) return null;
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('get_my_user_id');
  if (error) return null;
  const row = ((data ?? []) as Array<{ uid?: string | null }>)[0];
  return row?.uid?.trim() || null;
}

export async function uploadProfilePic(params: { fileUri: string; fileName: string }): Promise<string> {
  const ok = await ensureSupabaseSession();
  if (!ok) throw new Error('Sign in is required to upload a profile photo.');

  const supabase = getSupabaseAuthedClient();
  const userId = await getMyUserId();
  if (!userId) throw new Error('Could not resolve your user id for storage.');

  const safeName = (params.fileName || 'profile.jpg').trim().replace(/[^a-zA-Z0-9._-]/g, '_') || 'profile.jpg';
  const path = `profiles/${userId}/${safeName}`;

  // On Android, `fetch(file://...)` can throw "Network request failed". Prefer FileSystem for local reads.
  let bytes: Uint8Array | ArrayBuffer;
  try {
    const resp = await fetch(params.fileUri);
    if (!resp.ok) throw new Error(`Could not read image file (HTTP ${resp.status}).`);
    const blob = await resp.blob();
    bytes = await blob.arrayBuffer();
  } catch {
    const b64 = await readAsStringAsync(params.fileUri, { encoding: EncodingType.Base64 });
    bytes = base64ToUint8Array(b64);
  }

  const up = await supabase.storage.from(PROFILE_PHOTOS_BUCKET).upload(path, bytes, { upsert: true });
  if (up.error) throw new Error(up.error.message);

  const base = AppConfig.supabaseUrl().replace(/\/+$/, '');
  const publicUrl = `${base}/storage/v1/object/public/${PROFILE_PHOTOS_BUCKET}/${path}`;

  const { error: rpcError } = await supabase.rpc('update_my_prof_pic', { p_url: publicUrl });
  if (rpcError) throw new Error(rpcError.message);

  await bumpProfilePicVersion();
  return publicUrl;
}

