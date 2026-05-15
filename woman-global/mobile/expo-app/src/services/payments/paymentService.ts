import { Platform } from 'react-native';

import { listActiveSubscriptionPlans } from '@/services/api/subscriptions';
import { plansToConnectPackages } from '@/services/payments/packageCatalog';
import { purchasePackageWithPaystack } from '@/services/payments/paystackService';
import type { PaystackPurchaseContext } from '@/services/payments/types';
import {
  configureIapIfNeeded,
  fetchOfferings,
  iosPriceMapFromOfferings,
  isIapAvailable,
  purchaseIapPackage,
  restoreIapPurchases,
} from '@/services/payments/iapService';
import type { ConnectPackage, PurchaseResult } from '@/services/payments/types';
import type { SubscriptionPlan } from '@/types/models';

export type PurchaseConnectsOptions = {
  billingEmail?: string;
  planName?: string;
  priceLabel?: string;
  appleProductId?: string | null;
  paystack?: PaystackPurchaseContext;
};

export async function getAvailablePackages(): Promise<ConnectPackage[]> {
  const plans = await listActiveSubscriptionPlans();
  if (Platform.OS === 'ios' && isIapAvailable()) {
    await configureIapIfNeeded();
    const offerings = await fetchOfferings();
    const priceMap = iosPriceMapFromOfferings(offerings);
    return plansToConnectPackages(plans, priceMap);
  }
  return plansToConnectPackages(plans);
}

export async function purchaseConnects(
  packageId: string,
  options: PurchaseConnectsOptions = {},
): Promise<PurchaseResult> {
  if (Platform.OS === 'ios') {
    return purchaseIapPackage(packageId, options.appleProductId);
  }

  if (!options.paystack?.billingEmail || !options.paystack.onNavigateToCheckout) {
    return {
      success: false,
      platform: 'android',
      error: 'Billing email and checkout navigation are required for Android payments.',
    };
  }

  return purchasePackageWithPaystack(packageId, {
    billingEmail: options.paystack.billingEmail,
    onNavigateToCheckout: options.paystack.onNavigateToCheckout,
    planName: options.planName ?? 'Plan',
    priceLabel: options.priceLabel ?? '',
  });
}

export async function restorePurchases(): Promise<PurchaseResult> {
  if (Platform.OS !== 'ios') {
    return {
      success: false,
      platform: Platform.OS === 'android' ? 'android' : 'ios',
      error: 'Restore purchases is only supported on iOS.',
    };
  }
  return restoreIapPurchases();
}

export function subscriptionPlansToPackages(
  plans: SubscriptionPlan[],
  iosPriceByProductId?: Record<string, string>,
): ConnectPackage[] {
  return plansToConnectPackages(plans, iosPriceByProductId);
}
