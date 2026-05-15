import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/providers/ThemeProvider';
import type { ThemeColors } from '@/theme/types';
import { quoteTotal, type QuoteLine } from '@/utils/bookingQuote';

type Props = {
  lines: readonly QuoteLine[];
};

function formatKes(n: number) {
  return `KES ${Math.round(n).toLocaleString('en-KE')}`;
}

export function BookingOrderSummary({ lines }: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const total = quoteTotal(lines);

  if (lines.length === 0) return null;

  return (
    <View style={[styles.wrap, { backgroundColor: colors.surface, borderColor: colors.outlineSoft }]}>
      <AppText variant="caption" style={[styles.heading, { color: colors.onSurfaceVariant }]}>
        YOUR SELECTIONS
      </AppText>
      {lines.map((line, i) => {
        const sub = line.unitPrice * line.quantity;
        return (
          <View key={`${line.label}-${i}`} style={styles.row}>
            <View style={styles.rowLeft}>
              <AppText variant="bodyStrong" numberOfLines={2} style={{ color: colors.onSurface }}>
                {line.label}
              </AppText>
              <AppText variant="caption" style={{ color: colors.onSurfaceVariant, marginTop: 2 }}>
                {line.quantity} × {formatKes(line.unitPrice)}
              </AppText>
            </View>
            <AppText variant="bodyStrong" style={{ color: colors.onSurface }}>
              {formatKes(sub)}
            </AppText>
          </View>
        );
      })}
      <View style={[styles.divider, { backgroundColor: colors.outlineSoft }]} />
      <View style={styles.totalRow}>
        <AppText variant="bodyStrong" style={{ color: colors.onBackground }}>
          Order total
        </AppText>
        <AppText variant="bodyStrong" style={{ color: colors.primary }}>
          {formatKes(total)}
        </AppText>
      </View>
    </View>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    wrap: {
      borderRadius: 16,
      borderWidth: StyleSheet.hairlineWidth,
      padding: 14,
      gap: 10,
    },
    heading: {
      letterSpacing: 0.6,
      fontWeight: '600',
    },
    row: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      gap: 12,
    },
    rowLeft: {
      flex: 1,
      minWidth: 0,
    },
    divider: {
      height: StyleSheet.hairlineWidth,
      marginVertical: 2,
    },
    totalRow: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
    },
  });
}
