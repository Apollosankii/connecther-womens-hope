import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Image, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { getSignedUrlForProviderPortfolioDocument, listProviderPortfolioDocuments } from '@/services/api/documents';
import { getProviderUserById, listProvidersForService, startConversationWithProvider } from '@/services/api/marketplace';
import { providerIsBookable } from '@/services/api/jobs';
import { getMyUserProfile } from '@/services/api/profile';
import { ProviderReviewsSection } from '@/components/profile/ProviderReviewsSection';
import { Spacing } from '@/theme/spacing';
import type { ThemeColors } from '@/theme/types';

type Props = NativeStackScreenProps<AppStackParamList, 'ProviderProfile'>;

function notSpecified(value?: string | null) {
  const v = (value ?? '').trim();
  return v.length ? v : 'Not specified';
}

export function ProviderProfileScreen({ navigation, route }: Props) {
  const { colors } = useTheme();
  const canvas = colors.softCanvas;
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const {
    providerId,
    providerRef,
    serviceId,
    serviceName,
    providerDisplayName,
    providerPic,
    providerTitle,
    providerOccupation,
    providerWorkingHours,
    providerAreaLabel,
    prefillTotal,
    quoteLinesJson,
  } = route.params;

  const hasQuotePrefill =
    prefillTotal != null &&
    prefillTotal > 0 &&
    typeof quoteLinesJson === 'string' &&
    quoteLinesJson.trim().length > 0;

  const meQ = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyUserProfile });
  const isOwnProfile = meQ.data?.id === providerId;

  const providersQ = useQuery({
    queryKey: ['services', serviceId, 'providers'],
    queryFn: () => listProvidersForService(serviceId),
    enabled: serviceId > 0,
  });

  const providerUserQ = useQuery({
    queryKey: ['provider', 'user', providerId],
    queryFn: () => getProviderUserById(providerId),
    enabled: providerId > 0 && !isOwnProfile,
  });

  const row = providersQ.data?.find((p) => p.id === providerId) ?? providerUserQ.data ?? null;
  const me = meQ.data;

  const displayName = useMemo(() => {
    if (providerDisplayName?.trim()) return providerDisplayName.trim();
    if (row) {
      const n = `${row.first_name ?? ''} ${row.last_name ?? ''}`.trim();
      return n || row.user_name || `Provider #${providerId}`;
    }
    if (isOwnProfile && me) {
      const n = `${me.first_name ?? ''} ${me.last_name ?? ''}`.trim();
      return n || me.email || `Provider #${providerId}`;
    }
    return `Provider #${providerId}`;
  }, [providerDisplayName, row, isOwnProfile, me, providerId]);

  const pic = providerPic ?? row?.pic ?? (isOwnProfile ? (me?.prof_pic ?? me?.pic ?? null) : null);
  const titleStr = providerTitle ?? row?.title ?? (isOwnProfile ? me?.title : null) ?? null;
  const occupationStr = providerOccupation ?? row?.occupation ?? (isOwnProfile ? me?.occupation : null) ?? null;
  const hoursStr = providerWorkingHours ?? row?.working_hours ?? (isOwnProfile ? me?.working_hours : null) ?? null;
  const areaStr =
    providerAreaLabel ??
    (row
      ? [row.area_name, row.county, row.country].filter(Boolean).join(', ') || null
      : isOwnProfile && me
        ? [me.area_name, me.county, me.country].filter(Boolean).join(', ') || me.phone
        : null);

  const experienceText = (occupationStr ?? '').trim()
    ? occupationStr
    : 'This provider has not added professional experience details yet.';

  const docsQ = useQuery({
    queryKey: ['provider-documents', providerId],
    queryFn: () => listProviderPortfolioDocuments(providerId),
    enabled: providerId > 0,
  });

  const bookableQ = useQuery({
    queryKey: ['provider', 'bookable', providerId],
    queryFn: () => providerIsBookable(providerId),
    enabled: !isOwnProfile && providerId > 0 && serviceId > 0,
  });

  const canBook = bookableQ.data !== false;

  const [openingDocId, setOpeningDocId] = useState<number | null>(null);

  const startChat = useMutation({
    mutationFn: () => startConversationWithProvider(providerRef, serviceId),
    onSuccess: (outcome) => {
      if (outcome.chatCode) {
        navigation.navigate('Chat', {
          chatCode: outcome.chatCode,
          title: displayName,
          peerPic: pic ?? undefined,
        });
      } else {
        Alert.alert('Chat', outcome.errorCode ? `Could not open chat (${outcome.errorCode}).` : 'Could not open chat.');
      }
    },
    onError: (e) => Alert.alert('Chat failed', e instanceof Error ? e.message : 'Unknown error'),
  });

  async function openPortfolioDocument(docId: number, docTitle: string) {
    if (openingDocId != null) return;
    try {
      setOpeningDocId(docId);
      const signed = await getSignedUrlForProviderPortfolioDocument(docId, providerId);
      if (signed) {
        navigation.navigate('ProviderDocuments', { url: signed, title: docTitle });
      } else {
        Alert.alert('Document', 'Could not open this document. Try again later.');
      }
    } finally {
      setOpeningDocId(null);
    }
  }

  function navigateToBook() {
    if (hasQuotePrefill) {
      navigation.navigate('RequestBooking', {
        providerId,
        providerRef,
        serviceId,
        serviceName,
        providerDisplayName: displayName,
        providerPic: pic,
        prefillPrice: prefillTotal,
        quoteLinesJson,
      });
    } else {
      navigation.navigate('ServiceMenu', {
        serviceId,
        serviceName,
        providerRef,
        providerId,
        providerDisplayName: displayName,
        providerPic: pic,
      });
    }
  }

  return (
    <Screen padded={false} scroll safeAreaBackground={canvas} scrollContentInsetBottom={24}>
      <View style={styles.root}>
        <View style={styles.header}>
          <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
            <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
          </IconButton>
          <View style={{ flex: 1 }}>
            <AppText variant="h3">{!isOwnProfile && serviceId > 0 ? 'Provider' : 'Profile'}</AppText>
            {serviceId > 0 && (serviceName ?? '').trim() ? (
              <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
                {`Browsing for ${serviceName.trim()}`}
              </AppText>
            ) : (
              <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
                Your public provider profile
              </AppText>
            )}
          </View>
        </View>

        <View style={styles.content}>
          <AppCard style={styles.card}>
            <View style={styles.heroRow}>
              <View style={styles.avatarWrap}>
                {pic ? (
                  <Image source={{ uri: String(pic) }} style={styles.avatar} accessibilityIgnoresInvertColors />
                ) : (
                  <View style={[styles.avatar, styles.avatarPlaceholder]}>
                    <MaterialCommunityIcons name="account" size={40} color={colors.onSurfaceVariant} />
                  </View>
                )}
              </View>
              <View style={{ flex: 1, minWidth: 0 }}>
                <View style={styles.nameRow}>
                  <AppText variant="rowTitle" numberOfLines={2} style={{ flex: 1 }}>
                    {displayName}
                  </AppText>
                  {row?.wh_badge ? (
                    <View style={styles.badge}>
                      <AppText style={styles.badgeText}>WH</AppText>
                    </View>
                  ) : null}
                </View>
              </View>
            </View>
          </AppCard>

          <Section title="About">
            <DetailRow label="Professional title" value={notSpecified(titleStr)} />
            <DetailRow label="Service category" value={notSpecified(serviceName)} />
            <DetailRow label="Location" value={notSpecified(areaStr)} />
            <DetailRow label="Experience" value={experienceText} multiline />
            {hoursStr?.trim() ? <DetailRow label="Working hours" value={hoursStr.trim()} multiline /> : null}
          </Section>

          {!isOwnProfile && serviceId > 0 ? (
            <View style={styles.actions}>
              {!canBook && !bookableQ.isLoading ? (
                <AppText variant="caption" style={{ marginBottom: 10, color: colors.onSurfaceVariant, textAlign: 'center' }}>
                  This provider is offline or finishing an active job and is not available for new bookings.
                </AppText>
              ) : null}
              <AppButton
                variant="outline"
                loading={startChat.isPending}
                disabled={startChat.isPending}
                onPress={() => startChat.mutate()}
              >
                Message provider
              </AppButton>
              <AppButton disabled={!canBook || bookableQ.isLoading} onPress={navigateToBook}>
                Book now
              </AppButton>
            </View>
          ) : null}

          {providerId > 0 ? <ProviderReviewsSection providerId={providerId} /> : null}

          <Section title="Credentials & portfolio">
            {docsQ.isLoading ? (
              <AppText variant="body" style={{ color: colors.onSurface }}>
                Loading documents…
              </AppText>
            ) : docsQ.isError ? (
              <View style={{ gap: 8 }}>
                <AppText variant="body" style={{ color: colors.onSurface }}>
                  Could not load documents. Try again later.
                </AppText>
                <AppButton variant="outline" onPress={() => void docsQ.refetch()}>
                  Retry
                </AppButton>
              </View>
            ) : docsQ.data && docsQ.data.length ? (
              <View style={{ gap: 10 }}>
                {docsQ.data.map((d) => (
                  <Pressable
                    key={d.id}
                    accessibilityRole="button"
                    onPress={() => void openPortfolioDocument(d.id, d.docTypeName)}
                    disabled={openingDocId != null}
                  >
                    <AppCard padding="sm" style={{ borderRadius: 14 }}>
                      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                        <View style={{ flex: 1, minWidth: 0 }}>
                          <AppText variant="rowTitle">{d.docTypeName}</AppText>
                          <AppText variant="caption" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
                            {d.fileLabel}
                          </AppText>
                          <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
                            {d.verified ? 'Verified' : 'Not verified'}
                          </AppText>
                        </View>
                        {openingDocId === d.id ? (
                          <ActivityIndicator size="small" color={colors.primary} accessibilityLabel="Opening document" />
                        ) : (
                          <MaterialCommunityIcons name="chevron-right" size={22} color={colors.onSurfaceVariant} />
                        )}
                      </View>
                    </AppCard>
                  </Pressable>
                ))}
              </View>
            ) : (
              <AppText variant="body" style={{ color: colors.onSurface }}>
                No documents uploaded.
              </AppText>
            )}
          </Section>
        </View>
      </View>
    </Screen>
  );
}

function DetailRow({
  label,
  value,
  multiline = false,
}: {
  label: string;
  value: string;
  multiline?: boolean;
}) {
  const { colors } = useTheme();
  return (
    <View style={{ marginTop: 12 }}>
      <AppText variant="caption" style={{ color: colors.onSurfaceVariant, fontWeight: '600' }}>
        {label}
      </AppText>
      <AppText
        variant="body"
        style={{ marginTop: 4, color: colors.onSurface, lineHeight: multiline ? 22 : undefined }}
      >
        {value}
      </AppText>
    </View>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeSectionStyles(colors), [colors]);
  return (
    <View style={styles.section}>
      <AppText variant="sectionTitle">{title}</AppText>
      <View style={{ height: 8 }} />
      {children}
    </View>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    root: {
      backgroundColor: colors.background,
    },
    header: {
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
    },
    content: {
      paddingHorizontal: 16,
      paddingBottom: 32,
      gap: Spacing.lg,
    },
    card: {
      borderRadius: 16,
    },
    heroRow: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: 14,
    },
    nameRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
    },
    avatarWrap: {
      borderRadius: 16,
      overflow: 'hidden',
    },
    avatar: {
      width: 88,
      height: 88,
      borderRadius: 16,
    },
    avatarPlaceholder: {
      backgroundColor: colors.surfaceVariant,
      alignItems: 'center',
      justifyContent: 'center',
    },
    badge: {
      paddingHorizontal: 8,
      paddingVertical: 2,
      borderRadius: 8,
      backgroundColor: colors.outlineSoft,
    },
    badgeText: {
      fontSize: 11,
      fontWeight: '800',
      color: colors.primary,
    },
    actions: {
      gap: 10,
    },
  });
}

function makeSectionStyles(colors: ThemeColors) {
  return StyleSheet.create({
    section: {
      backgroundColor: colors.surface,
      borderColor: colors.outline,
      borderWidth: 1,
      borderRadius: 16,
      padding: 16,
    },
  });
}
