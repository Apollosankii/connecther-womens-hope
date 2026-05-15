import { AppConfig } from '@/services/config';
import { getSupabaseJwt } from '@/services/supabase/tokenStore';

export type IapSyncResult = {
  ok: boolean;
  activated?: boolean;
  detail?: string;
  plan_id?: number;
  connects_granted?: number | null;
  transaction_id?: string;
};

function iapSyncUrl(): string {
  const base = AppConfig.supabaseUrl().replace(/\/+$/, '');
  if (!base) throw new Error('Missing Supabase URL configuration.');
  return `${base}/functions/v1/iap-sync`;
}

/** Client sync after RevenueCat purchase (webhook may lag). */
export async function syncIapPurchase(params: {
  planId: number;
  transactionId?: string;
}): Promise<IapSyncResult> {
  const jwt = (await getSupabaseJwt())?.trim();
  if (!jwt) throw new Error('Please sign in again to continue.');
  const anon = AppConfig.supabaseAnonKey().trim();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
  };
  if (anon) headers.apikey = anon;

  const resp = await fetch(iapSyncUrl(), {
    method: 'POST',
    headers,
    body: JSON.stringify({
      plan_id: params.planId,
      transaction_id: params.transactionId,
    }),
  });

  const text = await resp.text();
  let json: IapSyncResult = { ok: false };
  try {
    json = JSON.parse(text) as IapSyncResult;
  } catch {
    throw new Error(text || `iap-sync failed (${resp.status})`);
  }

  if (!resp.ok) {
    throw new Error(json.detail ?? text ?? `iap-sync failed (${resp.status})`);
  }

  return json;
}
