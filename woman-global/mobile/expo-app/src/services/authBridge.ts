import { AppConfig } from '@/services/config';

export type AuthBridgeResult = {
  supabaseJwt: string;
  userId: string;
  firebaseUid: string;
};

type RawAuthBridgeResult = {
  supabase_jwt?: string;
  user_id?: string;
  firebase_uid?: string;
};

export class AuthBridgeError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'AuthBridgeError';
  }
}

export async function exchangeFirebaseIdToken(firebaseIdToken: string): Promise<AuthBridgeResult> {
  const supabaseUrl = AppConfig.supabaseUrl();
  if (!supabaseUrl) throw new AuthBridgeError('Missing Supabase URL configuration.');
  const url = `${supabaseUrl.replace(/\/+$/, '')}/functions/v1/auth-bridge`;

  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${firebaseIdToken.trim()}`,
    },
    body: JSON.stringify({}),
  });

  const text = await resp.text();
  if (!resp.ok) {
    let msg = text;
    try {
      const obj = JSON.parse(text) as { detail?: string; error?: string };
      msg = [obj.error, obj.detail].filter(Boolean).join(' - ') || text;
    } catch {
      // ignore JSON parse failures
    }
    throw new AuthBridgeError(`auth-bridge failed (${resp.status}): ${msg}`);
  }

  const obj = JSON.parse(text) as RawAuthBridgeResult;
  const jwt = (obj.supabase_jwt ?? '').trim();
  const userId = (obj.user_id ?? '').trim();
  const firebaseUid = (obj.firebase_uid ?? '').trim();

  if (!jwt || !userId || !firebaseUid) throw new AuthBridgeError('auth-bridge missing fields');
  return { supabaseJwt: jwt, userId, firebaseUid };
}

