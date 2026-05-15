import type { PropsWithChildren } from 'react';
import { StyleSheet, View, type StyleProp, type ViewProps, type ViewStyle } from 'react-native';

import { useTheme } from '@/providers/ThemeProvider';
import { Metrics } from '@/theme/metrics';

type Props = PropsWithChildren<
  ViewProps & {
    variant?: 'default' | 'elevated';
    padding?: 'md' | 'sm' | 'none';
    style?: StyleProp<ViewStyle>;
  }
>;

export function AppCard({ children, variant = 'default', padding = 'md', style, ...rest }: Props) {
  const { colors } = useTheme();
  return (
    <View
      {...rest}
      style={[
        styles.base,
        { backgroundColor: colors.surface },
        variant === 'elevated'
          ? [styles.elevated, { shadowColor: '#000' }]
          : [styles.default, { borderColor: colors.outlineSoft, shadowColor: '#000' }],
        padding === 'md' ? styles.padMd : padding === 'sm' ? styles.padSm : null,
        style,
      ]}
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: Metrics.radiusMd,
  },
  default: {
    // Subtle soft shadow like the shipped Android UI (no visible border).
    borderWidth: 1,
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
    elevation: 3,
  },
  elevated: {
    shadowColor: '#000',
    shadowOpacity: 0.12,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
    elevation: 6,
    borderWidth: 0,
  },
  padMd: {
    padding: Metrics.cardPadding,
  },
  padSm: {
    padding: Metrics.cardPaddingSm,
  },
});

