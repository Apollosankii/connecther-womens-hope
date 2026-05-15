import type { ConnectPackage } from '@/services/payments/types';
import type { SubscriptionPlan } from '@/types/models';
import { Platform } from 'react-native';

export function formatPlanPriceKes(plan: SubscriptionPlan): string {
  const cur = (plan.currency ?? 'KES').toUpperCase();
  const n = Number(plan.price);
  if (!Number.isFinite(n)) return `${cur} —`;
  return `${cur} ${Math.round(n).toLocaleString('en-KE')}`;
}

export function plansToConnectPackages(
  plans: SubscriptionPlan[],
  iosPriceByProductId?: Record<string, string>,
): ConnectPackage[] {
  return plans.map((plan) => {
    const appleId = plan.apple_product_id?.trim() ?? null;
    const iosPrice = appleId && iosPriceByProductId?.[appleId];
    const priceLabel =
      Platform.OS === 'ios' && iosPrice ? iosPrice : formatPlanPriceKes(plan);
    return {
      id: String(plan.id),
      planId: plan.id,
      name: plan.name,
      description: plan.description,
      priceLabel,
      isPopular: Boolean(plan.is_popular),
      features: plan.features,
      appleProductId: appleId,
    };
  });
}
