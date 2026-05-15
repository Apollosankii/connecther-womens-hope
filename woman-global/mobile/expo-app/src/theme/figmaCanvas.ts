import type { ThemeColors } from '@/theme/types';

/** Figma Connecther file: booking + services canvas (e.g. Checkout `2023:141`, Home Cleaning `87:204`). */
export const CONNECTHER_FIGMA_SOFT_BG = '#F5EEEC';

/**
 * Service menu frames from Figma MCP — Home Cleaning `87:204`, Corporate add-ons/toggle `2022:310`.
 * Light-mode reference tokens; dark mode uses `buildServiceMenuTheme` + theme colors.
 */
export const FIGMA_SERVICE_MENU = {
  canvasBg: '#F5EEEC',
  /** Top banner — Android `@drawable/connecther_` viewport 389×216; clip-path corner radius 5. */
  topAssetWidth: 389,
  topAssetHeight: 216,
  bannerCorner: 5,
  bannerTitle: '#FFF8F8',
  /** White cards + typography */
  cardBg: '#FFFFFF',
  cardRadius: 22,
  cardShadow: '#5A6CEA',
  cardShadowOpacity: 0.07,
  titleColor: '#09051C',
  priceColor: '#161213',
  unitColor: '#8E8989',
  qtyColor: 'rgba(24,24,24,0.70)',
  /** Stepper chips (Figma minus / plus assets) */
  stepperMinusBg: '#E4D0EF',
  stepperPlusBg: '#F215C1',
  /** Thumbnail elevation (M3 light / 2) */
  thumbRadius: 16,
  thumbSize: 62,
  /** Corporate add-on checkboxes */
  checkboxBorder: '#BF43A4',
  checkboxFill: '#BF43A4',
  /** Section titles e.g. “Add ons” */
  sectionTitleColor: '#09051C',
  sectionSubtitleColor: '#09051C',
} as const;

const FM = FIGMA_SERVICE_MENU;

export type ServiceMenuThemeTokens = {
  topAssetWidth: number;
  topAssetHeight: number;
  bannerCorner: number;
  cardRadius: number;
  thumbRadius: number;
  thumbSize: number;
  canvasBg: string;
  cardBg: string;
  cardShadow: string;
  cardShadowOpacity: number;
  titleColor: string;
  priceColor: string;
  unitColor: string;
  qtyColor: string;
  stepperMinusBg: string;
  stepperPlusBg: string;
  checkboxBorder: string;
  checkboxFill: string;
  sectionTitleColor: string;
  sectionSubtitleColor: string;
  thumbPlaceholderBg: string;
  thumbShadowBg: string;
  backPillBg: string;
  backIconOnHero: string;
  bannerTextOnHero: string;
  stepperMinusIcon: string;
  stepperPlusIcon: string;
  checkboxUncheckedBg: string;
};

/** Same Figma layout in light and dark; colors follow theme. */
export function buildServiceMenuTheme(colors: ThemeColors, mode: 'light' | 'dark'): ServiceMenuThemeTokens {
  const dim = {
    topAssetWidth: FM.topAssetWidth,
    topAssetHeight: FM.topAssetHeight,
    bannerCorner: FM.bannerCorner,
    cardRadius: FM.cardRadius,
    thumbRadius: FM.thumbRadius,
    thumbSize: FM.thumbSize,
  };
  if (mode === 'light') {
    return {
      ...dim,
      canvasBg: colors.softCanvas,
      cardBg: FM.cardBg,
      cardShadow: FM.cardShadow,
      cardShadowOpacity: FM.cardShadowOpacity,
      titleColor: FM.titleColor,
      priceColor: FM.priceColor,
      unitColor: FM.unitColor,
      qtyColor: FM.qtyColor,
      stepperMinusBg: FM.stepperMinusBg,
      stepperPlusBg: FM.stepperPlusBg,
      checkboxBorder: FM.checkboxBorder,
      checkboxFill: FM.checkboxFill,
      sectionTitleColor: FM.sectionTitleColor,
      sectionSubtitleColor: FM.sectionSubtitleColor,
      thumbPlaceholderBg: '#E8E4E2',
      thumbShadowBg: '#10100F',
      backPillBg: 'rgba(255,255,255,0.92)',
      backIconOnHero: '#2A2A2A',
      bannerTextOnHero: FM.bannerTitle,
      stepperMinusIcon: '#FFFFFF',
      stepperPlusIcon: '#FFFFFF',
      checkboxUncheckedBg: '#FFFFFF',
    };
  }
  return {
    ...dim,
    canvasBg: colors.softCanvas,
    cardBg: colors.surface,
    cardShadow: colors.primary,
    cardShadowOpacity: 0.2,
    titleColor: colors.onSurface,
    priceColor: colors.onSurface,
    unitColor: colors.onSurfaceVariant,
    qtyColor: colors.onSurface,
    stepperMinusBg: colors.surfaceVariant,
    stepperPlusBg: colors.primary,
    checkboxBorder: colors.primary,
    checkboxFill: colors.primary,
    sectionTitleColor: colors.onSurface,
    sectionSubtitleColor: colors.onSurfaceVariant,
    thumbPlaceholderBg: colors.surfaceVariant,
    thumbShadowBg: '#0A0809',
    backPillBg: 'rgba(22,18,20,0.75)',
    backIconOnHero: '#FFF8F8',
    bannerTextOnHero: '#FFF8F8',
    stepperMinusIcon: colors.onSurface,
    stepperPlusIcon: colors.onPrimary,
    checkboxUncheckedBg: colors.surfaceVariant,
  };
}
