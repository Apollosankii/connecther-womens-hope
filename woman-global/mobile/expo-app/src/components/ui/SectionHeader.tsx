import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View, type ViewStyle } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { Spacing } from '@/theme/spacing';

type Props = {
  overline?: string;
  title: string;
  actionLabel?: string;
  onPressAction?: () => void;
  style?: ViewStyle;
  right?: ReactNode;
};

export function SectionHeader({ overline, title, actionLabel, onPressAction, right, style }: Props) {
  return (
    <View style={[styles.wrap, style]}>
      <View style={styles.left}>
        {overline ? <AppText variant="overline">{overline}</AppText> : null}
        <AppText variant="sectionTitle">{title}</AppText>
      </View>
      {right ? right : null}
      {actionLabel && onPressAction ? (
        <Pressable onPress={onPressAction} hitSlop={10} accessibilityRole="button">
          <AppText variant="link">{actionLabel}</AppText>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    gap: Spacing.md,
  },
  left: {
    flex: 1,
    gap: 4,
  },
});

