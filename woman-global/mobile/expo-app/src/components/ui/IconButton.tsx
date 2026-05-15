import type { PropsWithChildren } from 'react';
import { Pressable, StyleSheet, type ViewStyle } from 'react-native';

import { useTheme } from '@/providers/ThemeProvider';
import { Metrics } from '@/theme/metrics';

type Props = PropsWithChildren<{
  onPress: () => void;
  accessibilityLabel: string;
  style?: ViewStyle;
  variant?: 'filled' | 'surface';
}>;

export function IconButton({
  onPress,
  accessibilityLabel,
  style,
  children,
  variant = 'filled',
}: Props) {
  const { colors } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      hitSlop={10}
      style={({ pressed }) => [
        styles.base,
        variant === 'filled'
          ? [styles.filled, { backgroundColor: colors.accent }]
          : [styles.surface, { backgroundColor: colors.surface, borderColor: colors.outlineSoft }],
        pressed && styles.pressed,
        style,
      ]}
    >
      {children}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    width: Metrics.iconButtonSize,
    height: Metrics.iconButtonSize,
    borderRadius: Metrics.radiusSm,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Metrics.iconButtonPadding,
  },
  filled: {
    shadowColor: '#000',
    shadowOpacity: 0.10,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
    elevation: 4,
  },
  surface: {
    borderWidth: 1,
  },
  pressed: {
    opacity: 0.9,
  },
});

