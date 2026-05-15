import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { Alert, FlatList, Linking, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppButton } from '@/components/ui/AppButton';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import { useTheme } from '@/providers/ThemeProvider';
import { acceptBookingRequest, cancelBookingRequest, declineBookingRequest, listMyBookingRequests } from '@/services/api/bookingRequests';
import type { ThemeColors } from '@/theme/types';
import type { BookingRequest } from '@/types/models';

export function ProviderBookingRequestsScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const qc = useQueryClient();
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['booking-requests', 'mine'],
    queryFn: listMyBookingRequests,
  });

  const acceptMut = useMutation({
    mutationFn: (id: number) => acceptBookingRequest(id),
    onSuccess: async (res) => {
      await qc.invalidateQueries({ queryKey: ['booking-requests'] });
      if (res.ok && res.chatCode) {
        navigation.navigate('Chat', { chatCode: res.chatCode, title: 'Chat' });
        return;
      }
      if (!res.ok && res.errorCode) {
        const msg =
          res.errorCode === 'provider_offline'
            ? 'You are offline. Turn on Online in Provider profile to accept bookings.'
            : res.errorCode === 'provider_busy'
              ? 'Finish your current job before accepting new bookings.'
              : `Could not accept: ${res.errorCode}`;
        Alert.alert('Accept booking', msg);
      }
    },
  });
  const declineMut = useMutation({
    mutationFn: (id: number) => declineBookingRequest(id),
    onSuccess: async () => qc.invalidateQueries({ queryKey: ['booking-requests'] }),
  });
  const cancelMut = useMutation({
    mutationFn: (id: number) => cancelBookingRequest(id),
    onSuccess: async () => qc.invalidateQueries({ queryKey: ['booking-requests'] }),
  });

  const items = data ?? [];
  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3">Booking Requests</AppText>
      </View>
      <View style={styles.divider} />
      {isLoading ? <Spinner /> : null}
      {error ? (
        <AppText style={styles.error} onPress={() => refetch()}>
          Failed to load requests. Tap to retry.
        </AppText>
      ) : null}
      <FlatList
        data={items}
        keyExtractor={(r) => String(r.id)}
        contentContainerStyle={styles.list}
        ListEmptyComponent={isLoading ? null : <ListEmpty title="No booking requests" body="Requests you send or receive will show here." />}
        renderItem={({ item }) => (
          <RequestRow
            item={item}
            colors={colors}
            onAccept={() => acceptMut.mutate(item.id)}
            onDecline={() => declineMut.mutate(item.id)}
            onCancel={() => cancelMut.mutate(item.id)}
            busy={acceptMut.isPending || declineMut.isPending || cancelMut.isPending}
          />
        )}
      />
    </Screen>
  );
}

function RequestRow({
  item,
  colors,
  onAccept,
  onDecline,
  onCancel,
  busy,
}: {
  item: BookingRequest;
  colors: ThemeColors;
  onAccept: () => void;
  onDecline: () => void;
  onCancel: () => void;
  busy: boolean;
}) {
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const isPending = item.status === 'pending';
  const isClient = item.i_am_client === true;
  const title = isClient ? item.provider_display || 'Provider' : item.client_display || 'Client';
  const mapsUrl = item.maps_url?.trim();

  return (
    <Pressable accessibilityRole="button">
      <AppCard padding="sm" style={styles.card}>
        <AppText variant="rowTitle">{title}</AppText>
        <AppText variant="caption" style={[styles.meta, { color: colors.onSurfaceVariant }]}>
          Status: {item.status}
        </AppText>
        {item.location_text ? (
          <AppText variant="caption" style={[styles.meta, { color: colors.onSurfaceVariant }]}>
            {item.location_text}
          </AppText>
        ) : null}
        {item.message ? (
          <AppText variant="caption" style={[styles.meta, { color: colors.onSurfaceVariant }]}>
            {item.message}
          </AppText>
        ) : null}
        {mapsUrl ? (
          <AppButton variant="outline" onPress={() => Linking.openURL(mapsUrl)} style={{ marginTop: 8 }}>
            Open in Maps
          </AppButton>
        ) : null}

        <View style={styles.actions}>
          {!isClient && isPending ? (
            <>
              <ActionButton colors={colors} label="Accept" tone="ok" onPress={onAccept} disabled={busy} />
              <ActionButton colors={colors} label="Decline" tone="danger" onPress={onDecline} disabled={busy} />
            </>
          ) : null}
          {isClient && isPending ? <ActionButton colors={colors} label="Cancel" tone="neutral" onPress={onCancel} disabled={busy} /> : null}
        </View>
      </AppCard>
    </Pressable>
  );
}

function ActionButton({
  label,
  tone,
  onPress,
  disabled,
  colors,
}: {
  label: string;
  tone: 'ok' | 'danger' | 'neutral';
  onPress: () => void;
  disabled?: boolean;
  colors: ThemeColors;
}) {
  const bg =
    tone === 'ok' ? colors.bookingStatus.acceptedText : tone === 'danger' ? colors.bookingStatus.declinedText : colors.surfaceVariant;
  const fg = tone === 'neutral' ? colors.onSurface : colors.onPrimary;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled}
      style={{
        paddingHorizontal: 12,
        paddingVertical: 10,
        borderRadius: 999,
        minWidth: 92,
        alignItems: 'center',
        backgroundColor: bg,
        opacity: disabled ? 0.6 : 1,
      }}
    >
      <AppText style={{ color: fg, fontWeight: '700' }}>{label}</AppText>
    </Pressable>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      backgroundColor: colors.background,
    },
    divider: {
      height: 1,
      marginHorizontal: 20,
      backgroundColor: colors.outline,
    },
    error: {
      color: colors.bookingStatus.declinedText,
      marginHorizontal: 20,
      marginBottom: 8,
      fontWeight: '600',
    },
    list: {
      paddingHorizontal: 12,
      paddingTop: 12,
      paddingBottom: 16,
      gap: 10,
    },
    card: { borderRadius: 16 },
    meta: { marginTop: 4 },
    actions: { flexDirection: 'row', gap: 10, marginTop: 10, flexWrap: 'wrap' },
  });
}
