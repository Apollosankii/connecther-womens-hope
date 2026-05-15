export const FLOATING_TAB_BAR_HEIGHT = 64;
export const FLOATING_TAB_BAR_BOTTOM_GAP = 10;
/** Extra space so the last line of text clears the pill comfortably (descenders, multi-line). */
export const FLOATING_TAB_BAR_EXTRA_SCROLL_PAD = 24;

/**
 * Native tab bar height — must stay in sync with `tabBarStyle.height` in `MainTabs.tsx`.
 * (`paddingBottom` / safe-area padding for icons lives inside this height.)
 */
export function getFloatingTabBarNativeHeight(insetsBottom: number) {
  return FLOATING_TAB_BAR_HEIGHT + Math.max(insetsBottom - 6, 0);
}

/**
 * Bottom padding for scrollable tab-root content when `Screen` uses default bottom safe-area.
 * Only the **pill height** is counted here: `SafeAreaView` already lifts content by `insets.bottom`,
 * so adding `insets.bottom` again would over-pad on iOS and still under-clear on some Android layouts.
 */
export function getFloatingTabBarScrollInset(insetsBottom: number) {
  return getFloatingTabBarNativeHeight(insetsBottom) + FLOATING_TAB_BAR_EXTRA_SCROLL_PAD;
}

