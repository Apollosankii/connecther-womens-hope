import { LinearGradient } from 'expo-linear-gradient';
import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, type TextStyle, type ViewStyle } from 'react-native';

/** Matches Figma service menu / checkout CTA (`87:204`, `2023:141`): #F215C1 → #EA9DF2. */
const GRADIENT = ['#F215C1', '#EA9DF2'] as const;

type Props = {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  /** Figma service menu CTA (`87:204`): ~43px height, narrower pill. */
  size?: 'default' | 'compact';
  style?: ViewStyle;
  textStyle?: TextStyle;
  children?: ReactNode;
};

/**
 * Pill CTA with pink→lavender gradient (matches Android `bg_gradient_cta` / Figma corporate flows).
 */
export function GradientCtaButton({ title, onPress, disabled, loading, size = 'default', style, textStyle, children }: Props) {
  const busy = Boolean(loading);
  const compact = size === 'compact';
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled || busy}
      onPress={onPress}
      style={({ pressed }) => [
        compact ? styles.shadowCompact : styles.shadow,
        pressed && !busy ? styles.pressed : null,
        (disabled || busy) && styles.dimmed,
        style,
      ]}
    >
      <LinearGradient
        colors={GRADIENT}
        start={{ x: 0, y: 0.5 }}
        end={{ x: 1, y: 0.5 }}
        style={[styles.gradient, compact && styles.gradientCompact]}
      >
        {children ? (
          children
        ) : (
          <Text style={[styles.label, textStyle]} numberOfLines={1}>
            {title}
          </Text>
        )}
        {busy ? <ActivityIndicator color="#FFFFFF" style={styles.spinner} /> : null}
      </LinearGradient>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  shadow: {
    borderRadius: 999,
    overflow: 'hidden',
    elevation: 4,
    shadowColor: '#FF26B9',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.28,
    shadowRadius: 12,
  },
  shadowCompact: {
    borderRadius: 999,
    overflow: 'hidden',
    elevation: 3,
    shadowColor: '#F215C1',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.22,
    shadowRadius: 10,
    alignSelf: 'center',
    minWidth: 164,
    maxWidth: '100%',
  },
  pressed: {
    opacity: 0.92,
    transform: [{ scale: 0.99 }],
  },
  dimmed: {
    opacity: 0.55,
  },
  gradient: {
    minHeight: 52,
    paddingHorizontal: 24,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
  },
  gradientCompact: {
    minHeight: 43,
    paddingVertical: 10,
    paddingHorizontal: 28,
  },
  label: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '600',
  },
  spinner: {
    marginLeft: 4,
  },
});
