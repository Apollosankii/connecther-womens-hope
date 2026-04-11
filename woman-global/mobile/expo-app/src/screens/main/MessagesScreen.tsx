import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';

import { Screen } from '@/components/layout/Screen';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import { listConversations } from '@/services/api/chat';
import { Colors } from '@/theme/colors';
import type { Conversation } from '@/types/models';

export function MessagesScreen() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['chat', 'conversations'],
    queryFn: listConversations,
  });
  const conversations = data ?? [];

  return (
    <Screen>
      <Text style={styles.title}>Messages</Text>
      {isLoading ? <Spinner /> : null}
      {error ? (
        <Text style={styles.error} onPress={() => refetch()}>
          Failed to load conversations. Tap to retry.
        </Text>
      ) : null}
      <FlatList
        data={conversations}
        keyExtractor={(c) => c.chat_code}
        contentContainerStyle={styles.list}
        ListEmptyComponent={isLoading ? null : <ListEmpty title="No conversations yet." />}
        renderItem={({ item }) => <ConversationRow item={item} />}
      />
    </Screen>
  );
}

function ConversationRow({ item }: { item: Conversation }) {
  return (
    <View style={styles.row}>
      <Text style={styles.name}>{item.other_user_name ?? item.chat_code}</Text>
      {item.last_message ? <Text style={styles.preview}>{item.last_message}</Text> : null}
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
  preview: {
    color: Colors.onSurfaceVariant,
  },
});

