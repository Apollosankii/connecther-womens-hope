import { Platform } from 'react-native';

import { AppConfig } from '@/services/config';
import type { PaystackPurchaseContext, PurchaseResult } from '@/services/payments/types';
import { getMyActiveSubscription, hasActiveSubscriptionForPayment } from '@/services/api/subscriptions';
import { getSupabaseJwt } from '@/services/supabase/tokenStore';

export type PaystackInitializedCharge = {
  accessCode: string;
  reference: string;
  authorizationUrl: string;
};

function paystackCheckoutUrl(): string {
  const base = AppConfig.supabaseUrl().replace(/\/+$/, '');
  if (!base) throw new Error('Missing Supabase URL configuration.');
  return `${base}/functions/v1/paystack-checkout`;
}

async function authedHeaders(): Promise<Record<string, string>> {
  const jwt = (await getSupabaseJwt())?.trim();
  if (!jwt) throw new Error('Please sign in again to continue.');
  const anon = AppConfig.supabaseAnonKey().trim();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
  };
  if (anon) headers.apikey = anon;
  return headers;
}

function extractAccessCodeFromUrl(url: string): string {
  try {
    const path = url.split('?')[0] ?? url;
    return path.split('/').filter(Boolean).pop()?.trim() ?? '';
  } catch {
    return url.split('/').filter(Boolean).pop()?.trim() ?? '';
  }
}

export async function initializePaystackCheckout(planId: number, email: string): Promise<PaystackInitializedCharge> {
  const em = email.trim();
  if (planId < 1 || !em) throw new Error('Plan and billing email are required.');

  const resp = await fetch(paystackCheckoutUrl(), {
    method: 'POST',
    headers: await authedHeaders(),
    body: JSON.stringify({ plan_id: planId, email: em }),
  });

  const text = await resp.text();
  if (!resp.ok) {
    throw new Error(`paystack-checkout init failed (${resp.status}): ${text}`);
  }

  const obj = JSON.parse(text) as {
    access_code?: string;
    reference?: string;
    authorization_url?: string;
  };

  let accessCode = (obj.access_code ?? '').trim();
  const reference = (obj.reference ?? '').trim();
  const authorizationUrl = (obj.authorization_url ?? '').trim();

  if (!accessCode && authorizationUrl) {
    accessCode = extractAccessCodeFromUrl(authorizationUrl);
  }
  if (!accessCode && !authorizationUrl) {
    throw new Error('paystack-checkout returned neither access_code nor authorization_url.');
  }
  if (!reference) {
    throw new Error('paystack-checkout missing payment reference.');
  }

  return { accessCode, reference, authorizationUrl };
}

export async function verifyPaystackTransaction(reference: string): Promise<boolean> {
  const ref = reference.trim();
  if (!ref) return false;

  const resp = await fetch(paystackCheckoutUrl(), {
    method: 'POST',
    headers: await authedHeaders(),
    body: JSON.stringify({ action: 'verify', reference: ref }),
  });

  const text = await resp.text();
  if (!resp.ok) return false;

  try {
    const obj = JSON.parse(text) as { ok?: boolean; activated?: boolean };
    return obj.ok === true && obj.activated === true;
  } catch {
    return false;
  }
}

export async function waitForSubscriptionActive(
  planId: number,
  paymentReference: string,
  maxAttempts = 24,
  intervalMs = 750,
): Promise<boolean> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    if (await hasActiveSubscriptionForPayment(planId, paymentReference)) return true;
    if (attempt < maxAttempts - 1) {
      await new Promise((r) => setTimeout(r, intervalMs));
    }
  }
  return false;
}

/**
 * Android Paystack: initialize checkout and navigate to WebView screen.
 * Completion is handled on PaystackCheckoutScreen (verify + poll).
 */
export async function purchasePackageWithPaystack(
  packageId: string,
  ctx: PaystackPurchaseContext & { planName: string; priceLabel: string },
): Promise<PurchaseResult> {
  if (Platform.OS === 'ios') {
    return {
      success: false,
      platform: 'android',
      error: 'Paystack is not available on iOS. Use App Store subscriptions.',
    };
  }

  const planId = parseInt(packageId, 10);
  if (!Number.isFinite(planId) || planId < 1) {
    return { success: false, platform: 'android', error: 'Invalid plan.' };
  }

  try {
    const init = await initializePaystackCheckout(planId, ctx.billingEmail);
    const url = init.authorizationUrl?.trim();
    if (!url) {
      return { success: false, platform: 'android', error: 'Could not start checkout.' };
    }
    ctx.onNavigateToCheckout({
      planId,
      planName: ctx.planName,
      priceLabel: ctx.priceLabel,
      authorizationUrl: url,
      reference: init.reference,
      email: ctx.billingEmail,
    });
    return {
      success: true,
      platform: 'android',
      transactionId: init.reference,
      planId,
    };
  } catch (e) {
    return {
      success: false,
      platform: 'android',
      error: e instanceof Error ? e.message : 'Paystack checkout failed.',
    };
  }
}

/** After Paystack verify succeeds — build PurchaseResult with connects from active sub. */
export async function buildPaystackSuccessResult(
  planId: number,
  reference: string,
): Promise<PurchaseResult> {
  const active = await getMyActiveSubscription();
  const granted = active?.connectsGranted ?? undefined;
  const used = active?.connectsUsed ?? 0;
  const creditsAwarded =
    granted != null ? Math.max(0, granted - used) : undefined;

  return {
    success: true,
    platform: 'android',
    transactionId: reference,
    planId,
    creditsAwarded,
  };
}
