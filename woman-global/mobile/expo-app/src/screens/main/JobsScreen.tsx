import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useMemo } from 'react';
import { FlatList, Linking, Pressable, StyleSheet, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppButton } from '@/components/ui/AppButton';
import { AppText } from '@/components/ui/AppText';
import { GradientCtaButton } from '@/components/ui/GradientCtaButton';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import { useTheme } from '@/providers/ThemeProvider';
import { listMyBookingRequests } from '@/services/api/bookingRequests';
import { listCompletedJobs, listPendingJobs } from '@/services/api/jobs';
import { Metrics } from '@/theme/metrics';
import type { ThemeColors } from '@/theme/types';
import type { BookingRequest, Job } from '@/types/models';
import { getFloatingTabBarScrollInset } from '@/navigation/floatingTabBar';

const RECENT_REQUESTS = 6;

export function JobsScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const insets = useSafeAreaInsets();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['jobs', 'pending'],
    queryFn: listPendingJobs,
  });
  const bookingQ = useQuery({
    queryKey: ['booking-requests', 'mine'],
    queryFn: listMyBookingRequests,
  });
  const completedQ = useQuery({
    queryKey: ['jobs', 'completed'],
    queryFn: listCompletedJobs,
  });

  useFocusEffect(
    useCallback(() => {
      void completedQ.refetch();
    }, [completedQ]),
  );

  const jobs = data ?? [];
  const allRequests = bookingQ.data ?? [];
  const pendingRequests = allRequests.filter((r) => r.status === 'pending');
  const recentRequests = pendingRequests.slice(0, RECENT_REQUESTS);

  const showMainEmpty =
    !isLoading &&
    jobs.length === 0 &&
    !bookingQ.isLoading &&
    allRequests.length === 0 &&
    !(error || bookingQ.error);

  const completedJobs = completedQ.data ?? [];

  const listHeader = (
    <View>
      <View style={styles.quickList}>
        <Pressable accessibilityRole="button" onPress={() => navigation?.navigate?.('ProviderBookingRequests')}>
          <AppCard padding="sm" style={styles.quickCard}>
            <AppText variant="rowTitle">Booking Requests</AppText>
            <AppText variant="caption" style={[styles.meta, { color: colors.onSurfaceVariant }]}>
              {allRequests.length} total · {pendingRequests.length} pending
            </AppText>
          </AppCard>
        </Pressable>
        <Pressable accessibilityRole="button">
          <AppCard padding="sm" style={styles.quickCard}>
            <AppText variant="rowTitle">Completed Jobs</AppText>
            <AppText variant="caption" style={[styles.meta, { color: colors.onSurfaceVariant }]}>
              {(completedQ.data?.length ?? 0).toString()} total
            </AppText>
          </AppCard>
        </Pressable>
      </View>

      {recentRequests.length > 0 ? (
        <View style={styles.recentSection}>
          <View style={styles.recentHeader}>
            <AppText variant="bodyStrong">Pending requests</AppText>
            <Pressable onPress={() => navigation?.navigate?.('ProviderBookingRequests')}>
              <AppText variant="caption" style={{ color: colors.primary, fontWeight: '700' }}>
                See all
              </AppText>
            </Pressable>
          </View>
          {recentRequests.map((req, idx) => (
            <BookingRequestTeaser
              key={`booking-req-${req.id}-${idx}`}
              item={req}
              onPress={() => navigation?.navigate?.('ProviderBookingRequests')}
            />
          ))}
        </View>
      ) : bookingQ.isLoading ? (
        <View style={{ paddingVertical: 8, alignItems: 'center' }}>
          <Spinner />
        </View>
      ) : null}

      {completedJobs.length > 0 ? (
        <View style={styles.recentSection}>
          <View style={styles.recentHeader}>
            <AppText variant="bodyStrong">Completed</AppText>
            <Pressable onPress={() => void completedQ.refetch()}>
              <AppText variant="caption" style={{ color: colors.primary, fontWeight: '700' }}>
                Refresh
              </AppText>
            </Pressable>
          </View>
          {completedJobs.map((j, jIdx) => {
            const amClient = j.i_am_client === true;
            const rateeName = ((amClient ? j.provider : j.client) ?? '').trim() || (amClient ? 'Provider' : 'Client');
            const rateeRole = amClient ? 'Caregiver' : 'Client';
            const showRate = j.my_review_submitted !== true;
            return (
              <AppCard key={`completed-${j.job_id}-${jIdx}`} padding="sm" style={{ borderRadius: 16, marginBottom: 8 }}>
                <AppText variant="rowTitle">{j.service ?? `Job #${j.job_id}`}</AppText>
                <AppText variant="caption" style={{ marginTop: 4, color: colors.onSurfaceVariant }} numberOfLines={2}>
                  {rateeName}
                  {j.completed_at ? ` · ${j.completed_at}` : ''}
                </AppText>
                {showRate ? (
                  <GradientCtaButton
                    title="Submit review"
                    onPress={() =>
                      navigation?.navigate?.('JobRating', {
                        jobId: j.job_id,
                        rateeName,
                        rateeRole,
                        serviceName: j.service ?? undefined,
                      })
                    }
                    style={{ marginTop: 10 }}
                  />
                ) : null}
              </AppCard>
            );
          })}
        </View>
      ) : null}
    </View>
  );

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <AppText variant="h3">Bookings</AppText>
        <AppText variant="body" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
          Jobs, booking requests, and completed work appear below.
        </AppText>
      </View>
      <View style={styles.divider} />
      {isLoading ? <Spinner /> : null}
      {error ? (
        <AppText style={styles.error} onPress={() => refetch()}>
          Failed to load jobs. Tap to retry.
        </AppText>
      ) : null}
      {bookingQ.error ? (
        <AppText style={styles.error} onPress={() => bookingQ.refetch()}>
          Failed to load booking requests. Tap to retry.
        </AppText>
      ) : null}
      <FlatList
        data={jobs}
        keyExtractor={(j, index) => `job-${j.job_id}-${index}`}
        contentContainerStyle={[
          styles.list,
          { paddingBottom: Math.max(16, getFloatingTabBarScrollInset(insets.bottom)) },
        ]}
        ListHeaderComponent={listHeader}
        ListEmptyComponent={
          showMainEmpty ? (
            <ListEmpty
              title="Nothing here yet"
              body="When you send or receive a booking request—or have an active job—it will appear here."
            />
          ) : null
        }
        renderItem={({ item }) => <JobRow item={item} colors={colors} />}
      />
    </Screen>
  );
}

function BookingRequestTeaser({ item, onPress }: { item: BookingRequest; onPress: () => void }) {
  const { colors } = useTheme();
  const isClient = item.i_am_client === true;
  const title = isClient ? item.provider_display || 'Provider' : item.client_display || 'Client';
  const mapsUrl = item.maps_url?.trim();
  return (
    <Pressable onPress={onPress} accessibilityRole="button" style={{ marginBottom: 8 }}>
      <AppCard padding="sm" style={{ borderRadius: 16 }}>
        <AppText variant="rowTitle" numberOfLines={1}>
          {title}
        </AppText>
        <AppText variant="caption" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
          {item.status} · Service #{item.service_id}
        </AppText>
        {item.location_text ? (
          <AppText variant="caption" numberOfLines={1} style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
            {item.location_text}
          </AppText>
        ) : null}
        {mapsUrl ? (
          <AppButton variant="outline" onPress={() => void Linking.openURL(mapsUrl)} style={{ marginTop: 8 }}>
            Open in Maps
          </AppButton>
        ) : null}
      </AppCard>
    </Pressable>
  );
}

function JobRow({ item, colors }: { item: Job; colors: ThemeColors }) {
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const price = item.price != null ? Math.round(item.price) : null;
  return (
    <Pressable accessibilityRole="button">
      <AppCard padding="sm" style={styles.row}>
        <AppText variant="rowTitle">{item.service ?? `Job #${item.job_id}`}</AppText>
        <View style={styles.rowGrid}>
          <View style={{ flex: 1 }}>
            <AppText variant="caption" style={styles.label}>
              Location
            </AppText>
            <AppText variant="caption" style={styles.value} numberOfLines={2}>
              {item.location ?? '—'}
            </AppText>
          </View>
          <View style={{ width: 10 }} />
          <View style={{ width: 110 }}>
            <AppText variant="caption" style={styles.label}>
              Status
            </AppText>
            <AppText variant="caption" style={styles.value}>
              {item.status ?? 'pending'}
            </AppText>
          </View>
        </View>
        {price != null ? (
          <AppText style={styles.price}>KES {price}</AppText>
        ) : null}
        {item.location ? (
          <AppButton onPress={() => {}} style={{ marginTop: 10 }}>
            View location
          </AppButton>
        ) : null}
      </AppCard>
    </Pressable>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      paddingHorizontal: Metrics.headerPaddingX,
      paddingTop: 12,
      paddingBottom: 8,
    },
    divider: {
      height: 1,
      marginHorizontal: Metrics.headerPaddingX,
      backgroundColor: colors.outlineSoft,
    },
    quickList: {
      paddingHorizontal: 12,
      paddingTop: 12,
      gap: 10,
    },
    quickCard: {
      borderRadius: 16,
    },
    error: {
      color: colors.bookingStatus.declinedText,
      marginHorizontal: Metrics.headerPaddingX,
      marginBottom: 8,
      fontWeight: '600',
    },
    list: {
      paddingHorizontal: 12,
      paddingTop: 12,
      paddingBottom: 16,
      gap: 10,
    },
    row: {
      borderRadius: 18,
    },
    rowGrid: {
      marginTop: 10,
      flexDirection: 'row',
    },
    label: {
      color: colors.onSurfaceVariant,
    },
    value: {
      marginTop: 2,
      color: colors.onSurface,
    },
    meta: {
      marginTop: 4,
    },
    price: {
      marginTop: 10,
      color: colors.primaryVariant,
      fontWeight: '800',
    },
    recentSection: {
      paddingHorizontal: 12,
      paddingTop: 8,
      paddingBottom: 4,
    },
    recentHeader: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: 8,
    },
  });
}
