import { AppConfig } from '@/services/config';

const ACTIONS = ['start', 'check'] as const;

function phoneVerifyUrl(): string {
  const base = AppConfig.supabaseUrl().replace(/\/+$/, '');
  if (!base) throw new Error('EXPO_PUBLIC_SUPABASE_URL is not set.');
  return `${base}/functions/v1/phone-verify`;
}

async function postPhoneVerify(
  firebaseIdToken: string,
  body: Record<string, unknown>,
): Promise<{ ok: boolean; verified?: boolean; error?: string; status: number }> {
  const anon = AppConfig.supabaseAnonKey().trim();
  if (!anon) throw new Error('EXPO_PUBLIC_SUPABASE_ANON_KEY is not set.');

  const res = await fetch(phoneVerifyUrl(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${anon}`,
      apikey: anon,
      'X-Firebase-Id-Token': firebaseIdToken,
    },
    body: JSON.stringify(body),
  });

  let parsed: { ok?: boolean; verified?: boolean; error?: string; code?: string } = {};
  try {
    parsed = (await res.json()) as typeof parsed;
  } catch {
    /* ignore */
  }

  if (!res.ok) {
    const msg =
      typeof parsed.error === 'string'
        ? parsed.error
        : typeof parsed.code === 'string'
          ? parsed.code
          : `HTTP ${res.status}`;
    return { ok: false, error: msg, status: res.status };
  }

  return {
    ok: parsed.ok === true,
    verified: parsed.verified === true,
    error: typeof parsed.error === 'string' ? parsed.error : undefined,
    status: res.status,
  };
}

export async function phoneVerifyStart(firebaseIdToken: string, phoneE164: string) {
  return postPhoneVerify(firebaseIdToken, { action: ACTIONS[0], phone: phoneE164 });
}

export async function phoneVerifyCheck(firebaseIdToken: string, phoneE164: string, code: string) {
  return postPhoneVerify(firebaseIdToken, { action: ACTIONS[1], phone: phoneE164, code: code.trim() });
}
