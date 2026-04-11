import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';

import { Screen } from '@/components/layout/Screen';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import { listServices } from '@/services/api/services';
import { Colors } from '@/theme/colors';
import type { Service } from '@/types/models';

export function ServicesScreen() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['services'],
    queryFn: listServices,
  });

  const services = data ?? [];

  return (
    <Screen>
      <Text style={styles.title}>Services</Text>
      {isLoading ? <Spinner /> : null}
      {error ? (
        <Text style={styles.error} onPress={() => refetch()}>
          Failed to load services. Tap to retry.
        </Text>
      ) : null}
      <FlatList
        data={services}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={styles.list}
        ListEmptyComponent={isLoading ? null : <ListEmpty title="No services found." />}
        renderItem={({ item }) => <ServiceRow item={item} />}
      />
    </Screen>
  );
}

function ServiceRow({ item }: { item: Service }) {
  return (
    <View style={styles.row}>
      <Text style={styles.name}>{item.name}</Text>
      {item.description ? <Text style={styles.desc}>{item.description}</Text> : null}
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
  desc: {
    color: Colors.onSurfaceVariant,
    lineHeight: 18,
  },
});

