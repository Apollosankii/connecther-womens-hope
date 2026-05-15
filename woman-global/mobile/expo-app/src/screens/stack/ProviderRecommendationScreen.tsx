import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import * as Location from 'expo-location';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { GradientCtaButton } from '@/components/ui/GradientCtaButton';
import { IconButton } from '@/components/ui/IconButton';
import { Spinner } from '@/components/ui/Spinner';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { listProvidersForService, listProvidersForServiceNear } from '@/services/api/marketplace';
import { getMyUserProfile } from '@/services/api/profile';
import { Spacing } from '@/theme/spacing';
import type { ThemeColors } from '@/theme/types';
import type { Provider } from '@/types/models';
import { haversineMeters, providerRefForSort, sortProvidersByDistance } from '@/utils/providerGeoSort';

type Props = NativeStackScreenProps<AppStackParamList, 'ProviderRecommendation'>;

export function ProviderRecommendationScreen({ navigation, route }: Props) {
  const { serviceId, serviceName, prefillTotal, quoteLinesJson } = route.params;
  const { colors } = useTheme();
  const canvas = colors.softCanvas;
  const styles = useMemo(() => makeStyles(colors), [colors]);

  const meQ = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyUserProfile });

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [rawProviders, setRawProviders] = useState<Provider[]>([]);
  const [seekerLat, setSeekerLat] = useState<number | null>(null);
  const [seekerLng, setSeekerLng] = useState<number | null>(null);
  const [rejected, setRejected] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      let lat: number | null = null;
      let lng: number | null = null;
      try {
        const perm = await Location.requestForegroundPermissionsAsync();
        if (perm.status === 'granted') {
          const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
          lat = pos.coords.latitude;
          lng = pos.coords.longitude;
        }
        if (!cancelled) {
          setSeekerLat(lat);
          setSeekerLng(lng);
        }

        const raw =
          lat != null && lng != null
            ? await listProvidersForServiceNear(serviceId, lat, lng)
            : await listProvidersForService(serviceId);
        if (!cancelled) {
          setRawProviders(raw);
        }
      } catch (e) {
        if (!cancelled) {
          setLoadError(e instanceof Error ? e.message : 'Could not load providers.');
          setRawProviders([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [serviceId, refreshKey]);

  const sorted = useMemo(() => {
    const myId = meQ.data?.id;
    const filtered =
      myId != null && myId > 0 ? rawProviders.filter((p) => p.id !== myId) : rawProviders;
    return sortProvidersByDistance(filtered, seekerLat, seekerLng);
  }, [rawProviders, meQ.data?.id, seekerLat, seekerLng]);

  const noPeerProviders =
    !loading &&
    !loadError &&
    rawProviders.length > 0 &&
    sorted.length === 0 &&
    meQ.data?.id != null;

  const candidate = useMemo(() => {
    const rej = new Set(rejected);
    return sorted.find((p) => !rej.has(providerRefForSort(p))) ?? null;
  }, [sorted, rejected]);

  const exhausted = !loading && sorted.length > 0 && rejected.length >= sorted.length;
  const empty = !loading && sorted.length === 0;

  const displayName = useCallback((p: Provider) => {
    const n = `${p.first_name ?? ''} ${p.last_name ?? ''}`.trim();
    return n || p.user_name?.trim() || `Provider #${p.id}`;
  }, []);

  const onSkip = () => {
    if (!candidate) return;
    setRejected((prev) => [...prev, providerRefForSort(candidate)]);
  };

  const browseParams = {
    serviceId,
    serviceName,
    ...(prefillTotal != null && prefillTotal > 0 && quoteLinesJson ? { prefillTotal, quoteLinesJson } : {}),
  };

  const openRequestBooking = (p: Provider) => {
    const ref = (p.user_name ?? '').trim() || String(p.id);
    navigation.navigate('RequestBooking', {
      providerId: p.id,
      providerRef: ref,
      serviceId,
      serviceName,
      providerDisplayName: displayName(p),
      providerPic: p.pic ?? null,
      ...(prefillTotal != null && prefillTotal > 0 ? { prefillPrice: prefillTotal } : {}),
      ...(quoteLinesJson?.trim() ? { quoteLinesJson: quoteLinesJson.trim() } : {}),
    });
  };

  const openProfile = (p: Provider) => {
    const ref = (p.user_name ?? '').trim() || String(p.id);
    const areaBits = [p.area_name, p.county, p.country].filter(Boolean);
    navigation.navigate('ProviderProfile', {
      providerId: p.id,
      providerRef: ref,
      serviceId,
      serviceName,
      providerDisplayName: displayName(p),
      providerPic: p.pic ?? null,
      providerTitle: p.title ?? null,
      providerOccupation: p.occupation ?? null,
      providerWorkingHours: p.working_hours ?? null,
      providerAreaLabel: areaBits.length ? areaBits.join(' · ') : undefined,
      ...(prefillTotal != null && prefillTotal > 0 && quoteLinesJson ? { prefillTotal, quoteLinesJson } : {}),
    });
  };

  const distanceKm =
    candidate &&
    seekerLat != null &&
    seekerLng != null &&
    candidate.latitude != null &&
    candidate.longitude != null
      ? haversineMeters(seekerLat, seekerLng, candidate.latitude, candidate.longitude) / 1000
      : null;

  const subtitle = serviceName?.trim()
    ? `Next available for: ${serviceName.trim()}`
    : "We'll match you with an available provider for this service.";

  return (
    <Screen padded={false} safeAreaBackground={canvas}>
      <ScrollView contentContainerStyle={[styles.scroll, { backgroundColor: canvas }]}>
        <View style={styles.toolbar}>
          <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
            <MaterialCommunityIcons name="arrow-left" size={20} color={colors.accent} />
          </IconButton>
          <AppText variant="h3" style={styles.toolbarTitle}>
            Suggested provider
          </AppText>
          <View style={{ width: 48 }} />
        </View>

        <AppText variant="body" style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
          {subtitle}
        </AppText>
        {!loading && !loadError && seekerLat == null && sorted.length > 0 ? (
          <AppText variant="caption" style={{ textAlign: 'center', color: colors.onSurfaceVariant, marginBottom: 8 }}>
            Location off — providers are listed without distance sorting. Enable location to use your service search
            radius.
          </AppText>
        ) : null}

        {loading ? (
          <View style={styles.centerBlock}>
            <Spinner />
          </View>
        ) : loadError ? (
          <View style={styles.centerBlock}>
            <AppText variant="body">{loadError}</AppText>
            <AppButton
              variant="outline"
              onPress={() => {
                setRejected([]);
                setRefreshKey((k) => k + 1);
              }}
              style={{ marginTop: 12 }}
            >
              Retry
            </AppButton>
          </View>
        ) : empty || exhausted ? (
          <View style={styles.centerBlock}>
            <AppText variant="body" style={{ textAlign: 'center', color: colors.onBackground }}>
              {noPeerProviders
                ? 'There are no other providers to suggest for this service right now. You can still browse the full directory.'
                : exhausted
                  ? "You've skipped every provider we could suggest. Browse the full list or try again later."
                  : 'No providers are available for this service right now.'}
            </AppText>
            <AppButton variant="outline" onPress={() => navigation.navigate('CategoryUsers', browseParams)} style={{ marginTop: 16 }}>
              Browse all providers
            </AppButton>
          </View>
        ) : candidate ? (
          <>
            <AppCard padding="md" style={styles.heroCard}>
              {candidate.pic ? (
                <Image source={{ uri: candidate.pic }} style={styles.avatar} resizeMode="cover" accessibilityIgnoresInvertColors />
              ) : (
                <View style={[styles.avatar, styles.avatarPh]}>
                  <AppText variant="h2">{displayName(candidate).slice(0, 1).toUpperCase()}</AppText>
                </View>
              )}
              <AppText variant="h3" style={[styles.name, { color: colors.onBackground }]}>
                {displayName(candidate)}
              </AppText>
              {candidate.title?.trim() ? (
                <AppText variant="body" style={{ marginTop: 4, textAlign: 'center', color: colors.onSurfaceVariant }}>
                  {candidate.title.trim()}
                </AppText>
              ) : null}
              {(() => {
                const area = [candidate.area_name, candidate.county, candidate.country].filter(Boolean).join(' · ');
                return area ? (
                  <AppText variant="caption" style={{ marginTop: 6, textAlign: 'center', color: colors.onSurfaceVariant }}>
                    {area}
                  </AppText>
                ) : null;
              })()}
              {distanceKm != null ? (
                <AppText variant="body" style={{ marginTop: Spacing.sm, textAlign: 'center', color: colors.onBackground }}>
                  About {distanceKm.toFixed(1)} km away
                </AppText>
              ) : null}
            </AppCard>

            <AppCard padding="md" style={styles.actionsCard}>
              <GradientCtaButton title="Continue to booking" onPress={() => openRequestBooking(candidate)} />
              <Pressable onPress={onSkip} style={({ pressed }) => [styles.secondaryAction, pressed && { opacity: 0.85 }]}>
                <AppText variant="body" style={{ color: colors.onSurfaceVariant, textAlign: 'center', fontWeight: '600' }}>
                  Book another provider
                </AppText>
              </Pressable>
              <Pressable onPress={() => openProfile(candidate)} style={({ pressed }) => [styles.linkAction, pressed && { opacity: 0.85 }]}>
                <AppText variant="body" style={{ color: colors.accent, textAlign: 'center', fontWeight: '700' }}>
                  View provider profile
                </AppText>
              </Pressable>
            </AppCard>
          </>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    scroll: {
      paddingHorizontal: 16,
      paddingBottom: 32,
      paddingTop: 8,
    },
    toolbar: {
      flexDirection: 'row',
      alignItems: 'center',
      marginBottom: 8,
    },
    toolbarTitle: { flex: 1, textAlign: 'center', fontSize: 18 },
    subtitle: {
      textAlign: 'center',
      marginBottom: 16,
      paddingHorizontal: 8,
    },
    centerBlock: {
      marginTop: 32,
      alignItems: 'center',
      paddingHorizontal: 8,
    },
    heroCard: {
      borderRadius: 16,
      alignItems: 'center',
    },
    avatar: {
      width: 100,
      height: 100,
      borderRadius: 50,
      marginBottom: 4,
    },
    avatarPh: {
      backgroundColor: colors.surfaceVariant,
      alignItems: 'center',
      justifyContent: 'center',
    },
    name: {
      marginTop: 8,
      textAlign: 'center',
    },
    actionsCard: {
      marginTop: 16,
      borderRadius: 20,
      gap: 12,
    },
    secondaryAction: {
      paddingVertical: 14,
      borderRadius: 14,
      backgroundColor: colors.surfaceVariant,
      borderWidth: 1,
      borderColor: colors.outlineSoft,
    },
    linkAction: {
      paddingVertical: 12,
    },
  });
}
