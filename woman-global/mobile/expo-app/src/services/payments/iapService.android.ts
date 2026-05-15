import type { IapCustomerInfo, PurchaseResult } from '@/services/payments/types';

export function isIapAvailable(): boolean {
  return false;
}

export async function configureIapIfNeeded(): Promise<void> {}

export async function loginIapUser(_supabaseUserId: string): Promise<void> {}

export async function fetchOfferings(): Promise<null> {
  return null;
}

export async function getCustomerInfo(): Promise<IapCustomerInfo | null> {
  return null;
}

export async function purchaseIapPackage(_packageId: string, _appleProductId?: string | null): Promise<PurchaseResult> {
  return {
    success: false,
    platform: 'ios',
    error: 'In-app purchases are only available on iOS.',
  };
}

export async function restoreIapPurchases(): Promise<PurchaseResult> {
  return {
    success: false,
    platform: 'ios',
    error: 'Restore purchases is only available on iOS.',
  };
}

export function iosPriceMapFromOfferings(_offerings: null): Record<string, string> {
  return {};
}
