import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { Spinner } from '@/components/ui/Spinner';
import { TextField } from '@/components/ui/TextField';
import { useTheme } from '@/providers/ThemeProvider';
import { searchProviders } from '@/services/api/search';
import type { ThemeColors } from '@/theme/types';

export function SearchScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const [q, setQ] = useState('');
  const enabled = useMemo(() => q.trim().length >= 2, [q]);
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['search', q.trim()],
    queryFn: () => searchProviders(q),
    enabled,
  });

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <View style={{ flex: 1 }}>
          <AppText variant="h3">Search</AppText>
          <AppText variant="caption" style={{ color: colors.onSurfaceVariant }}>
            Find providers by name, experience, or county.
          </AppText>
        </View>
      </View>
      <View style={styles.body}>
        <TextField label="Search" value={q} onChangeText={setQ} placeholder="e.g., caregiver" />
        {isLoading ? <Spinner /> : null}
        {error ? (
          <AppText style={styles.error} onPress={() => refetch()}>
            Failed to search. Tap to retry.
          </AppText>
        ) : null}
      </View>
      <FlatList
        data={data ?? []}
        keyExtractor={(u: any) => String(u.id)}
        contentContainerStyle={styles.list}
        renderItem={({ item }: any) => (
          <Pressable accessibilityRole="button">
            <AppCard padding="sm" style={styles.card}>
              <AppText variant="rowTitle">
                {(item.first_name ?? '').trim()} {(item.last_name ?? '').trim()}
              </AppText>
              <AppText variant="caption" style={styles.meta}>
                {(item.title ?? item.occupation ?? 'Provider').toString()}
              </AppText>
              <AppText variant="caption" style={styles.meta}>
                {(item.county ?? item.area_name ?? '').toString()}
              </AppText>
            </AppCard>
          </Pressable>
        )}
      />
    </Screen>
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
    body: { paddingHorizontal: 16, paddingBottom: 12, gap: 10 },
    error: { color: colors.bookingStatus.declinedText, fontWeight: '600' },
    list: { paddingHorizontal: 12, paddingBottom: 16, gap: 10 },
    card: { borderRadius: 16 },
    meta: { marginTop: 4, color: colors.onSurfaceVariant },
  });
}
