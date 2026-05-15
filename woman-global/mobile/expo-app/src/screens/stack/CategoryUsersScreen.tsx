import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { ListEmpty } from '@/components/ui/ListEmpty';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { listProvidersForService } from '@/services/api/marketplace';
import { getMyUserProfile } from '@/services/api/profile';
import { Spacing } from '@/theme/spacing';
import type { ThemeColors } from '@/theme/types';
import type { Provider } from '@/types/models';

type Props = NativeStackScreenProps<AppStackParamList, 'CategoryUsers'>;

export function CategoryUsersScreen({ navigation, route }: Props) {
  const { colors } = useTheme();
  const canvas = colors.softCanvas;
  const styles = useMemo(() => makeScreenStyles(colors), [colors]);
  const { serviceId, serviceName, prefillTotal, quoteLinesJson } = route.params;
  const meQ = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyUserProfile });
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['services', serviceId, 'providers'],
    queryFn: () => listProvidersForService(serviceId),
  });

  const providers = useMemo(() => {
    const rows = data ?? [];
    const myId = meQ.data?.id;
    if (myId == null || myId <= 0) return rows;
    return rows.filter((p) => p.id !== myId);
  }, [data, meQ.data?.id]);

  const onlySelfInDirectory =
    !isLoading &&
    providers.length === 0 &&
    (data?.length ?? 0) > 0 &&
    meQ.data?.id != null;

  return (
    <Screen padded={false} safeAreaBackground={canvas}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
            <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
          </IconButton>
          <View style={{ flex: 1 }}>
            <AppText variant="h3">{serviceName || 'Providers'}</AppText>
            <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
              Browse professionals offering this service.
            </AppText>
          </View>
        </View>
      </View>

      {error ? (
        <AppText style={styles.error} onPress={() => refetch()}>
          Failed to load providers. Tap to retry.
        </AppText>
      ) : null}

      <FlatList
        data={providers}
        keyExtractor={(p) => String(p.id)}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          isLoading ? null : (
            <ListEmpty
              title={onlySelfInDirectory ? 'No other providers' : 'No providers yet'}
              body={
                onlySelfInDirectory
                  ? 'You are listed for this service. Other professionals will appear here when they join.'
                  : 'No professionals are available for this service right now.'
              }
            />
          )
        }
        renderItem={({ item }) => (
          <ProviderRow
            item={item}
            onPress={() => {
              const displayName =
                `${item.first_name ?? ''} ${item.last_name ?? ''}`.trim() || item.user_name || `Provider #${item.id}`;
              const areaBits = [item.area_name, item.county, item.country].filter(Boolean);
              navigation.navigate('ProviderProfile', {
                providerId: item.id,
                providerRef: item.user_name || String(item.id),
                serviceId,
                serviceName,
                providerDisplayName: displayName,
                providerPic: item.pic,
                providerTitle: item.title,
                providerOccupation: item.occupation,
                providerWorkingHours: item.working_hours,
                providerAreaLabel: areaBits.length ? areaBits.join(' · ') : undefined,
                ...(prefillTotal != null && prefillTotal > 0 && quoteLinesJson
                  ? { prefillTotal, quoteLinesJson }
                  : {}),
              });
            }}
          />
        )}
      />
    </Screen>
  );
}

function ProviderRow({ item, onPress }: { item: Provider; onPress: () => void }) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeRowStyles(colors), [colors]);
  const displayName =
    `${item.first_name ?? ''} ${item.last_name ?? ''}`.trim() || item.user_name || `Provider #${item.id}`;
  return (
    <Pressable onPress={onPress} accessibilityRole="button">
      <AppCard padding="sm" style={styles.row}>
        <View style={styles.rowTop}>
          <AppText variant="rowTitle">{displayName}</AppText>
          {item.wh_badge ? (
            <View style={styles.badge}>
              <AppText style={styles.badgeText}>WH</AppText>
            </View>
          ) : null}
        </View>
        <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
          {item.occupation || item.title || 'Professional'}
        </AppText>
        <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
          {item.county || item.area_name || '—'}
        </AppText>
      </AppCard>
    </Pressable>
  );
}

function makeScreenStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
    },
    headerLeft: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
    },
    error: {
      color: colors.bookingStatus.declinedText,
      marginHorizontal: 16,
      marginBottom: 8,
      fontWeight: '600',
    },
    list: {
      paddingHorizontal: 12,
      paddingTop: 12,
      paddingBottom: 16,
      gap: Spacing.md,
    },
  });
}

function makeRowStyles(colors: ThemeColors) {
  return StyleSheet.create({
    row: {
      borderRadius: 16,
    },
    rowTop: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: 10,
    },
    badge: {
      backgroundColor: colors.surfaceVariant,
      borderColor: colors.outline,
      borderWidth: 1,
      borderRadius: 999,
      paddingHorizontal: 10,
      paddingVertical: 4,
    },
    badgeText: {
      color: colors.onSurfaceVariant,
      fontWeight: '800',
      fontSize: 12,
    },
  });
}
