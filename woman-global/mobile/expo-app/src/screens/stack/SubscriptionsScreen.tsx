import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Alert, FlatList, Linking, Platform, StyleSheet, View } from 'react-native';

import { ConnectPackageCard } from '@/components/payments/ConnectPackageCard';
import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import { useIAP } from '@/hooks/useIAP';
import type { AppStackParamList } from '@/navigation/types';
import { useAuth } from '@/providers/AuthProvider';
import { useTheme } from '@/providers/ThemeProvider';
import { getMyUserProfile } from '@/services/api/profile';
import { formatPlanPriceKes, plansToConnectPackages } from '@/services/payments/packageCatalog';
import { purchaseConnects } from '@/services/payments/paymentService';
import { getMyActiveSubscription, listActiveSubscriptionPlans } from '@/services/api/subscriptions';
import { Spacing } from '@/theme/spacing';
import type { ThemeColors } from '@/theme/types';
import type { SubscriptionPlan } from '@/types/models';

type Nav = NativeStackNavigationProp<AppStackParamList>;

export function SubscriptionsScreen() {
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { firebaseUser } = useAuth();
  const [subscribingPlanId, setSubscribingPlanId] = useState<number | null>(null);

  const isIos = Platform.OS === 'ios';
  const iap = useIAP();

  const profileQ = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyUserProfile });
  const plansQ = useQuery({ queryKey: ['subscriptions', 'plans'], queryFn: listActiveSubscriptionPlans });
  const activeQ = useQuery({ queryKey: ['subscriptions', 'active'], queryFn: getMyActiveSubscription });

  const billingEmail = (profileQ.data?.email ?? firebaseUser?.email ?? '').trim();
  const active = activeQ.data;
  const plans = plansQ.data ?? [];
  const packages = isIos && iap.iapEnabled ? iap.packages : plansToConnectPackages(plans);
  const loading = isIos && iap.iapEnabled ? iap.loading : plansQ.isLoading || activeQ.isLoading;
  const error = plansQ.error ?? activeQ.error;

  async function onSubscribe(plan: SubscriptionPlan) {
    if (active?.planId === plan.id) return;

    if (isIos) {
      if (!iap.iapEnabled) {
        Alert.alert(
          'App Store subscriptions',
          'In-app purchases are not configured in this build. Add EXPO_PUBLIC_REVENUECAT_IOS_API_KEY and use a development build.',
        );
        return;
      }
      const pkg = packages.find((p) => p.planId === plan.id);
      const result = await iap.purchase(String(plan.id), pkg?.appleProductId);
      if (result.success) {
        Alert.alert('Subscription active', 'Your plan is now active. You can continue booking providers.');
      } else if (result.error && !result.error.includes('cancelled')) {
        Alert.alert('Purchase', result.error);
      }
      return;
    }

    if (!billingEmail) {
      Alert.alert('Billing email required', 'Add an email to your profile before subscribing.');
      return;
    }

    setSubscribingPlanId(plan.id);
    try {
      const result = await purchaseConnects(String(plan.id), {
        billingEmail,
        planName: plan.name,
        priceLabel: formatPlanPriceKes(plan),
        appleProductId: plan.apple_product_id,
        paystack: {
          billingEmail,
          onNavigateToCheckout: (params: AppStackParamList['PaystackCheckout']) => {
            navigation.navigate('PaystackCheckout', params);
          },
        },
      });
      if (!result.success && result.error) {
        Alert.alert('Subscribe', result.error);
      }
    } finally {
      setSubscribingPlanId(null);
    }
  }

  async function onRestore() {
    const result = await iap.restore();
    if (result.success) {
      Alert.alert('Restored', 'Your App Store subscription has been restored.');
      void queryClient.invalidateQueries({ queryKey: ['subscriptions'] });
    } else if (result.error) {
      Alert.alert('Restore', result.error);
    }
  }

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3">Subscriptions</AppText>
      </View>

      {loading ? (
        <View style={styles.centered}>
          <Spinner />
        </View>
      ) : error ? (
        <View style={styles.centered}>
          <AppText variant="body">Could not load plans.</AppText>
          <AppButton
            variant="outline"
            style={{ marginTop: 12 }}
            onPress={() => {
              void plansQ.refetch();
              void activeQ.refetch();
              void iap.refresh();
            }}
          >
            Retry
          </AppButton>
        </View>
      ) : (
        <FlatList
          data={plans}
          keyExtractor={(p) => String(p.id)}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <>
              {active ? (
                <AppCard variant="elevated" style={styles.activeCard}>
                  <AppText variant="sectionTitle">Your current plan</AppText>
                  <AppText variant="bodyStrong" style={{ marginTop: 6 }}>
                    {active.planName}
                  </AppText>
                  {active.expiresAt ? (
                    <AppText variant="caption" style={styles.meta}>
                      Renews / expires: {active.expiresAt}
                    </AppText>
                  ) : (
                    <AppText variant="caption" style={styles.meta}>
                      Active subscription
                    </AppText>
                  )}
                  {active.connectsGranted != null ? (
                    <AppText variant="caption" style={styles.meta}>
                      Connects remaining:{' '}
                      {Math.max(0, active.connectsGranted - (active.connectsUsed ?? 0))} of {active.connectsGranted}
                    </AppText>
                  ) : (
                    <AppText variant="caption" style={styles.meta}>
                      Unlimited connects on this plan
                    </AppText>
                  )}
                </AppCard>
              ) : (
                <AppCard style={styles.activeCard}>
                  <AppText variant="body">No active subscription. Choose a plan below to get more connects.</AppText>
                </AppCard>
              )}

              {isIos ? (
                <AppText variant="caption" style={[styles.iosNote, { color: colors.onSurfaceVariant }]}>
                  Purchases are processed by Apple. Subscriptions renew automatically unless cancelled in App Store
                  settings.
                </AppText>
              ) : null}

              {plans.length > 0 ? (
                <AppText variant="sectionTitle" style={styles.plansTitle}>
                  Available plans
                </AppText>
              ) : null}
            </>
          }
          ListEmptyComponent={<ListEmpty body="No subscription plans are available right now." />}
          renderItem={({ item }) => {
            const pkg = packages.find((p) => p.planId === item.id);
            const isCurrent = active?.planId === item.id;
            const busy = subscribingPlanId === item.id || iap.purchasingId === String(item.id);
            return (
              <ConnectPackageCard
                pkg={
                  pkg ?? {
                    id: String(item.id),
                    planId: item.id,
                    name: item.name,
                    description: item.description,
                    priceLabel: formatPlanPriceKes(item),
                    isPopular: Boolean(item.is_popular),
                    features: item.features,
                    appleProductId: item.apple_product_id,
                  }
                }
                isCurrent={isCurrent}
                busy={busy}
                disabled={(subscribingPlanId != null && subscribingPlanId !== item.id) || iap.purchasingId != null}
                subscribeLabel={isIos ? 'Subscribe with Apple' : 'Subscribe'}
                onSubscribe={() => void onSubscribe(item)}
              />
            );
          }}
          ListFooterComponent={
            isIos && iap.iapEnabled ? (
              <View style={styles.footerActions}>
                <AppButton variant="outline" onPress={() => void onRestore()} loading={iap.restoring} disabled={iap.purchasingId != null}>
                  Restore purchases
                </AppButton>
                <AppButton
                  variant="outline"
                  onPress={() => void Linking.openURL('https://apps.apple.com/account/subscriptions')}
                  style={{ marginTop: 8 }}
                >
                  Manage in App Store
                </AppButton>
              </View>
            ) : null
          }
        />
      )}
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      backgroundColor: colors.background,
    },
    centered: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
    },
    list: {
      padding: Spacing.md,
      paddingBottom: 32,
    },
    activeCard: {
      marginBottom: 12,
      borderRadius: 16,
    },
    iosNote: {
      marginBottom: 12,
      lineHeight: 18,
    },
    plansTitle: {
      marginBottom: 8,
    },
    meta: {
      marginTop: 4,
      color: colors.onSurfaceVariant,
    },
    footerActions: {
      marginTop: 8,
      marginBottom: 16,
    },
  });
}
