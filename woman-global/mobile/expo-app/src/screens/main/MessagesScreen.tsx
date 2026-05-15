import { useMemo } from 'react';
import { FlatList, Image, Pressable, StyleSheet, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { listConversations } from '@/services/api/chat';
import { Metrics } from '@/theme/metrics';
import type { ThemeColors } from '@/theme/types';
import type { Conversation } from '@/types/models';
import { getFloatingTabBarScrollInset } from '@/navigation/floatingTabBar';

export function MessagesScreen() {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const insets = useSafeAreaInsets();
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['chat', 'conversations'],
    queryFn: listConversations,
  });
  const conversations = data ?? [];

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <AppText variant="h3">Conversations</AppText>
        <AppText variant="body" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
          Chats with providers and clients
        </AppText>
      </View>
      <View style={styles.divider} />
      {isLoading ? <Spinner /> : null}
      {error ? (
        <AppText style={styles.error} onPress={() => refetch()}>
          Failed to load conversations. Tap to retry.
        </AppText>
      ) : null}
      <FlatList
        style={{ flex: 1 }}
        data={conversations}
        keyExtractor={(c) => c.chat_code}
        contentContainerStyle={[
          styles.list,
          { paddingBottom: Math.max(16, getFloatingTabBarScrollInset(insets.bottom)) },
        ]}
        ListEmptyComponent={
          isLoading ? null : (
            <ListEmpty
              title="No conversations yet"
              body="When you book a service or start a chat, it will appear here."
            />
          )
        }
        renderItem={({ item }) => (
          <ConversationRow
            item={item}
            colors={colors}
            onPress={() =>
              navigation.navigate('Chat', {
                chatCode: item.chat_code,
                title: item.other_user_name ?? 'Chat',
                peerPic: item.other_user_pic ?? null,
              })
            }
          />
        )}
      />
    </Screen>
  );
}

function ConversationRow({
  item,
  colors,
  onPress,
}: {
  item: Conversation;
  colors: ThemeColors;
  onPress: () => void;
}) {
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const initial = (item.other_user_name ?? item.chat_code ?? '?').trim().slice(0, 1).toUpperCase();
  return (
    <Pressable onPress={onPress} accessibilityRole="button">
      <AppCard padding="none" style={styles.row}>
        <View style={styles.rowInner}>
          <View style={styles.avatar}>
            {item.other_user_pic ? (
              <Image source={{ uri: String(item.other_user_pic) }} style={styles.avatarImg} accessibilityIgnoresInvertColors />
            ) : (
              <AppText style={styles.avatarText}>{initial}</AppText>
            )}
          </View>
          <View style={{ flex: 1 }}>
            <AppText variant="rowTitle">{item.other_user_name ?? item.chat_code}</AppText>
            <AppText variant="caption" style={[styles.preview, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
              {item.last_message || 'Start Chat…'}
            </AppText>
          </View>
        </View>
      </AppCard>
    </Pressable>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      paddingHorizontal: Metrics.headerPaddingX,
      paddingTop: 16,
      paddingBottom: 12,
    },
    divider: {
      height: 1,
      backgroundColor: colors.outlineSoft,
      marginHorizontal: Metrics.headerPaddingX,
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
    rowInner: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 14,
      paddingVertical: 12,
    },
    avatar: {
      width: 44,
      height: 44,
      borderRadius: 22,
      backgroundColor: colors.surfaceVariant,
      alignItems: 'center',
      justifyContent: 'center',
      borderWidth: 1,
      borderColor: colors.outlineSoft,
      overflow: 'hidden',
    },
    avatarImg: {
      width: 44,
      height: 44,
      borderRadius: 22,
    },
    avatarText: {
      fontWeight: '900',
      color: colors.onSurface,
    },
    preview: {
      marginTop: 4,
    },
  });
}
