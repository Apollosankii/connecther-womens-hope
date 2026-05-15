import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { useSafeAreaInsets, type Edge } from 'react-native-safe-area-context';

import { Screen } from '@/components/layout/Screen';
import { AppText } from '@/components/ui/AppText';
import { GradientCtaButton } from '@/components/ui/GradientCtaButton';
import { Spinner } from '@/components/ui/Spinner';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { getServiceById } from '@/services/api/services';
import { buildServiceMenuTheme, type ServiceMenuThemeTokens } from '@/theme/figmaCanvas';
import { quoteTotal } from '@/utils/bookingQuote';
import { encodeQuoteLinesForIntent } from '@/utils/bookingQuoteIntent';
import {
  buildQuoteLinesFromTaskMenuRows,
  cloneTaskMenuRows,
  getTaskMenuJsonString,
  parseServiceTaskMenu,
  type TaskMenuQuantity,
  type TaskMenuRow,
  type TaskMenuToggle,
} from '@/utils/serviceTaskMenu';

type Props = NativeStackScreenProps<AppStackParamList, 'ServiceMenu'>;

type UiMode = 'loading' | 'menu' | 'no_menu_grid' | 'missing';

/** Hero image draws under status bar; only pad left/right/bottom (light + dark). */
const SERVICE_MENU_HERO_SAFE_AREA: readonly Edge[] = ['bottom', 'left', 'right'];

export function ServiceMenuScreen({ navigation, route }: Props) {
  const { serviceId, serviceName, providerRef, providerId, providerDisplayName, providerPic } = route.params;
  const { colors, effectiveMode } = useTheme();
  const sm = useMemo(() => buildServiceMenuTheme(colors, effectiveMode), [colors, effectiveMode]);
  const canvas = sm.canvasBg;
  const styles = useMemo(() => makeStyles(sm), [sm]);
  const figmaStyles = useMemo(() => createServiceMenuFigmaStyles(sm), [sm]);
  const insets = useSafeAreaInsets();
  const { width: windowWidth } = useWindowDimensions();

  const figmaHeroHeight = useMemo(
    () => (windowWidth * sm.topAssetHeight) / sm.topAssetWidth,
    [windowWidth, sm.topAssetHeight, sm.topAssetWidth],
  );

  const toolbarTitle = (serviceName ?? '').trim() || 'Choose services';
  const bannerOverlayTitle = `${toolbarTitle} Services`;

  const [rows, setRows] = useState<TaskMenuRow[]>([]);
  const [bannerUrl, setBannerUrl] = useState<string | undefined>();
  const [uiMode, setUiMode] = useState<UiMode>('loading');
  const [didAutoForward, setDidAutoForward] = useState(false);

  const title = (serviceName ?? '').trim() || 'Service';

  const serviceQ = useQuery({
    queryKey: ['service', serviceId],
    queryFn: () => getServiceById(serviceId),
  });

  const svc = serviceQ.data ?? null;

  useEffect(() => {
    if (serviceQ.isLoading || didAutoForward) return;
    if (svc == null && !serviceQ.isFetching && serviceQ.isFetched) {
      setUiMode('missing');
      return;
    }
    if (svc == null) return;

    const parsed = parseServiceTaskMenu(getTaskMenuJsonString(svc));
    if (!parsed) {
      if (providerRef?.trim()) {
        setDidAutoForward(true);
        if (providerId == null) {
          Alert.alert('Booking', 'Missing provider id. Go back and try again.');
          navigation.goBack();
          return;
        }
        navigation.replace('RequestBooking', {
          providerId,
          providerRef: providerRef.trim(),
          serviceId,
          serviceName,
          providerDisplayName,
          providerPic: providerPic ?? null,
        });
        return;
      }
      setUiMode('no_menu_grid');
      const pic = (svc as { service_pic?: string | null }).service_pic ?? (svc as { pic?: string | null }).pic;
      setBannerUrl(typeof pic === 'string' && pic.trim() ? pic.trim() : undefined);
      return;
    }

    setBannerUrl(parsed.bannerImageUrl?.trim() || undefined);
    setRows(cloneTaskMenuRows(parsed.rows));
    setUiMode('menu');
  }, [svc, serviceQ.isLoading, serviceQ.isFetching, serviceQ.isFetched, providerRef, providerId, serviceId, serviceName, providerDisplayName, providerPic, navigation, didAutoForward]);

  const quoteLines = useMemo(() => buildQuoteLinesFromTaskMenuRows(rows), [rows]);
  const total = useMemo(() => quoteTotal(quoteLines), [quoteLines]);

  const bumpQty = useCallback((key: string, delta: number) => {
    setRows((prev) =>
      prev.map((r) => {
        if (r.type !== 'quantity' || r.key !== key) return r;
        const q = Math.min(r.max, Math.max(r.min, r.quantity + delta));
        return { ...r, quantity: q };
      }),
    );
  }, []);

  const setToggle = useCallback((key: string, checked: boolean) => {
    setRows((prev) =>
      prev.map((r) => {
        if (r.type !== 'toggle' || r.key !== key) return r;
        return { ...r, checked };
      }),
    );
  }, []);

  const onPrimaryPress = () => {
    const ref = providerRef?.trim();
    if (uiMode === 'no_menu_grid') {
      navigation.navigate('ProviderRecommendation', { serviceId, serviceName });
      return;
    }
    if (total <= 0) {
      Alert.alert('Service menu', 'Pick at least one priced item (quantity above zero or enable an add-on).');
      return;
    }
    const json = encodeQuoteLinesForIntent(quoteLines);
    if (ref) {
      if (providerId == null) {
        Alert.alert('Booking', 'Missing provider id.');
        return;
      }
      navigation.navigate('RequestBooking', {
        providerId,
        providerRef: ref,
        serviceId,
        serviceName,
        providerDisplayName,
        providerPic: providerPic ?? null,
        prefillPrice: total,
        quoteLinesJson: json,
      });
    } else {
      navigation.navigate('ProviderRecommendation', {
        serviceId,
        serviceName,
        prefillTotal: total,
        quoteLinesJson: json,
      });
    }
  };

  const bannerSource =
    bannerUrl?.trim() ||
    (typeof (svc as { service_pic?: string | null })?.service_pic === 'string'
      ? String((svc as { service_pic?: string | null }).service_pic).trim()
      : '') ||
    (typeof (svc as { pic?: string | null })?.pic === 'string' ? String((svc as { pic?: string | null }).pic).trim() : '');

  /** Full-bleed hero when a banner URL exists (menu or no-menu grid). */
  const heroUri = bannerSource?.trim() ?? '';
  const hasHero = Boolean(heroUri);
  const screenSafeEdges = hasHero ? SERVICE_MENU_HERO_SAFE_AREA : undefined;

  if (uiMode === 'missing') {
    return (
      <Screen padded={false} safeAreaBackground={canvas} safeAreaEdges={screenSafeEdges}>
        <View style={[styles.centered, { paddingTop: insets.top + 8, backgroundColor: canvas }]}>
          <AppText variant="body">Service not found.</AppText>
          <Pressable onPress={() => navigation.goBack()} style={{ marginTop: 12 }}>
            <AppText variant="link">Go back</AppText>
          </Pressable>
        </View>
      </Screen>
    );
  }

  if (serviceQ.isError) {
    return (
      <Screen padded={false} safeAreaBackground={canvas} safeAreaEdges={screenSafeEdges}>
        <View style={[styles.centered, { paddingTop: insets.top + 8, backgroundColor: canvas }]}>
          <AppText variant="body">Could not load this service.</AppText>
          <Pressable onPress={() => serviceQ.refetch()} style={{ marginTop: 12 }}>
            <AppText variant="link">Retry</AppText>
          </Pressable>
        </View>
      </Screen>
    );
  }

  if (serviceQ.isLoading || uiMode === 'loading') {
    return (
      <Screen padded={false} safeAreaBackground={canvas} safeAreaEdges={screenSafeEdges}>
        <View style={[styles.centered, { paddingTop: insets.top + 8, backgroundColor: canvas }]}>
          <Spinner />
        </View>
      </Screen>
    );
  }

  return (
    <Screen padded={false} safeAreaBackground={canvas} safeAreaEdges={screenSafeEdges}>
      <View style={[styles.root, { backgroundColor: canvas }]}>
        <>
          {hasHero ? (
            <View style={[styles.bannerBlock, { height: figmaHeroHeight }]}>
              <Image source={{ uri: heroUri }} style={styles.bannerImg} resizeMode="cover" accessibilityIgnoresInvertColors />
              <LinearGradient
                colors={['transparent', 'rgba(0,0,0,0.42)']}
                locations={[0.35, 1]}
                style={StyleSheet.absoluteFill}
                pointerEvents="none"
              />
              <View style={[styles.bannerChrome, { paddingTop: Math.max(insets.top, 8) }]}>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="Back"
                  onPress={() => navigation.goBack()}
                  style={({ pressed }) => [styles.backPill, pressed && { opacity: 0.88 }]}
                >
                  <MaterialCommunityIcons name="arrow-left" size={22} color={sm.backIconOnHero} />
                </Pressable>
                <Text style={styles.bannerToolbarTitle} numberOfLines={2}>
                  {toolbarTitle}
                </Text>
              </View>
              <Text style={styles.bannerTitle} numberOfLines={2}>
                {bannerOverlayTitle}
              </Text>
            </View>
          ) : (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Back"
              onPress={() => navigation.goBack()}
              style={({ pressed }) => [
                styles.backPill,
                styles.floatingBack,
                { top: Math.max(insets.top, 8) + 4, left: 12 },
                pressed && { opacity: 0.88 },
              ]}
            >
              <MaterialCommunityIcons name="arrow-left" size={22} color={colors.onBackground} />
            </Pressable>
          )}
        </>

        {uiMode === 'no_menu_grid' ? (
          <View style={[styles.placeholderWrap, hasHero ? { paddingTop: 0 } : { paddingTop: 60 }]}>
            <AppText variant="body" style={{ textAlign: 'center', color: colors.onSurfaceVariant, paddingHorizontal: 24 }}>
              This service does not have a line-item menu yet. Find a provider to send a custom request.
            </AppText>
          </View>
        ) : (
          <FlatList
            data={rows}
            keyExtractor={(item, i) =>
              item.type === 'quantity' || item.type === 'toggle'
                ? `${item.type}-${item.key}-${i}`
                : item.type === 'section'
                  ? `section-${i}-${item.title}`
                  : `tm-${i}`
            }
            contentContainerStyle={[
              styles.list,
              hasHero ? { paddingTop: 0 } : { paddingTop: 60 },
              { paddingBottom: 120 + insets.bottom },
            ]}
            ItemSeparatorComponent={() => <View style={{ height: 12 }} />}
            renderItem={({ item }) => (
              <MenuRowView row={item} sm={sm} figmaStyles={figmaStyles} onBumpQty={bumpQty} onToggle={setToggle} />
            )}
          />
        )}

        <View style={[styles.footer, styles.footerParity, { paddingBottom: Math.max(12, insets.bottom) }]}>
          {uiMode === 'menu' ? (
            <Text style={[styles.totalLine, { color: sm.priceColor }]}>Total: KES {Math.round(total)}</Text>
          ) : null}
          <GradientCtaButton
            title={uiMode === 'no_menu_grid' ? 'Find a provider' : 'Book now'}
            onPress={onPrimaryPress}
            size="compact"
            style={{ marginTop: uiMode === 'menu' ? 10 : 0, alignSelf: 'center' }}
          />
        </View>
      </View>
    </Screen>
  );
}

function createServiceMenuFigmaStyles(sm: ServiceMenuThemeTokens) {
  return StyleSheet.create({
    card: {
      minHeight: 103,
      backgroundColor: sm.cardBg,
      borderRadius: sm.cardRadius,
      paddingVertical: 14,
      paddingHorizontal: 15,
      shadowColor: sm.cardShadow,
      shadowOffset: { width: 0, height: 10 },
      shadowOpacity: sm.cardShadowOpacity,
      shadowRadius: 22,
      elevation: 7,
    },
    cardRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
    },
    thumbShadow: {
      width: sm.thumbSize,
      height: sm.thumbSize,
      borderRadius: sm.thumbRadius,
      overflow: 'hidden',
      backgroundColor: sm.thumbShadowBg,
      shadowColor: '#000',
      shadowOpacity: 0.22,
      shadowRadius: 4,
      shadowOffset: { width: 0, height: 2 },
      elevation: 4,
    },
    thumb: {
      width: sm.thumbSize,
      height: sm.thumbSize,
      borderRadius: sm.thumbRadius,
    },
    thumbPlaceholder: {
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: sm.thumbPlaceholderBg,
    },
    cardMid: {
      flex: 1,
      minWidth: 0,
      gap: 2,
    },
    lineTitle: {
      fontSize: 15,
      fontWeight: '600',
      color: sm.titleColor,
      lineHeight: 20,
    },
    unitLabel: {
      fontSize: 13,
      fontWeight: '400',
      color: sm.unitColor,
      marginTop: 2,
    },
    priceLine: {
      fontSize: 15,
      fontWeight: '400',
      color: sm.priceColor,
      letterSpacing: 0.5,
      marginTop: 4,
    },
    stepper: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 6,
      width: 86,
      justifyContent: 'flex-end',
    },
    stepMinus: {
      width: 28,
      height: 28,
      borderRadius: 8,
      backgroundColor: sm.stepperMinusBg,
      alignItems: 'center',
      justifyContent: 'center',
    },
    stepPlus: {
      width: 28,
      height: 28,
      borderRadius: 8,
      backgroundColor: sm.stepperPlusBg,
      alignItems: 'center',
      justifyContent: 'center',
    },
    qtyNum: {
      minWidth: 22,
      textAlign: 'center',
      fontSize: 16,
      fontWeight: '500',
      color: sm.qtyColor,
    },
    checkboxOuter: {
      width: 26,
      height: 26,
      borderRadius: 6,
      borderWidth: 2,
      borderColor: sm.checkboxBorder,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: sm.checkboxUncheckedBg,
    },
    checkboxOuterOn: {
      backgroundColor: sm.checkboxFill,
      borderColor: sm.checkboxFill,
    },
    sectionBlock: {
      marginTop: 8,
      marginBottom: 4,
      paddingHorizontal: 2,
    },
    sectionTitle: {
      fontSize: 15,
      fontWeight: '700',
      color: sm.sectionTitleColor,
    },
    sectionSubtitle: {
      marginTop: 6,
      fontSize: 13,
      fontWeight: '300',
      color: sm.sectionSubtitleColor,
      lineHeight: 18,
      maxWidth: 320,
      alignSelf: 'flex-start',
    },
  });
}

function MenuRowView({
  row,
  sm,
  figmaStyles,
  onBumpQty,
  onToggle,
}: {
  row: TaskMenuRow;
  sm: ServiceMenuThemeTokens;
  figmaStyles: ReturnType<typeof createServiceMenuFigmaStyles>;
  onBumpQty: (key: string, delta: number) => void;
  onToggle: (key: string, v: boolean) => void;
}) {
  if (row.type === 'section') {
    return (
      <View style={figmaStyles.sectionBlock}>
        <Text style={figmaStyles.sectionTitle}>{row.title}</Text>
        {row.subtitle ? (
          <Text style={figmaStyles.sectionSubtitle} numberOfLines={4}>
            {row.subtitle}
          </Text>
        ) : null}
      </View>
    );
  }
  if (row.type === 'quantity') {
    return <QuantityCard row={row} sm={sm} figmaStyles={figmaStyles} onBumpQty={onBumpQty} />;
  }
  return <ToggleCard row={row} sm={sm} figmaStyles={figmaStyles} onToggle={onToggle} />;
}

function QuantityCard({
  row,
  sm,
  figmaStyles,
  onBumpQty,
}: {
  row: TaskMenuQuantity;
  sm: ServiceMenuThemeTokens;
  figmaStyles: ReturnType<typeof createServiceMenuFigmaStyles>;
  onBumpQty: (key: string, delta: number) => void;
}) {
  return (
    <View style={figmaStyles.card}>
      <View style={figmaStyles.cardRow}>
        {row.imageUrl ? (
          <View style={figmaStyles.thumbShadow}>
            <Image source={{ uri: row.imageUrl }} style={figmaStyles.thumb} resizeMode="cover" accessibilityIgnoresInvertColors />
          </View>
        ) : (
          <View style={[figmaStyles.thumbShadow, figmaStyles.thumbPlaceholder]}>
            <MaterialCommunityIcons name="image-outline" size={22} color={sm.unitColor} />
          </View>
        )}
        <View style={figmaStyles.cardMid}>
          <Text style={figmaStyles.lineTitle} numberOfLines={2}>
            {row.title}
          </Text>
          {row.unitLabel ? (
            <Text style={figmaStyles.unitLabel} numberOfLines={2}>
              {row.unitLabel}
            </Text>
          ) : null}
          <Text style={figmaStyles.priceLine}>{`KES  ${Math.round(row.unitPrice)}`}</Text>
        </View>
        <View style={figmaStyles.stepper}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Decrease quantity"
            onPress={() => onBumpQty(row.key, -1)}
            style={({ pressed }) => [figmaStyles.stepMinus, pressed && { opacity: 0.85 }]}
          >
            <MaterialCommunityIcons name="minus" size={18} color={sm.stepperMinusIcon} />
          </Pressable>
          <Text style={figmaStyles.qtyNum}>{row.quantity}</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Increase quantity"
            onPress={() => onBumpQty(row.key, 1)}
            style={({ pressed }) => [figmaStyles.stepPlus, pressed && { opacity: 0.9 }]}
          >
            <MaterialCommunityIcons name="plus" size={18} color={sm.stepperPlusIcon} />
          </Pressable>
        </View>
      </View>
    </View>
  );
}

function ToggleCard({
  row,
  sm,
  figmaStyles,
  onToggle,
}: {
  row: TaskMenuToggle;
  sm: ServiceMenuThemeTokens;
  figmaStyles: ReturnType<typeof createServiceMenuFigmaStyles>;
  onToggle: (key: string, v: boolean) => void;
}) {
  return (
    <View style={figmaStyles.card}>
      <View style={figmaStyles.cardRow}>
        {row.imageUrl ? (
          <View style={figmaStyles.thumbShadow}>
            <Image source={{ uri: row.imageUrl }} style={figmaStyles.thumb} resizeMode="cover" accessibilityIgnoresInvertColors />
          </View>
        ) : (
          <View style={[figmaStyles.thumbShadow, figmaStyles.thumbPlaceholder]}>
            <MaterialCommunityIcons name="checkbox-intermediate" size={22} color={sm.unitColor} />
          </View>
        )}
        <View style={figmaStyles.cardMid}>
          <Text style={figmaStyles.lineTitle} numberOfLines={2}>
            {row.title}
          </Text>
          {row.unitLabel ? (
            <Text style={figmaStyles.unitLabel} numberOfLines={2}>
              {row.unitLabel}
            </Text>
          ) : null}
          <Text style={figmaStyles.priceLine}>+ KES {Math.round(row.unitPrice)} when on</Text>
        </View>
        <Pressable
          accessibilityRole="checkbox"
          accessibilityState={{ checked: row.checked }}
          onPress={() => onToggle(row.key, !row.checked)}
          style={({ pressed }) => [figmaStyles.checkboxOuter, row.checked && figmaStyles.checkboxOuterOn, pressed && { opacity: 0.9 }]}
        >
          {row.checked ? <MaterialCommunityIcons name="check" size={16} color={sm.stepperPlusIcon} /> : null}
        </Pressable>
      </View>
    </View>
  );
}

function makeStyles(sm: ServiceMenuThemeTokens) {
  return StyleSheet.create({
    root: { flex: 1 },
    centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
    bannerBlock: {
      width: '100%',
      alignSelf: 'stretch',
      marginTop: 0,
      marginHorizontal: 0,
      borderTopLeftRadius: 0,
      borderTopRightRadius: 0,
      borderBottomLeftRadius: sm.bannerCorner,
      borderBottomRightRadius: sm.bannerCorner,
      overflow: 'hidden',
    },
    bannerImg: {
      ...StyleSheet.absoluteFillObject,
      width: '100%',
      height: '100%',
    },
    bannerChrome: {
      position: 'absolute',
      left: 0,
      right: 0,
      top: 0,
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 12,
      paddingBottom: 8,
    },
    bannerToolbarTitle: {
      flex: 1,
      marginLeft: 8,
      marginRight: 8,
      fontSize: 18,
      fontWeight: '600',
      color: sm.bannerTextOnHero,
      textShadowColor: 'rgba(0,0,0,0.45)',
      textShadowOffset: { width: 0, height: 1 },
      textShadowRadius: 4,
    },
    floatingBack: {
      position: 'absolute',
      zIndex: 20,
    },
    backPill: {
      width: 44,
      height: 44,
      borderRadius: 22,
      backgroundColor: sm.backPillBg,
      alignItems: 'center',
      justifyContent: 'center',
    },
    bannerTitle: {
      position: 'absolute',
      left: 16,
      right: 16,
      bottom: 18,
      textAlign: 'center',
      fontSize: 32,
      fontWeight: '500',
      color: sm.bannerTextOnHero,
      textShadowColor: 'rgba(0,0,0,0.35)',
      textShadowOffset: { width: 0, height: 1 },
      textShadowRadius: 6,
    },
    list: { paddingHorizontal: 15, paddingTop: 12, gap: 10 },
    footer: {
      position: 'absolute',
      left: 0,
      right: 0,
      bottom: 0,
      paddingTop: 12,
    },
    footerParity: {
      paddingHorizontal: 15,
      backgroundColor: 'transparent',
      borderTopLeftRadius: 0,
      borderTopRightRadius: 0,
      shadowOpacity: 0,
      elevation: 0,
    },
    totalLine: {
      textAlign: 'center',
      fontSize: 15,
      fontWeight: '600',
    },
    placeholderWrap: { flex: 1, justifyContent: 'center', paddingVertical: 24 },
  });
}
