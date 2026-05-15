import { AppConfig } from '@/services/config';
import { getFirebaseAuth } from '@/services/firebaseAuth';

export type PanicSmsResult =
  | { ok: true; sentCount: number }
  | { ok: false; code: string; message: string };

/**
 * GBV panic SMS via Supabase Edge `panic-sms` (Africa's Talking for subscribers).
 * Same gateway pattern as Android `PanicSmsClient`: anon + apikey, Firebase token in `X-Firebase-Id-Token`.
 */
export async function sendPanicSmsViaEdge(params: {
  recipientsE164: string[];
  latitude?: number | null;
  longitude?: number | null;
}): Promise<PanicSmsResult> {
  const base = AppConfig.supabaseUrl().replace(/\/+$/, '');
  const anon = AppConfig.supabaseAnonKey();
  if (!base || !anon) {
    return { ok: false, code: 'CONFIG', message: 'Missing Supabase URL or anon key.' };
  }

  const user = getFirebaseAuth().currentUser;
  if (!user) {
    return { ok: false, code: 'NO_FIREBASE_USER', message: 'Sign in required for ConnectHer SMS.' };
  }

  const idToken = await user.getIdToken(true);
  const body: Record<string, unknown> = { recipients: params.recipientsE164 };
  if (typeof params.latitude === 'number' && typeof params.longitude === 'number') {
    body.latitude = params.latitude;
    body.longitude = params.longitude;
  }

  const url = `${base}/functions/v1/panic-sms`;
  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${anon}`,
      apikey: anon,
      'X-Firebase-Id-Token': idToken,
    },
    body: JSON.stringify(body),
  });

  const text = await resp.text();
  let json: { ok?: boolean; sent_count?: number; code?: string; error?: string; detail?: string } = {};
  try {
    json = JSON.parse(text) as typeof json;
  } catch {
    /* ignore */
  }

  const message = [json.error, json.detail].filter(Boolean).join(' — ') || text || `HTTP ${resp.status}`;

  if (!resp.ok) {
    return { ok: false, code: json.code || `HTTP_${resp.status}`, message };
  }
  if (!json.ok) {
    return { ok: false, code: json.code || 'UNKNOWN', message };
  }

  return { ok: true, sentCount: typeof json.sent_count === 'number' ? json.sent_count : params.recipientsE164.length };
}
