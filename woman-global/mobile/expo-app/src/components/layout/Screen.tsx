import type { PropsWithChildren } from 'react';
import { ScrollView, StyleSheet, View, type ViewStyle } from 'react-native';
import { SafeAreaView, useSafeAreaInsets, type Edge } from 'react-native-safe-area-context';

import { useTheme } from '@/providers/ThemeProvider';
import { Metrics } from '@/theme/metrics';
import { getFloatingTabBarScrollInset } from '@/navigation/floatingTabBar';

type Props = PropsWithChildren<{
  padded?: boolean;
  style?: ViewStyle;
  scroll?: boolean;
  /** Override SafeArea background (e.g. Figma booking canvas `#F5EEEC`). Defaults to theme `colors.background`. */
  safeAreaBackground?: string;
  /** Extra bottom padding applied to ScrollView content. Useful when UI overlays (e.g. floating tab bar). */
  scrollContentInsetBottom?: number;
  /** When true and `scroll` is enabled, automatically pads content so the floating tab bar never blocks the bottom. */
  useFloatingTabBarInset?: boolean;
  /**
   * Which edges `SafeAreaView` applies insets to. Omit for all edges.
   * Use e.g. `['bottom','left','right']` when the screen draws edge-to-edge under the status bar.
   */
  safeAreaEdges?: readonly Edge[];
}>;

export function Screen({
  children,
  padded = true,
  style,
  scroll = false,
  safeAreaBackground,
  scrollContentInsetBottom = 0,
  useFloatingTabBarInset = false,
  safeAreaEdges,
}: Props) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const content = <View style={[styles.container, padded && styles.padded, style]}>{children}</View>;
  const floatingInset = useFloatingTabBarInset ? getFloatingTabBarScrollInset(insets.bottom) : 0;
  const effectiveInset = Math.max(0, scrollContentInsetBottom, floatingInset);
  const bg = safeAreaBackground ?? colors.background;
  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: bg }]} {...(safeAreaEdges != null ? { edges: safeAreaEdges } : {})}>
      {scroll ? (
        <ScrollView
          contentContainerStyle={{ flexGrow: 1, paddingBottom: effectiveInset }}
          keyboardShouldPersistTaps="handled"
        >
          {content}
        </ScrollView>
      ) : (
        content
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
  },
  container: {
    flex: 1,
  },
  padded: {
    paddingHorizontal: Metrics.screenPaddingX,
    paddingTop: Metrics.screenPaddingTop,
  },
});

