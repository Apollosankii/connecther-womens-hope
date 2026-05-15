export type PaymentPlatform = 'ios' | 'android';

export type PurchaseResult = {
  success: boolean;
  transactionId?: string;
  platform: PaymentPlatform;
  creditsAwarded?: number;
  planId?: number;
  error?: string;
};

export type ConnectPackage = {
  id: string;
  planId: number;
  name: string;
  description?: string | null;
  priceLabel: string;
  isPopular?: boolean;
  features?: string[] | null;
  appleProductId?: string | null;
};

/** Subset of RevenueCat CustomerInfo used in hooks (avoids importing purchases on Android/web). */
export type IapCustomerInfo = {
  entitlements: { active: Record<string, unknown> };
  activeSubscriptions: string[];
};

export type PaystackPurchaseContext = {
  billingEmail: string;
  onNavigateToCheckout: (params: {
    planId: number;
    planName: string;
    priceLabel: string;
    authorizationUrl: string;
    reference: string;
    email: string;
  }) => void;
};
