import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  FlatList,
  Image,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Screen } from '@/components/layout/Screen';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { getChatHeader, getMyChatSenderId, listChatMessages, sendChatMessage } from '@/services/api/chat';
import { Spacing } from '@/theme/spacing';
import type { ThemeColors } from '@/theme/types';
import type { ChatMessage } from '@/types/models';

type Props = NativeStackScreenProps<AppStackParamList, 'Chat'>;

export function ChatScreen({ navigation, route }: Props) {
  const { chatCode, title: titleParam, peerPic: peerPicParam } = route.params;
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const insets = useSafeAreaInsets();
  const qc = useQueryClient();
  const [draft, setDraft] = useState('');
  const [androidKbHeight, setAndroidKbHeight] = useState(0);

  useEffect(() => {
    if (Platform.OS !== 'android') return;
    const show = Keyboard.addListener('keyboardDidShow', (e) => {
      setAndroidKbHeight(e.endCoordinates?.height ?? 0);
    });
    const hide = Keyboard.addListener('keyboardDidHide', () => setAndroidKbHeight(0));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);

  const headerQ = useQuery({
    queryKey: ['chat', chatCode, 'header'],
    queryFn: () => getChatHeader(chatCode),
  });

  const myIdQ = useQuery({
    queryKey: ['chat', 'mySenderId'],
    queryFn: getMyChatSenderId,
  });

  const { data } = useQuery({
    queryKey: ['chat', chatCode, 'messages'],
    queryFn: () => listChatMessages(chatCode),
  });

  const messages = useMemo(() => (data ?? []).slice().reverse(), [data]);

  const displayTitle =
    (headerQ.data?.peer_name ?? '').trim() || (titleParam ?? '').trim() || 'Chat';
  const serviceLine = (headerQ.data?.service_name ?? '').trim();
  const headerPic = peerPicParam ?? headerQ.data?.peer_pic ?? null;
  const mySenderId = myIdQ.data ?? null;

  const send = useMutation({
    mutationFn: async () => {
      const t = draft.trim();
      if (!t) return;
      await sendChatMessage(chatCode, t);
    },
    onSuccess: async () => {
      setDraft('');
      await qc.invalidateQueries({ queryKey: ['chat', chatCode, 'messages'] });
      await qc.invalidateQueries({ queryKey: ['chat', 'conversations'] });
    },
    onError: (e) => {
      Alert.alert('Message not sent', e instanceof Error ? e.message : 'Unknown error');
    },
  });

  const canSend = draft.trim().length > 0 && !send.isPending;

  const keyboardVerticalOffset = Platform.OS === 'ios' ? Math.max(insets.top, 12) : 0;

  return (
    <Screen padded={false}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={keyboardVerticalOffset}
      >
        <View
          style={[
            styles.flex,
            Platform.OS === 'android' && androidKbHeight > 0 ? { paddingBottom: androidKbHeight } : null,
          ]}
        >
          <View style={styles.header}>
            <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation.goBack()}>
              <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
            </IconButton>
            {headerPic ? (
              <Image source={{ uri: String(headerPic) }} style={styles.headerAvatar} accessibilityIgnoresInvertColors />
            ) : (
              <View style={[styles.headerAvatar, styles.headerAvatarPlaceholder]}>
                <AppText variant="bodyStrong">{displayTitle.trim().slice(0, 1).toUpperCase()}</AppText>
              </View>
            )}
            <View style={{ flex: 1, minWidth: 0 }}>
              <AppText variant="body" numberOfLines={1} style={{ color: colors.onBackground, fontSize: 15 }}>
                {displayTitle}
              </AppText>
              {serviceLine ? (
                <AppText
                  variant="caption"
                  style={{ marginTop: 2, color: colors.chat.textSubtitle, fontStyle: 'italic' }}
                  numberOfLines={1}
                >
                  {serviceLine}
                </AppText>
              ) : null}
            </View>
          </View>

          <FlatList
            style={styles.flex}
            data={messages}
            inverted
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="interactive"
            keyExtractor={(m) => String(m.id)}
            contentContainerStyle={styles.thread}
            renderItem={({ item }) => (
              <ChatBubble item={item} mySenderId={mySenderId} styles={styles} bubbleColors={colors} />
            )}
          />

          <View
            style={[
              styles.composerBar,
              {
                backgroundColor: colors.chat.composerSurface,
                paddingBottom: Math.max(insets.bottom, 10),
              },
            ]}
          >
            <View style={styles.composerInner}>
              <View style={styles.inputWrap}>
                <TextInput
                  style={[styles.input, { color: colors.onSurface }]}
                  placeholder="TYPE MESSAGE.."
                  placeholderTextColor={colors.chat.textSubtitle}
                  value={draft}
                  onChangeText={setDraft}
                  multiline
                  maxLength={4000}
                />
                <Pressable style={styles.inInputEmoji} accessibilityLabel="Emoji" hitSlop={8}>
                  <MaterialCommunityIcons name="emoticon-happy-outline" size={22} color={colors.chat.textSubtitle} />
                </Pressable>
              </View>
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ disabled: !canSend }}
                onPress={() => canSend && send.mutate()}
                disabled={!canSend}
                style={({ pressed }) => [
                  styles.iconCircle,
                  !canSend && { opacity: 0.45 },
                  pressed && canSend && { opacity: 0.9 },
                ]}
              >
                <MaterialCommunityIcons name="send" size={20} color={colors.primary} />
              </Pressable>
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

function ChatBubble({
  item,
  mySenderId,
  styles,
  bubbleColors,
}: {
  item: ChatMessage;
  mySenderId: string | null;
  styles: ReturnType<typeof makeStyles>;
  bubbleColors: ThemeColors;
}) {
  const sid = item.sender_id ?? null;
  const isMine = Boolean(mySenderId && sid && sid === mySenderId);
  return (
    <View style={[styles.bubbleWrap, isMine ? styles.bubbleWrapMine : styles.bubbleWrapTheirs]}>
      <View
        style={[
          styles.bubbleBox,
          isMine
            ? { backgroundColor: bubbleColors.chat.bubbleOutgoing }
            : {
                backgroundColor: bubbleColors.chat.bubbleIncoming,
                borderWidth: 1,
                borderColor: bubbleColors.primary,
              },
        ]}
      >
        <AppText variant="body" style={{ color: isMine ? bubbleColors.chat.textOnOutgoing : bubbleColors.chat.textOnIncoming }}>
          {item.content}
        </AppText>
      </View>
    </View>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    flex: {
      flex: 1,
    },
    header: {
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      flexDirection: 'row',
      alignItems: 'center',
      gap: 10,
      backgroundColor: colors.chat.headerSurface,
      borderBottomLeftRadius: 20,
      borderBottomRightRadius: 20,
      elevation: 3,
      shadowColor: '#000',
      shadowOpacity: 0.12,
      shadowRadius: 6,
      shadowOffset: { width: 0, height: 2 },
    },
    headerAvatar: {
      width: 40,
      height: 40,
      borderRadius: 20,
    },
    headerAvatarPlaceholder: {
      backgroundColor: colors.outlineSoft,
      alignItems: 'center',
      justifyContent: 'center',
    },
    thread: {
      paddingHorizontal: 12,
      paddingTop: 12,
      paddingBottom: 16,
      gap: 8,
      flexGrow: 1,
      backgroundColor: colors.chat.threadBackground,
    },
    bubbleWrap: {
      maxWidth: '88%',
    },
    bubbleWrapMine: {
      alignSelf: 'flex-end',
    },
    bubbleWrapTheirs: {
      alignSelf: 'flex-start',
    },
    bubbleBox: {
      borderRadius: 8,
      paddingHorizontal: 12,
      paddingVertical: 10,
    },
    composerBar: {
      borderTopLeftRadius: 20,
      borderTopRightRadius: 20,
      paddingTop: 12,
      paddingHorizontal: 12,
    },
    composerInner: {
      flexDirection: 'row',
      alignItems: 'flex-end',
      gap: 6,
    },
    inputWrap: {
      flex: 1,
      position: 'relative',
      justifyContent: 'center',
    },
    input: {
      minHeight: 44,
      maxHeight: 120,
      borderRadius: 22,
      backgroundColor: colors.chat.inputFill,
      paddingHorizontal: 14,
      paddingRight: 40,
      paddingVertical: 10,
      fontSize: 15,
    },
    inInputEmoji: {
      position: 'absolute',
      right: 10,
      top: 0,
      bottom: 0,
      justifyContent: 'center',
    },
    iconCircle: {
      width: 40,
      height: 40,
      borderRadius: 20,
      backgroundColor: colors.chat.inputFill,
      alignItems: 'center',
      justifyContent: 'center',
    },
  });
}
