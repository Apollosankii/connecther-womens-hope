import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, StyleSheet, Switch, View } from 'react-native';

import { ProviderPublicPreviewCard } from '@/components/profile/ProviderPublicPreviewCard';
import { ProviderReviewsSection } from '@/components/profile/ProviderReviewsSection';
import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { Spinner } from '@/components/ui/Spinner';
import { TextField } from '@/components/ui/TextField';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { getSignedUrlForProviderPortfolioDocument, listProviderPortfolioDocuments } from '@/services/api/documents';
import { myProviderHasActiveJob } from '@/services/api/jobs';
import { getMyUserProfile, updateMyProviderProfile } from '@/services/api/profile';
import { getProfilePicVersion } from '@/services/supabase/tokenStore';
import { Spacing } from '@/theme/spacing';
import type { ThemeColors } from '@/theme/types';
import { getUserFriendlyError } from '@/utils/userFriendlyError';

type Nav = NativeStackNavigationProp<AppStackParamList>;

export function ManageProviderProfileScreen() {
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);

  const profileQ = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyUserProfile });
  const picVerQ = useQuery({ queryKey: ['profile', 'picVersion'], queryFn: getProfilePicVersion });
  const busyQ = useQuery({ queryKey: ['provider', 'active-job'], queryFn: myProviderHasActiveJob });

  const me = profileQ.data;
  const providerId = me?.id ?? 0;

  const docsQ = useQuery({
    queryKey: ['provider-documents', providerId],
    queryFn: () => listProviderPortfolioDocuments(providerId),
    enabled: providerId > 0,
  });

  const [headline, setHeadline] = useState('');
  const [experience, setExperience] = useState('');
  const [workingHours, setWorkingHours] = useState('');
  const [online, setOnline] = useState(true);
  const [openingDocId, setOpeningDocId] = useState<number | null>(null);

  useEffect(() => {
    if (!me) return;
    setHeadline((me.title ?? '').trim());
    setExperience((me.occupation ?? '').trim());
    setWorkingHours((me.working_hours ?? '').trim());
    setOnline(me.available_for_booking !== false);
  }, [me]);

  const rawPhotoUrl = me?.prof_pic ?? me?.pic ?? null;
  const photoUrl =
    rawPhotoUrl && picVerQ.data
      ? `${String(rawPhotoUrl)}${String(rawPhotoUrl).includes('?') ? '&' : '?'}v=${picVerQ.data}`
      : rawPhotoUrl;

  const saveMut = useMutation({
    mutationFn: () =>
      updateMyProviderProfile({
        headline,
        experience,
        workingHours,
        availableForBooking: online,
      }),
    onSuccess: async (ok) => {
      if (ok) {
        await queryClient.invalidateQueries({ queryKey: ['profile', 'me'] });
        Alert.alert('Saved', 'Your provider profile was updated.');
        navigation.goBack();
      } else {
        Alert.alert('Save failed', 'Could not save to the server. Check your connection and try again.');
      }
    },
    onError: (e) => {
      Alert.alert('Save failed', getUserFriendlyError(e, 'Could not save your provider profile.'));
    },
  });

  async function openPortfolioDocument(docId: number, docTitle: string) {
    if (!providerId || openingDocId != null) return;
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

  const hasActiveJob = busyQ.data === true;
  const isSuspended = Boolean(me?.provider_suspended);
  const loading = profileQ.isLoading;

  return (
    <Screen padded={false} scroll safeAreaBackground={colors.softCanvas} scrollContentInsetBottom={32}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <View style={{ flex: 1 }}>
          <AppText variant="h3">Provider profile</AppText>
          <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
            Availability, public details, and reviews
          </AppText>
        </View>
      </View>

      <View style={styles.body}>
        <AppText variant="bodyStrong" style={{ color: colors.onBackground }}>
          How clients find you
        </AppText>
        <AppText variant="body" style={{ marginTop: 6, color: colors.onSurfaceVariant, lineHeight: 22 }}>
          This information appears in search and on your public profile. Keep it accurate and professional.
        </AppText>

        {loading ? (
          <View style={{ marginTop: Spacing.lg }}>
            <Spinner />
          </View>
        ) : (
          <>
            {isSuspended ? (
              <AppCard
                style={[styles.banner, { marginTop: Spacing.md, borderColor: '#C62828', borderWidth: 1 }]}
                padding="md"
              >
                <View style={styles.bannerRow}>
                  <MaterialCommunityIcons name="account-cancel-outline" size={22} color="#C62828" />
                  <AppText variant="body" style={styles.bannerText}>
                    Your provider account has been suspended. You cannot receive new bookings. Contact support if
                    you believe this is a mistake.
                  </AppText>
                </View>
              </AppCard>
            ) : null}

            {hasActiveJob && !isSuspended ? (
              <AppCard style={[styles.banner, { marginTop: Spacing.md }]} padding="md">
                <View style={styles.bannerRow}>
                  <MaterialCommunityIcons name="briefcase-clock-outline" size={22} color={colors.primary} />
                  <AppText variant="body" style={styles.bannerText}>
                    You have an active job in progress. You are hidden from new booking suggestions until the
                    client marks it complete. Go online again after you finish.
                  </AppText>
                </View>
              </AppCard>
            ) : null}

            <View style={{ marginTop: Spacing.md }}>
              <ProviderPublicPreviewCard
                profile={me}
                headline={headline}
                experience={experience}
                workingHours={workingHours}
                photoUrl={photoUrl}
              />
            </View>

            {providerId > 0 ? (
              <View style={{ marginTop: Spacing.md }}>
                <ProviderReviewsSection providerId={providerId} />
              </View>
            ) : null}

            <AppCard padding="md" style={{ marginTop: Spacing.md }}>
              <AppText variant="caption" style={styles.overline}>
                ONLINE STATUS
              </AppText>
              <View style={styles.switchRow}>
                <View style={{ flex: 1 }}>
                  <AppText variant="rowTitle">{online ? 'Online' : 'Offline'}</AppText>
                  <AppText variant="caption" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
                    {online
                      ? 'Seekers can find you and send booking requests.'
                      : 'You are hidden from provider lists and cannot accept new bookings.'}
                  </AppText>
                </View>
                <Switch
                  value={online}
                  onValueChange={setOnline}
                  trackColor={{ false: colors.outlineSoft, true: colors.primary }}
                  thumbColor={colors.surface}
                  accessibilityLabel={online ? 'Online' : 'Offline'}
                />
              </View>
            </AppCard>

            <AppCard padding="md" style={{ marginTop: Spacing.md }}>
              <AppText variant="caption" style={styles.overline}>
                EDIT PUBLIC DETAILS
              </AppText>
              <TextField label="Professional headline" value={headline} onChangeText={setHeadline} placeholder="e.g. Certified cleaner" />
              <View style={{ height: Spacing.md }} />
              <TextField
                label="Experience & bio"
                value={experience}
                onChangeText={setExperience}
                placeholder="Describe your experience"
                multiline
                style={{ minHeight: 96 }}
              />
              <View style={{ height: Spacing.md }} />
              <TextField
                label="Working hours"
                value={workingHours}
                onChangeText={setWorkingHours}
                placeholder="e.g. Mon–Sat 8am–6pm"
                multiline
                style={{ minHeight: 72 }}
              />
              <AppText variant="caption" style={{ marginTop: Spacing.sm, color: colors.onSurfaceVariant }}>
                Profile photo: change it from the main Profile tab.
              </AppText>
            </AppCard>

            <AppCard padding="md" style={{ marginTop: Spacing.md }}>
              <AppText variant="caption" style={styles.overline}>
                TRUST & CREDENTIALS
              </AppText>
              <AppText variant="body" style={{ color: colors.onSurfaceVariant, lineHeight: 22 }}>
                Upload certificates, ID, or portfolio files. Clients can view these from your public profile.
              </AppText>

              {docsQ.isLoading ? (
                <AppText variant="body" style={{ marginTop: 12, color: colors.onSurface }}>
                  Loading documents…
                </AppText>
              ) : docsQ.data && docsQ.data.length > 0 ? (
                <View style={{ marginTop: 12, gap: 8 }}>
                  {docsQ.data.map((d) => (
                    <Pressable
                      key={d.id}
                      accessibilityRole="button"
                      onPress={() => void openPortfolioDocument(d.id, d.docTypeName)}
                      disabled={openingDocId != null}
                    >
                      <View style={styles.docRow}>
                        <View style={{ flex: 1, minWidth: 0 }}>
                          <AppText variant="rowTitle">{d.docTypeName}</AppText>
                          <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
                            {d.verified ? 'Verified' : 'Pending review'} · {d.fileLabel}
                          </AppText>
                        </View>
                        {openingDocId === d.id ? (
                          <ActivityIndicator size="small" color={colors.primary} />
                        ) : (
                          <MaterialCommunityIcons name="chevron-right" size={22} color={colors.onSurfaceVariant} />
                        )}
                      </View>
                    </Pressable>
                  ))}
                </View>
              ) : (
                <AppText variant="body" style={{ marginTop: 12, color: colors.onSurface }}>
                  No documents uploaded yet.
                </AppText>
              )}

              <AppButton
                variant="outline"
                onPress={() => navigation.navigate('ManageProviderDocuments')}
                style={{ marginTop: Spacing.md }}
              >
                Portfolio & documents
              </AppButton>
            </AppCard>

            <AppButton
              onPress={() => saveMut.mutate()}
              loading={saveMut.isPending}
              style={{ marginTop: Spacing.lg }}
            >
              Save changes
            </AppButton>
          </>
        )}
      </View>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: Spacing.sm,
      paddingHorizontal: Spacing.md,
      paddingTop: Spacing.sm,
      paddingBottom: Spacing.md,
    },
    body: {
      paddingHorizontal: Spacing.md,
      paddingBottom: Spacing.xl,
    },
    overline: {
      fontSize: 11,
      fontWeight: '700',
      letterSpacing: 1.1,
      color: colors.primary,
      marginBottom: Spacing.md,
    },
    switchRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: Spacing.md,
    },
    banner: {
      borderColor: colors.outlineSoft,
      borderWidth: StyleSheet.hairlineWidth,
    },
    bannerRow: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: Spacing.sm,
    },
    bannerText: {
      flex: 1,
      color: colors.onSurface,
    },
    docRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      paddingVertical: 8,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: colors.outlineSoft,
    },
  });
}
