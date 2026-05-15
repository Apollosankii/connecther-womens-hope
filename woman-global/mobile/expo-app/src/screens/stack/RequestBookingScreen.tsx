import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery } from '@tanstack/react-query';
import * as Location from 'expo-location';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BookingOrderSummary } from '@/components/booking/BookingOrderSummary';
import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { GradientCtaButton } from '@/components/ui/GradientCtaButton';
import { IconButton } from '@/components/ui/IconButton';
import { TextField } from '@/components/ui/TextField';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { createBookingRequest } from '@/services/api/booking';
import { getServiceById } from '@/services/api/services';
import { Metrics } from '@/theme/metrics';
import { Spacing } from '@/theme/spacing';
import type { ThemeColors } from '@/theme/types';
import { appendQuoteBreakdown, quoteTotal, type QuoteLine } from '@/utils/bookingQuote';
import { decodeQuoteLinesFromIntent } from '@/utils/bookingQuoteIntent';

type Props = NativeStackScreenProps<AppStackParamList, 'RequestBooking'>;

type SchemaRow = { key: string; label: string };

/** Parity with Android `strings.xml` + `SupabaseData.bookingRequestErrorMessage`. */
function bookingRequestErrorMessage(code: string | undefined | null): string {
  switch (code) {
    case 'supabase_not_configured':
      return 'Supabase is not configured on this build.';
    case 'not_authenticated':
    case 'auth_required':
      return 'Please sign in again to continue.';
    case 'provider_not_found_or_unavailable':
    case 'provider_not_found':
      return 'Provider is unavailable. Please choose another provider.';
    case 'provider_not_subscribed_to_service':
      return 'This provider is not subscribed to this service.';
    case 'provider_busy':
      return 'Provider is currently unavailable for booking.';
    case 'provider_offline':
      return 'This provider is offline and not accepting bookings right now.';
    case 'duplicate_booking_same_provider':
      return 'You already have an open booking or active job with this provider. Finish or cancel it before booking again.';
    case 'cannot_book_self':
      return 'You cannot book yourself.';
    case 'free_tier_exhausted':
      return 'You have used your free connects for trying the app. Subscribe to get more connects and keep booking service providers.';
    case 'connects_exhausted':
      return 'You have used all connects on your current plan for this period. Open Subscriptions to renew or choose a plan with more connects.';
    case 'insufficient_connects':
    case 'subscription_required':
      return 'Subscribe to get more connects and continue booking service providers.';
    case 'not_implemented':
      return 'Booking is temporarily unavailable. Please try again later.';
    case 'location_detail_required':
      return 'This service needs your building or unit details. Fill in the extra address fields and try again.';
    case 'service_not_found':
      return 'This service is no longer available. Go back and pick another service.';
    default:
      return 'Booking request failed. Please try again.';
  }
}

function isBookingConnectLimitError(code: string | undefined | null): boolean {
  return (
    code === 'free_tier_exhausted' ||
    code === 'connects_exhausted' ||
    code === 'insufficient_connects' ||
    code === 'subscription_required'
  );
}

function mapBookingErrorFromException(e: Error): string {
  let t: unknown = e;
  while (t instanceof Error) {
    const msg = (t.message ?? '').toLowerCase();
    if (msg.includes('jwt') || msg.includes('auth') || msg.includes('not authenticated')) return 'auth_required';
    if (msg.includes('provider_not_found_or_unavailable')) return 'provider_not_found_or_unavailable';
    if (msg.includes('provider_not_found')) return 'provider_not_found';
    if (msg.includes('duplicate_booking_same_provider')) return 'duplicate_booking_same_provider';
    if (msg.includes('cannot_book_self')) return 'cannot_book_self';
    if (msg.includes('provider_busy') || msg.includes('available_for_booking')) return 'provider_busy';
    if (msg.includes('free_tier_exhausted')) return 'free_tier_exhausted';
    if (msg.includes('connects_exhausted')) return 'connects_exhausted';
    if (msg.includes('insufficient_connects')) return 'insufficient_connects';
    if (msg.includes('location_detail_required')) return 'location_detail_required';
    if (msg.includes('service_not_found')) return 'service_not_found';
    if (msg.includes('subscription')) return 'subscription_required';
    if (msg.includes('permission denied') || msg.includes('rls')) return 'auth_required';
    t = t.cause;
  }
  return 'request_failed';
}

function parseLocationDetailSchema(raw: unknown): SchemaRow[] {
  let arr: unknown[] = [];
  if (raw == null) return [];
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw) as unknown;
      arr = Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  } else if (Array.isArray(raw)) {
    arr = raw;
  } else {
    return [];
  }
  const out: SchemaRow[] = [];
  for (let i = 0; i < arr.length; i++) {
    const o = arr[i];
    if (!o || typeof o !== 'object') continue;
    const obj = o as Record<string, unknown>;
    const key = String(obj.key ?? '').trim() || `field_${i}`;
    const label = String(obj.label ?? '').trim() || key;
    out.push({ key, label });
  }
  return out;
}

function hasNonEmptyLocationExtra(extra: Record<string, string>): boolean {
  return Object.values(extra).some((v) => String(v ?? '').trim().length > 0);
}

const multilineInput = { minHeight: 96 };

export function RequestBookingScreen({ navigation, route }: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const canvasBg = colors.softCanvas;
  const insets = useSafeAreaInsets();
  const { providerId, providerRef, serviceId, serviceName, providerDisplayName, providerPic, prefillPrice, quoteLinesJson } =
    route.params;
  const [price, setPrice] = useState(() => {
    if (prefillPrice != null && Number.isFinite(prefillPrice) && prefillPrice > 0) return String(Math.round(prefillPrice));
    const lines = decodeQuoteLinesFromIntent(quoteLinesJson);
    const t = quoteTotal(lines);
    if (t > 0) return String(Math.round(t));
    return '';
  });
  const [locationText, setLocationText] = useState('');
  const [message, setMessage] = useState('');
  const [latitude, setLatitude] = useState<number | null>(null);
  const [longitude, setLongitude] = useState<number | null>(null);
  const [gpsError, setGpsError] = useState<string | null>(null);
  const [extraValues, setExtraValues] = useState<Record<string, string>>({});
  const [quoteLines, setQuoteLines] = useState<QuoteLine[]>(() => decodeQuoteLinesFromIntent(quoteLinesJson));

  useEffect(() => {
    setQuoteLines(decodeQuoteLinesFromIntent(quoteLinesJson));
  }, [quoteLinesJson]);

  useEffect(() => {
    if (prefillPrice != null && Number.isFinite(prefillPrice) && prefillPrice > 0) {
      setPrice(String(Math.round(prefillPrice)));
      return;
    }
    const lines = decodeQuoteLinesFromIntent(quoteLinesJson);
    const t = quoteTotal(lines);
    if (t > 0) setPrice(String(Math.round(t)));
  }, [prefillPrice, quoteLinesJson]);
  const composedMessage = useMemo(() => appendQuoteBreakdown(message, quoteLines), [message, quoteLines]);

  const serviceQ = useQuery({
    queryKey: ['service', serviceId],
    queryFn: () => getServiceById(serviceId),
  });

  const svc = serviceQ.data ?? null;
  const requireDetail = svc?.require_location_detail === true;
  const schemaRows = useMemo(() => parseLocationDetailSchema(svc?.location_detail_schema ?? null), [svc]);

  const effectiveSchema = useMemo(() => {
    if (!requireDetail) return [];
    if (schemaRows.length > 0) return schemaRows;
    return [{ key: 'detail', label: 'Building, floor, unit, directions (required for this service)' }];
  }, [requireDetail, schemaRows]);

  useEffect(() => {
    if (!requireDetail) {
      setExtraValues({});
      return;
    }
    setExtraValues((prev) => {
      const next: Record<string, string> = {};
      for (const row of effectiveSchema) {
        next[row.key] = prev[row.key] ?? '';
      }
      return next;
    });
  }, [requireDetail, effectiveSchema]);

  const showBookingFailure = (code: string | undefined | null) => {
    const msg = bookingRequestErrorMessage(code);
    if (isBookingConnectLimitError(code)) {
      Alert.alert('Out of connects', msg, [
        { text: 'Cancel', style: 'cancel' },
        { text: 'View subscriptions', onPress: () => navigation.navigate('Subscriptions') },
      ]);
    } else {
      Alert.alert('Booking request failed', msg);
    }
  };

  const mutation = useMutation({
    mutationFn: async (vars: {
      price: number;
      locationText: string;
      message: string;
      lat: number | null;
      lng: number | null;
      locationExtra: Record<string, string>;
    }) =>
      createBookingRequest({
        providerRef,
        serviceId,
        proposedPrice: vars.price,
        locationText: vars.locationText,
        message: vars.message,
        latitude: vars.lat ?? undefined,
        longitude: vars.lng ?? undefined,
        locationExtra: vars.locationExtra,
      }),
    onSuccess: (res) => {
      if (res.ok) {
        const display =
          (providerDisplayName ?? '').trim() ||
          (providerRef ?? '').trim() ||
          `Provider #${providerId}`;
        navigation.replace('ConnectionSuccess', {
          providerId,
          providerRef,
          serviceId,
          serviceName,
          providerDisplayName: display,
          providerPic: providerPic ?? null,
          postBooking: true,
          ...(prefillPrice != null && prefillPrice > 0 ? { prefillPrice } : {}),
          ...(quoteLinesJson?.trim() ? { quoteLinesJson: quoteLinesJson.trim() } : {}),
        });
      } else {
        showBookingFailure(res.errorCode);
      }
    },
    onError: (e) => {
      const err = e instanceof Error ? e : new Error(String(e));
      showBookingFailure(mapBookingErrorFromException(err));
    },
  });

  const useMyLocation = async () => {
    setGpsError(null);
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Location', 'Permission is needed to attach GPS coordinates to this request.');
      return;
    }
    try {
      const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setLatitude(pos.coords.latitude);
      setLongitude(pos.coords.longitude);
      setGpsError(null);
    } catch {
      setLatitude(null);
      setLongitude(null);
      setGpsError('Could not get GPS location');
    }
  };

  const submit = () => {
    const ref = (providerRef ?? '').trim();
    if (!ref || !Number.isFinite(serviceId)) {
      Alert.alert('Booking request', 'Provider/service missing');
      return;
    }

    const p = Number(price || '0');
    if (!Number.isFinite(p) || p <= 0) {
      Alert.alert('Booking request', 'Enter a valid price');
      return;
    }

    const trimmedExtra: Record<string, string> = {};
    for (const row of effectiveSchema) {
      const v = (extraValues[row.key] ?? '').trim();
      if (v) trimmedExtra[row.key] = v;
    }

    if (requireDetail && !hasNonEmptyLocationExtra(trimmedExtra)) {
      Alert.alert('Booking request', 'Add the required building or unit details before sending this request.');
      return;
    }

    mutation.mutate({
      price: p,
      locationText,
      message: composedMessage,
      lat: latitude,
      lng: longitude,
      locationExtra: requireDetail ? trimmedExtra : {},
    });
  };

  const totalLabel = useMemo(() => {
    const p = Number(price || '0');
    if (!Number.isFinite(p) || p <= 0) return 'Total: KES —';
    return `Total: KES ${Math.round(p)}`;
  }, [price]);

  const subtitleLine = useMemo(() => {
    const serviceIdStr = String(serviceId ?? '');
    const svcLine = (serviceName ?? '').trim() || `Service #${serviceIdStr || '—'}`;
    const who = (providerDisplayName ?? '').trim() || (providerRef ?? '').trim() || 'this user';
    return `${svcLine} · ${who}`;
  }, [serviceId, serviceName, providerDisplayName, providerRef]);

  const hasQuote = quoteLines.length > 0;
  const quoteComputedTotal = useMemo(() => quoteTotal(quoteLines), [quoteLines]);
  const priceMismatch =
    hasQuote &&
    quoteComputedTotal > 0 &&
    (() => {
      const p = Number(price || '0');
      return Number.isFinite(p) && Math.round(p) !== Math.round(quoteComputedTotal);
    })();
  const busy = mutation.isPending || serviceQ.isLoading;

  return (
    <Screen padded={false} safeAreaBackground={canvasBg}>
      <View style={[styles.root, { backgroundColor: canvasBg }]}>
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={[styles.header, { borderBottomColor: colors.outlineSoft }]}>
            <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
              <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
            </IconButton>
            <View style={styles.headerTitleBlock}>
              <AppText variant="h3" style={[styles.headerTitle, { color: colors.onBackground }]}>
                Order process
              </AppText>
              <AppText variant="caption" style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>
                Confirm details before you send the request
              </AppText>
            </View>
            <View style={styles.headerSpacer} />
          </View>

          <View style={styles.content}>
            <AppCard variant="elevated" padding="none" style={styles.orderCard}>
              <View style={styles.cardInner}>
                <View style={styles.cardHeaderRow}>
                  <View style={styles.cardHeaderText}>
                    <AppText variant="bodyStrong" style={[styles.orderDetailsTitle, { color: colors.onBackground }]}>
                      Order details
                    </AppText>
                    <AppText variant="caption" style={[styles.orderMeta, { color: colors.onSurfaceVariant }]}>
                      Fields marked required must be complete to continue
                    </AppText>
                  </View>
                  {serviceQ.isLoading ? (
                    <ActivityIndicator size="small" color={colors.primary} accessibilityLabel="Loading service" />
                  ) : null}
                </View>

                <View style={[styles.subtitleChip, { backgroundColor: colors.surfaceVariant, borderColor: colors.outlineSoft }]}>
                  <MaterialCommunityIcons name="briefcase-outline" size={16} color={colors.onSurfaceVariant} style={styles.subtitleIcon} />
                  <AppText variant="caption" style={[styles.subtitleChipText, { color: colors.onSurface }]}>
                    {subtitleLine}
                  </AppText>
                </View>

                {hasQuote ? (
                  <>
                    <BookingOrderSummary lines={quoteLines} />
                    <AppText variant="caption" style={[styles.quoteCaption, { color: colors.onSurfaceVariant }]}>
                      These selections are also included in the message sent to the provider.
                    </AppText>
                  </>
                ) : null}

                <View style={[styles.divider, { backgroundColor: colors.outlineSoft }]} />

                <AppText variant="caption" style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>
                  PRICING
                </AppText>
                <TextField
                  label="Proposed price (KES)"
                  keyboardType="decimal-pad"
                  placeholder="e.g. 2500"
                  value={price}
                  onChangeText={setPrice}
                />
                {priceMismatch ? (
                  <AppText variant="caption" style={[styles.priceHint, { color: colors.accent }]}>
                    Menu total is KES {Math.round(quoteComputedTotal)}. Adjust the price if you intend a different amount.
                  </AppText>
                ) : null}

                <View style={[styles.divider, { backgroundColor: colors.outlineSoft }]} />

                <View style={styles.locationSection}>
                  <AppText variant="caption" style={[styles.sectionLabelInline, { color: colors.onSurfaceVariant }]}>
                    LOCATION & ACCESS
                  </AppText>
                  <TextField
                    label="Location / address notes"
                    placeholder="Area, gate, building, floor, landmarks"
                    value={locationText}
                    onChangeText={setLocationText}
                    multiline
                    style={multilineInput}
                  />
                  <AppButton
                    variant="outline"
                    size="content"
                    onPress={() => void useMyLocation()}
                    style={styles.gpsButton}
                  >
                    <View style={styles.gpsButtonInner}>
                      <MaterialCommunityIcons name="crosshairs-gps" size={18} color={colors.primary} />
                      <AppText variant="bodyStrong" style={[styles.gpsButtonLabel, { color: colors.primary }]}>
                        Use current GPS location
                      </AppText>
                    </View>
                  </AppButton>
                  {latitude != null && longitude != null ? (
                    <View style={[styles.gpsFeedback, { backgroundColor: colors.surfaceVariant }]}>
                      <MaterialCommunityIcons name="map-marker-check" size={18} color={colors.bookingStatus.acceptedText} />
                      <AppText variant="caption" style={[styles.gpsFeedbackText, { color: colors.onSurface }]}>
                        GPS attached · {latitude.toFixed(5)}, {longitude.toFixed(5)}
                      </AppText>
                    </View>
                  ) : null}
                  {gpsError ? (
                    <View style={[styles.gpsFeedback, styles.gpsFeedbackError, { borderColor: colors.outlineSoft }]}>
                      <MaterialCommunityIcons name="map-marker-alert" size={18} color={colors.bookingStatus.declinedText} />
                      <AppText variant="caption" style={[styles.gpsFeedbackText, { color: colors.bookingStatus.declinedText }]}>
                        {gpsError}
                      </AppText>
                    </View>
                  ) : null}
                </View>

                {effectiveSchema.length > 0 ? (
                  <>
                    <View style={[styles.divider, { backgroundColor: colors.outlineSoft }]} />
                    <AppText variant="caption" style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>
                      EXTRA DETAILS (REQUIRED)
                    </AppText>
                    <AppText variant="caption" style={[styles.sectionHint, { color: colors.onSurfaceVariant }]}>
                      This provider needs precise access information for this service.
                    </AppText>
                    {effectiveSchema.map((row) => (
                      <TextField
                        key={row.key}
                        label={row.label}
                        placeholder={row.label}
                        value={extraValues[row.key] ?? ''}
                        onChangeText={(t) => setExtraValues((prev) => ({ ...prev, [row.key]: t }))}
                      />
                    ))}
                  </>
                ) : null}

                <View style={[styles.divider, { backgroundColor: colors.outlineSoft }]} />

                <AppText variant="caption" style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>
                  NOTE TO PROVIDER
                </AppText>
                <TextField
                  label="Optional message"
                  placeholder="Timing, access, pets, parking, or anything else helpful"
                  value={message}
                  onChangeText={setMessage}
                  multiline
                  style={multilineInput}
                />

                <View style={[styles.helpCard, { backgroundColor: colors.surfaceVariant, borderColor: colors.outlineSoft }]}>
                  <MaterialCommunityIcons name="map-search-outline" size={20} color={colors.onSurfaceVariant} />
                  <AppText variant="caption" style={[styles.helpCardText, { color: colors.onSurfaceVariant }]}>
                    Use GPS for coordinates, describe the address in the field above, and double-check in Maps before you send.
                  </AppText>
                </View>
              </View>
            </AppCard>

            <AppText variant="caption" style={[styles.paymentFootnote, { color: colors.onSurfaceVariant }]}>
              Payment is handled outside the app.
            </AppText>
          </View>
        </ScrollView>

        <View style={[styles.bottomDock, { paddingBottom: Math.max(12, insets.bottom) }]}>
          <View style={[styles.bottomHairline, { backgroundColor: colors.outlineSoft }]} />
          <AppCard style={styles.bottomCard}>
            <AppText variant="bodyStrong" style={[styles.totalLine, { color: colors.onBackground }]}>
              {totalLabel}
            </AppText>
            <AppText variant="caption" style={[styles.bottomSubline, { color: colors.onSurfaceVariant }]}>
              Tap below to send your request to the provider
            </AppText>
            <GradientCtaButton
              title="Make request"
              loading={busy}
              disabled={busy}
              onPress={submit}
              style={styles.cta}
            />
          </AppCard>
        </View>
      </View>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    root: {
      flex: 1,
    },
    scrollContent: {
      paddingBottom: 152,
    },
    header: {
      paddingHorizontal: Metrics.screenPaddingX,
      paddingTop: Spacing.sm,
      paddingBottom: Spacing.md,
      flexDirection: 'row',
      alignItems: 'flex-start',
      borderBottomWidth: StyleSheet.hairlineWidth,
    },
    headerTitleBlock: {
      flex: 1,
      alignItems: 'center',
      paddingHorizontal: Spacing.xs,
    },
    headerTitle: {
      fontSize: 20,
      fontWeight: '600',
      letterSpacing: -0.3,
      textAlign: 'center',
    },
    headerSubtitle: {
      marginTop: 4,
      textAlign: 'center',
      fontSize: 12,
      lineHeight: 16,
      maxWidth: 260,
    },
    headerSpacer: {
      width: Metrics.iconButtonSize,
    },
    content: {
      paddingHorizontal: Metrics.screenPaddingX,
      paddingTop: Spacing.lg,
      paddingBottom: Spacing.lg,
    },
    orderCard: {
      borderRadius: 22,
      borderWidth: StyleSheet.hairlineWidth,
      borderColor: colors.outlineSoft,
      shadowColor: 'rgba(90,108,234,0.22)',
      shadowOpacity: 0.18,
      shadowRadius: 20,
      shadowOffset: { width: 0, height: 10 },
      elevation: 5,
      backgroundColor: colors.surface,
      overflow: 'hidden',
    },
    cardInner: {
      padding: Metrics.cardPadding,
      gap: Spacing.md,
    },
    cardHeaderRow: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      gap: Spacing.md,
    },
    cardHeaderText: {
      flex: 1,
      minWidth: 0,
    },
    orderDetailsTitle: {
      fontSize: 18,
      letterSpacing: -0.2,
    },
    orderMeta: {
      marginTop: 4,
      fontSize: 12,
      lineHeight: 16,
    },
    subtitleChip: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingVertical: 10,
      paddingHorizontal: 12,
      borderRadius: Metrics.radiusSm,
      borderWidth: StyleSheet.hairlineWidth,
      alignSelf: 'stretch',
    },
    subtitleIcon: {
      marginRight: 8,
    },
    subtitleChipText: {
      flex: 1,
      fontSize: 13,
      lineHeight: 18,
      fontWeight: '500',
    },
    infoBanner: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: 10,
      paddingVertical: 10,
      paddingHorizontal: 12,
      borderRadius: Metrics.radiusSm,
      borderWidth: StyleSheet.hairlineWidth,
    },
    infoBannerText: {
      flex: 1,
      fontSize: 12,
      lineHeight: 17,
    },
    quoteCaption: {
      marginTop: -4,
      fontSize: 12,
      lineHeight: 17,
    },
    priceHint: {
      marginTop: 4,
      fontSize: 12,
      lineHeight: 17,
    },
    divider: {
      height: StyleSheet.hairlineWidth,
      alignSelf: 'stretch',
      marginVertical: 2,
      opacity: 0.85,
    },
    sectionLabel: {
      fontSize: 11,
      fontWeight: '700',
      letterSpacing: 1.1,
      marginBottom: -4,
    },
    sectionHint: {
      fontSize: 12,
      lineHeight: 17,
      marginTop: -4,
    },
    /** Kotlin parity: address field, then full-width GPS row (12dp below field). */
    locationSection: {
      alignSelf: 'stretch',
      gap: Spacing.md,
    },
    sectionLabelInline: {
      fontSize: 11,
      fontWeight: '700',
      letterSpacing: 1.1,
      marginBottom: 0,
    },
    gpsButton: {
      alignSelf: 'stretch',
      width: '100%',
    },
    gpsButtonInner: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 10,
    },
    gpsButtonLabel: {
      flexShrink: 1,
      fontSize: 15,
      lineHeight: 20,
    },
    gpsFeedback: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 10,
      paddingVertical: 10,
      paddingHorizontal: 12,
      borderRadius: Metrics.radiusSm,
    },
    gpsFeedbackError: {
      backgroundColor: 'transparent',
      borderWidth: StyleSheet.hairlineWidth,
    },
    gpsFeedbackText: {
      flex: 1,
      fontSize: 12,
      lineHeight: 17,
      fontWeight: '500',
    },
    helpCard: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: 12,
      paddingVertical: 12,
      paddingHorizontal: 12,
      borderRadius: Metrics.radiusSm,
      borderWidth: StyleSheet.hairlineWidth,
      marginTop: 4,
    },
    helpCardText: {
      flex: 1,
      fontSize: 12,
      lineHeight: 18,
    },
    paymentFootnote: {
      textAlign: 'center',
      fontSize: 12,
      lineHeight: 17,
      marginTop: Spacing.lg,
      paddingHorizontal: Spacing.md,
    },
    bottomDock: {
      position: 'absolute',
      left: 0,
      right: 0,
      bottom: 0,
      paddingHorizontal: 0,
    },
    bottomHairline: {
      height: StyleSheet.hairlineWidth,
      marginHorizontal: Metrics.screenPaddingX,
      opacity: 0.9,
    },
    bottomCard: {
      borderTopLeftRadius: 22,
      borderTopRightRadius: 22,
      borderBottomLeftRadius: 0,
      borderBottomRightRadius: 0,
      paddingHorizontal: Metrics.cardPadding,
      paddingTop: Spacing.lg,
      paddingBottom: Spacing.lg,
      backgroundColor: colors.surface,
      elevation: 14,
      shadowColor: '#000',
      shadowOpacity: 0.12,
      shadowRadius: 16,
      shadowOffset: { width: 0, height: -6 },
    },
    totalLine: {
      fontSize: 18,
      fontWeight: '700',
      letterSpacing: -0.3,
      textAlign: 'center',
    },
    bottomSubline: {
      textAlign: 'center',
      fontSize: 12,
      lineHeight: 17,
      marginTop: 6,
    },
    cta: {
      marginTop: 14,
    },
  });
}
