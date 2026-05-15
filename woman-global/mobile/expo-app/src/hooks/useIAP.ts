import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Platform } from 'react-native';

import { getMyActiveSubscription, listActiveSubscriptionPlans } from '@/services/api/subscriptions';
import {
  configureIapIfNeeded,
  fetchOfferings,
  getCustomerInfo,
  iosPriceMapFromOfferings,
  isIapAvailable,
  loginIapUser,
  purchaseIapPackage,
} from '@/services/payments/iapService';
import { plansToConnectPackages } from '@/services/payments/packageCatalog';
import type { ConnectPackage, PurchaseResult } from '@/services/payments/types';
import { restorePurchases } from '@/services/payments/paymentService';
import { getUserId } from '@/services/supabase/tokenStore';

export function useIAP() {
  const queryClient = useQueryClient();
  const [purchasingId, setPurchasingId] = useState<string | null>(null);
  const [restoring, setRestoring] = useState(false);
  const [lastError, setLastError] = useState<string | null>(null);

  const iapEnabled = Platform.OS === 'ios' && isIapAvailable();

  useEffect(() => {
    if (!iapEnabled) return;
    void (async () => {
      const uid = await getUserId();
      if (uid) await loginIapUser(uid);
    })();
  }, [iapEnabled]);

  const plansQ = useQuery({
    queryKey: ['subscriptions', 'plans'],
    queryFn: listActiveSubscriptionPlans,
  });

  const offeringsQ = useQuery({
    queryKey: ['iap', 'offerings'],
    queryFn: async () => {
      await configureIapIfNeeded();
      return fetchOfferings();
    },
    enabled: iapEnabled,
  });

  const activeQ = useQuery({
    queryKey: ['subscriptions', 'active'],
    queryFn: getMyActiveSubscription,
  });

  const customerQ = useQuery({
    queryKey: ['iap', 'customer'],
    queryFn: getCustomerInfo,
    enabled: iapEnabled,
  });

  const packages: ConnectPackage[] = useMemo(() => {
    const plans = plansQ.data ?? [];
    if (!iapEnabled) return plansToConnectPackages(plans);
    const priceMap = iosPriceMapFromOfferings(offeringsQ.data ?? null);
    return plansToConnectPackages(plans, priceMap);
  }, [plansQ.data, offeringsQ.data, iapEnabled]);

  const hasActiveSubscription = useMemo(() => {
    if (activeQ.data) return true;
    if (!customerQ.data) return false;
    return (
      Object.keys(customerQ.data.entitlements.active).length > 0 ||
      customerQ.data.activeSubscriptions.length > 0
    );
  }, [activeQ.data, customerQ.data]);

  const loading = plansQ.isLoading || (iapEnabled && offeringsQ.isLoading);

  const refresh = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['subscriptions'] }),
      queryClient.invalidateQueries({ queryKey: ['iap'] }),
    ]);
  }, [queryClient]);

  const purchase = useCallback(
    async (packageId: string, appleProductId?: string | null): Promise<PurchaseResult> => {
      setLastError(null);
      setPurchasingId(packageId);
      try {
        const result = await purchaseIapPackage(packageId, appleProductId);
        if (!result.success) {
          setLastError(result.error ?? 'Purchase failed.');
        } else {
          await refresh();
        }
        return result;
      } finally {
        setPurchasingId(null);
      }
    },
    [refresh],
  );

  const restore = useCallback(async (): Promise<PurchaseResult> => {
    setLastError(null);
    setRestoring(true);
    try {
      const result = await restorePurchases();
      if (!result.success) {
        setLastError(result.error ?? 'Restore failed.');
      } else {
        await refresh();
      }
      return result;
    } finally {
      setRestoring(false);
    }
  }, [refresh]);

  return {
    iapEnabled,
    loading,
    packages,
    activeSubscription: activeQ.data,
    hasActiveSubscription,
    purchasingId,
    restoring,
    lastError,
    purchase,
    restore,
    refresh,
  };
}
