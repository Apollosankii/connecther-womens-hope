import type { PropsWithChildren, ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, type ViewStyle } from 'react-native';

import { useTheme } from '@/providers/ThemeProvider';
import { Metrics } from '@/theme/metrics';

type Variant = 'primary' | 'outline';

type Props = PropsWithChildren<{
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  variant?: Variant;
  /** default: fixed height; content: minHeight + vertical padding for multi-line / icon rows */
  size?: 'default' | 'content';
  style?: ViewStyle;
}>;

export function AppButton({
  children,
  onPress,
  disabled,
  loading,
  variant = 'primary',
  size = 'default',
  style,
}: Props) {
  const { colors } = useTheme();
  const isDisabled = Boolean(disabled || loading);
  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      style={({ pressed }) => [
        styles.base,
        size === 'content' ? styles.contentSized : styles.fixedHeight,
        size === 'content' && styles.contentAlign,
        variant === 'primary' ? [styles.primary, { backgroundColor: colors.primary }] : [styles.outline, { borderColor: colors.primary }],
        pressed && !isDisabled && styles.pressed,
        isDisabled && styles.disabled,
        style,
      ]}
      accessibilityRole="button"
    >
      {loading ? (
        <ActivityIndicator color={variant === 'primary' ? colors.onPrimary : colors.primary} />
      ) : (
        <ButtonLabel variant={variant} colors={colors}>
          {children}
        </ButtonLabel>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: Metrics.radiusSm,
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  fixedHeight: {
    height: Metrics.buttonHeight,
    alignItems: 'center',
  },
  contentSized: {
    minHeight: Metrics.buttonHeight,
    paddingVertical: 12,
  },
  contentAlign: {
    alignItems: 'stretch',
  },
  primary: {
  },
  outline: {
    backgroundColor: 'transparent',
    borderWidth: 1,
  },
  pressed: {
    opacity: 0.9,
  },
  disabled: {
    opacity: 0.6,
  },
  text: {
    fontSize: 16,
    fontWeight: '600',
  },
  textOnPrimary: {
  },
  textPrimary: {
  },
});

function ButtonLabel({
  variant,
  children,
  colors,
}: {
  variant: Variant;
  children: ReactNode;
  colors: { primary: string; onPrimary: string };
}) {
  if (typeof children === 'string' || typeof children === 'number') {
    return (
      <Text
        style={[
          styles.text,
          variant === 'primary' ? { color: colors.onPrimary } : { color: colors.primary },
        ]}
      >
        {children}
      </Text>
    );
  }
  return <>{children}</>;
}

