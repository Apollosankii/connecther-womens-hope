import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation } from '@tanstack/react-query';
import { Alert, Image, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GradientCtaButton } from '@/components/ui/GradientCtaButton';
import { AppText } from '@/components/ui/AppText';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { startConversationWithProvider } from '@/services/api/marketplace';
import { ShockwaveRings } from '@/components/ui/ShockwaveRings';

type Props = NativeStackScreenProps<AppStackParamList, 'ConnectionSuccess'>;

export function ConnectionSuccessScreen({ navigation, route }: Props) {
  const { colors } = useTheme();
  const canvasBg = colors.softCanvas;
  const {
    providerId,
    providerRef,
    serviceId,
    serviceName,
    providerDisplayName,
    providerPic,
    postBooking,
    prefillPrice,
    quoteLinesJson,
  } = route.params;
  const insets = useSafeAreaInsets();
  const first =
    (providerDisplayName ?? '').trim().split(/\s+/).filter(Boolean)[0] ||
    (providerDisplayName ?? 'them').trim() ||
    'them';

  const startChat = useMutation({
    mutationFn: () => startConversationWithProvider(providerRef, serviceId),
    onSuccess: (outcome) => {
      if (outcome.chatCode) {
        navigation.replace('Chat', {
          chatCode: outcome.chatCode,
          title: providerDisplayName,
          peerPic: providerPic ?? undefined,
        });
      } else {
        Alert.alert('Chat', outcome.errorCode ? `Couldn’t open chat (${outcome.errorCode}).` : 'Couldn’t open chat.');
      }
    },
    onError: (e) => Alert.alert('Chat failed', e instanceof Error ? e.message : 'Unknown error'),
  });

  return (
    <View
      style={[
        styles.root,
        { paddingTop: insets.top + 8, paddingBottom: Math.max(16, insets.bottom + 8), backgroundColor: canvasBg },
      ]}
    >
      <Pressable accessibilityRole="button" onPress={() => navigation.goBack()} style={styles.backHit}>
        <MaterialCommunityIcons name="arrow-left" size={22} color={colors.accent} />
      </Pressable>

      <AppText
        variant="body"
        style={[styles.headline, { color: colors.onBackground, fontSize: 16, fontWeight: '500', textAlign: 'center' }]}
      >
        {postBooking
          ? `Your booking request was sent. Say hello to ${first}`
          : `You have been connected with ${first}`}
      </AppText>
      {postBooking && (serviceName ?? '').trim() ? (
        <AppText variant="caption" style={[styles.caption, { color: colors.onSurfaceVariant }]}>
          {`Service: ${(serviceName ?? '').trim()}`}
        </AppText>
      ) : null}

      <View style={styles.hero}>
        <View style={styles.ringStage}>
          <ShockwaveRings
            outerSize={280}
            middleSize={200}
            outerColor="rgba(255, 38, 185, 0.18)"
            middleColor="rgba(232, 150, 255, 0.22)"
          />
          {providerPic ? (
            <Image source={{ uri: String(providerPic) }} style={styles.avatar} accessibilityIgnoresInvertColors />
          ) : (
            <View style={[styles.avatar, styles.avatarPh]}>
              <MaterialCommunityIcons name="account" size={48} color="#9E9E9E" />
            </View>
          )}
        </View>
      </View>

      <View style={styles.footer}>
        <GradientCtaButton
          title="Send message"
          loading={startChat.isPending}
          disabled={startChat.isPending}
          onPress={() => startChat.mutate()}
        />
        {!postBooking ? (
          <Pressable
            accessibilityRole="button"
            onPress={() =>
              navigation.replace('RequestBooking', {
                providerId,
                providerRef,
                serviceId,
                serviceName,
                providerDisplayName,
                providerPic: providerPic ?? null,
                ...(prefillPrice != null && prefillPrice > 0 ? { prefillPrice } : {}),
                ...(quoteLinesJson?.trim() ? { quoteLinesJson: quoteLinesJson.trim() } : {}),
              })
            }
            style={styles.secondary}
          >
            <AppText variant="body" style={[styles.secondaryLabel, { color: colors.accent }]}>
              Continue to booking details
            </AppText>
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: 20,
  },
  backHit: {
    alignSelf: 'flex-start',
    padding: 8,
    marginBottom: 4,
  },
  headline: {
    textAlign: 'center',
    marginTop: 8,
  },
  caption: {
    textAlign: 'center',
    marginTop: 8,
    paddingHorizontal: 12,
  },
  hero: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: 260,
  },
  ringStage: {
    width: 280,
    height: 280,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatar: {
    width: 112,
    height: 112,
    borderRadius: 56,
    zIndex: 2,
    borderWidth: 4,
    borderColor: '#BF43A4',
  },
  avatarPh: {
    backgroundColor: '#F0F0F0',
    alignItems: 'center',
    justifyContent: 'center',
  },
  footer: {
    gap: 12,
  },
  secondary: {
    paddingVertical: 10,
    alignItems: 'center',
  },
  secondaryLabel: {
    fontWeight: '600',
  },
});
