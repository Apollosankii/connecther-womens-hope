import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/providers/ThemeProvider';
import type { ConnectPackage } from '@/services/payments/types';
import type { ThemeColors } from '@/theme/types';

type Props = {
  pkg: ConnectPackage;
  isCurrent?: boolean;
  busy?: boolean;
  disabled?: boolean;
  subscribeLabel?: string;
  onSubscribe: () => void;
};

export function ConnectPackageCard({
  pkg,
  isCurrent = false,
  busy = false,
  disabled = false,
  subscribeLabel = 'Subscribe',
  onSubscribe,
}: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);

  return (
    <AppCard variant="elevated" style={styles.card}>
      <View style={styles.header}>
        <AppText variant="bodyStrong">{pkg.name}</AppText>
        {pkg.isPopular ? (
          <View style={[styles.badge, { backgroundColor: colors.primary }]}>
            <AppText variant="caption" style={{ color: colors.onPrimary, fontWeight: '700' }}>
              Popular
            </AppText>
          </View>
        ) : null}
      </View>
      {pkg.description ? (
        <AppText variant="caption" style={styles.meta}>
          {pkg.description}
        </AppText>
      ) : null}
      <AppText variant="h3" style={[styles.price, { color: colors.primary }]}>
        {pkg.priceLabel}
      </AppText>
      {pkg.features?.length ? (
        <AppText variant="caption" style={[styles.meta, { marginTop: 6 }]}>
          {pkg.features.join(' · ')}
        </AppText>
      ) : null}
      <AppButton
        style={{ marginTop: 12 }}
        variant={isCurrent ? 'outline' : 'primary'}
        disabled={isCurrent || disabled}
        loading={busy}
        onPress={onSubscribe}
      >
        {isCurrent ? 'Current plan' : subscribeLabel}
      </AppButton>
    </AppCard>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    card: {
      borderRadius: 16,
      marginBottom: 12,
    },
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: 8,
    },
    badge: {
      paddingHorizontal: 8,
      paddingVertical: 2,
      borderRadius: 8,
    },
    meta: {
      marginTop: 4,
      color: colors.onSurfaceVariant,
    },
    price: {
      marginTop: 8,
    },
  });
}
