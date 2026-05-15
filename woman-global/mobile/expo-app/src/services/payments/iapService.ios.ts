import Purchases, {
  type CustomerInfo,
  type PurchasesOfferings,
  type PurchasesPackage,
  LOG_LEVEL,
} from 'react-native-purchases';

import { AppConfig } from '@/services/config';
import { syncIapPurchase } from '@/services/api/iapSync';
import type { IapCustomerInfo, PurchaseResult } from '@/services/payments/types';
import { getMyActiveSubscription } from '@/services/api/subscriptions';
import { getUserId } from '@/services/supabase/tokenStore';

let configured = false;

function revenueCatApiKey(): string {
  return AppConfig.revenueCatIosApiKey();
}

export function isIapAvailable(): boolean {
  return Boolean(revenueCatApiKey());
}

export async function configureIapIfNeeded(): Promise<void> {
  if (configured) return;
  const apiKey = revenueCatApiKey();
  if (!apiKey) {
    console.warn('[IAP] EXPO_PUBLIC_REVENUECAT_IOS_API_KEY not set');
    return;
  }

  if (__DEV__) {
    Purchases.setLogLevel(LOG_LEVEL.DEBUG);
  }

  const appUserId = (await getUserId())?.trim();
  await Purchases.configure({
    apiKey,
    appUserID: appUserId || undefined,
  });
  configured = true;

  if (appUserId) {
    try {
      await Purchases.logIn(appUserId);
    } catch (e) {
      console.warn('[IAP] logIn failed', e);
    }
  }
}

export async function loginIapUser(supabaseUserId: string): Promise<void> {
  await configureIapIfNeeded();
  if (!configured) return;
  await Purchases.logIn(supabaseUserId);
}

export async function fetchOfferings(): Promise<PurchasesOfferings | null> {
  await configureIapIfNeeded();
  if (!configured) return null;
  return Purchases.getOfferings();
}

export async function getCustomerInfo(): Promise<IapCustomerInfo | null> {
  await configureIapIfNeeded();
  if (!configured) return null;
  const info: CustomerInfo = await Purchases.getCustomerInfo();
  return info;
}

function findPackageForPlan(
  offerings: PurchasesOfferings | null,
  packageId: string,
  appleProductId?: string | null,
): PurchasesPackage | null {
  const all = offerings?.current?.availablePackages ?? [];
  if (all.length === 0) return null;

  if (appleProductId) {
    const byProduct = all.find((p) => p.product.identifier === appleProductId);
    if (byProduct) return byProduct;
  }

  const planNum = parseInt(packageId, 10);
  const byPlanAttr = all.find((p) => {
    const id = (p as { identifier?: string }).identifier ?? '';
    return id === `plan_${planNum}` || id === packageId;
  });
  if (byPlanAttr) return byPlanAttr;

  const index = Math.max(0, planNum - 1);
  return all[index] ?? all[0] ?? null;
}

function mapIapError(e: unknown): string {
  if (e && typeof e === 'object' && 'userCancelled' in e && (e as { userCancelled?: boolean }).userCancelled) {
    return 'Purchase cancelled.';
  }
  if (e instanceof Error) return e.message;
  return 'Purchase failed.';
}

export async function purchaseIapPackage(
  packageId: string,
  appleProductId?: string | null,
): Promise<PurchaseResult> {
  const planId = parseInt(packageId, 10);
  if (!Number.isFinite(planId) || planId < 1) {
    return { success: false, platform: 'ios', error: 'Invalid plan.' };
  }

  try {
    await configureIapIfNeeded();
    if (!configured) {
      return {
        success: false,
        platform: 'ios',
        error: 'Subscriptions are not configured. Set EXPO_PUBLIC_REVENUECAT_IOS_API_KEY and rebuild.',
      };
    }

    const offerings = await Purchases.getOfferings();
    const pkg = findPackageForPlan(offerings, packageId, appleProductId);
    if (!pkg) {
      return {
        success: false,
        platform: 'ios',
        error: 'This plan is not available in the App Store yet. Check RevenueCat offerings.',
      };
    }

    const { customerInfo, productIdentifier } = await Purchases.purchasePackage(pkg);
    const transactionId = productIdentifier ?? pkg.product.identifier;

    try {
      await syncIapPurchase({ planId, transactionId: String(transactionId) });
    } catch (syncErr) {
      console.warn('[IAP] iap-sync failed (webhook may still activate)', syncErr);
    }

    const active = await getMyActiveSubscription();
    const granted = active?.connectsGranted ?? undefined;
    const used = active?.connectsUsed ?? 0;

    return {
      success: true,
      platform: 'ios',
      transactionId: String(transactionId),
      planId,
      creditsAwarded: granted != null ? Math.max(0, granted - used) : undefined,
    };
  } catch (e) {
    return {
      success: false,
      platform: 'ios',
      error: mapIapError(e),
    };
  }
}

export async function restoreIapPurchases(): Promise<PurchaseResult> {
  try {
    await configureIapIfNeeded();
    if (!configured) {
      return { success: false, platform: 'ios', error: 'Subscriptions are not configured.' };
    }

    const customerInfo = await Purchases.restorePurchases();
    const hasPremium =
      Object.keys(customerInfo.entitlements.active).length > 0 ||
      customerInfo.activeSubscriptions.length > 0;

    if (!hasPremium) {
      return { success: false, platform: 'ios', error: 'No active subscriptions found to restore.' };
    }

    const active = await getMyActiveSubscription();
    return {
      success: true,
      platform: 'ios',
      planId: active?.planId,
      creditsAwarded: active?.connectsGranted ?? undefined,
    };
  } catch (e) {
    return {
      success: false,
      platform: 'ios',
      error: mapIapError(e),
    };
  }
}

export function iosPriceMapFromOfferings(offerings: PurchasesOfferings | null): Record<string, string> {
  const map: Record<string, string> = {};
  const packages = offerings?.current?.availablePackages ?? [];
  for (const p of packages) {
    map[p.product.identifier] = p.product.priceString;
  }
  return map;
}
