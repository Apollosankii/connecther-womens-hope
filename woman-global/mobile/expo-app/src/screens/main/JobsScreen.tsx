import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';

import { Screen } from '@/components/layout/Screen';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import { listPendingJobs } from '@/services/api/jobs';
import { Colors } from '@/theme/colors';
import type { Job } from '@/types/models';

export function JobsScreen() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['jobs', 'pending'],
    queryFn: listPendingJobs,
  });

  const jobs = data ?? [];
  return (
    <Screen>
      <Text style={styles.title}>Jobs</Text>
      {isLoading ? <Spinner /> : null}
      {error ? (
        <Text style={styles.error} onPress={() => refetch()}>
          Failed to load jobs. Tap to retry.
        </Text>
      ) : null}
      <FlatList
        data={jobs}
        keyExtractor={(j) => String(j.job_id)}
        contentContainerStyle={styles.list}
        ListEmptyComponent={isLoading ? null : <ListEmpty title="No pending jobs." />}
        renderItem={({ item }) => <JobRow item={item} />}
      />
    </Screen>
  );
}

function JobRow({ item }: { item: Job }) {
  return (
    <View style={styles.row}>
      <Text style={styles.name}>{item.service ?? `Job #${item.job_id}`}</Text>
      <Text style={styles.meta}>{item.location ?? '—'}</Text>
      <Text style={styles.meta}>{item.status ?? 'pending'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: Colors.onBackground,
    marginBottom: 10,
  },
  error: {
    color: Colors.bookingStatus.declinedText,
    marginBottom: 8,
    fontWeight: '600',
  },
  list: {
    paddingBottom: 24,
    gap: 10,
  },
  row: {
    backgroundColor: Colors.surface,
    borderColor: Colors.outline,
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    gap: 4,
  },
  name: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.onSurface,
  },
  meta: {
    color: Colors.onSurfaceVariant,
  },
});

