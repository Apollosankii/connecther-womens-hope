import { useMemo } from 'react';
import { Modal, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ConnectPackageCard } from '@/components/payments/ConnectPackageCard';
import { AppButton } from '@/components/ui/AppButton';
import { AppText } from '@/components/ui/AppText';
import { Spinner } from '@/components/ui/Spinner';
import { useIAP } from '@/hooks/useIAP';
import { useTheme } from '@/providers/ThemeProvider';
import type { ThemeColors } from '@/theme/types';

type Props = {
  visible: boolean;
  onClose: () => void;
  onPurchaseComplete?: () => void;
};

export function PurchaseModal({ visible, onClose, onPurchaseComplete }: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const insets = useSafeAreaInsets();
  const iap = useIAP();

  const activePlanId = iap.activeSubscription?.planId;

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="Close" />
      <View style={[styles.sheet, { paddingBottom: Math.max(16, insets.bottom), backgroundColor: colors.surface }]}>
        <View style={styles.handle} />
        <AppText variant="h3" style={{ color: colors.onBackground }}>
          Get more connects
        </AppText>
        <AppText variant="caption" style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
          {Platform.OS === 'ios'
            ? 'Subscriptions are billed through the App Store. Manage or cancel in Settings.'
            : 'Pay securely with Paystack (M-Pesa, card, and more).'}
        </AppText>

        {iap.lastError ? (
          <AppText variant="caption" style={[styles.error, { color: colors.accent }]}>
            {iap.lastError}
          </AppText>
        ) : null}

        {iap.loading ? (
          <View style={styles.centered}>
            <Spinner />
          </View>
        ) : (
          <ScrollView style={styles.list} keyboardShouldPersistTaps="handled">
            {iap.packages.map((pkg) => (
              <ConnectPackageCard
                key={pkg.id}
                pkg={pkg}
                isCurrent={activePlanId === pkg.planId}
                busy={iap.purchasingId === pkg.id}
                disabled={iap.purchasingId != null && iap.purchasingId !== pkg.id}
                onSubscribe={() => {
                  void iap.purchase(pkg.id, pkg.appleProductId).then((res) => {
                    if (res.success) {
                      onPurchaseComplete?.();
                      onClose();
                    }
                  });
                }}
              />
            ))}
          </ScrollView>
        )}

        {Platform.OS === 'ios' && iap.iapEnabled ? (
          <AppButton variant="outline" onPress={() => void iap.restore()} loading={iap.restoring} disabled={iap.purchasingId != null}>
            Restore purchases
          </AppButton>
        ) : null}

        <AppButton variant="outline" onPress={onClose} style={{ marginTop: 8 }}>
          Close
        </AppButton>
      </View>
    </Modal>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    backdrop: {
      flex: 1,
      backgroundColor: colors.scrim,
    },
    sheet: {
      borderTopLeftRadius: 20,
      borderTopRightRadius: 20,
      paddingHorizontal: 16,
      paddingTop: 12,
      maxHeight: '85%',
    },
    handle: {
      alignSelf: 'center',
      width: 40,
      height: 4,
      borderRadius: 2,
      backgroundColor: colors.outlineSoft,
      marginBottom: 12,
    },
    subtitle: {
      marginTop: 4,
      marginBottom: 12,
    },
    error: {
      marginBottom: 8,
    },
    list: {
      maxHeight: 360,
    },
    centered: {
      paddingVertical: 32,
      alignItems: 'center',
    },
  });
}
