/**
 * Layout tokens ported from Android dp values used across screens.
 */
export const Metrics = {
  // Horizontal screen padding: Android varies 12–20dp depending on screen.
  // Use 16 as the default content gutter.
  screenPaddingX: 16,
  screenPaddingTop: 12,
  headerPaddingX: 20,

  // Common component heights
  inputHeight: 48,
  buttonHeight: 48,

  // Radii
  radiusSm: 12,
  radiusMd: 16,
  radiusLg: 20,
  radiusPill: 999,

  // Media blocks (service cards)
  mediaHeightSm: 88,
  mediaHeightMd: 110,

  // Icon button
  iconButtonSize: 44, // Android Home: 44dp
  iconButtonPadding: 10,

  // Cards
  cardPadding: 16,
  cardPaddingSm: 12,
} as const;

