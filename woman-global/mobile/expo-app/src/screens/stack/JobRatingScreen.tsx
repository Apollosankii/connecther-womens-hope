import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Alert, Image, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { GradientCtaButton } from '@/components/ui/GradientCtaButton';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { submitJobReview } from '@/services/api/reviews';
import type { ThemeColors } from '@/theme/types';

const STAR_ON = '#FFCC00';
const STAR_OFF = '#E0E0E0';

type Props = NativeStackScreenProps<AppStackParamList, 'JobRating'>;

export function JobRatingScreen({ navigation, route }: Props) {
  const { jobId, rateeName, rateeRole, serviceName, rateePic } = route.params;
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const { colors } = useTheme();
  const canvas = colors.softCanvas;
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const [stars, setStars] = useState(0);
  const [comment, setComment] = useState('');

  const mutation = useMutation({
    mutationFn: async () => submitJobReview(jobId, stars, comment.trim() || undefined),
    onSuccess: (res) => {
      if (res.ok) {
        void queryClient.invalidateQueries({ queryKey: ['jobs', 'completed'] });
        navigation.goBack();
      } else {
        const code = 'errorCode' in res ? res.errorCode : 'unknown';
        Alert.alert('Rating', code === 'already_rated' ? 'You already submitted a review.' : 'Could not submit.');
      }
    },
    onError: (e) => Alert.alert('Rating', e instanceof Error ? e.message : 'Unknown error'),
  });

  const canSubmit = stars >= 1;

  const starRow = useMemo(
    () => (
      <View style={styles.stars}>
        {[1, 2, 3, 4, 5].map((n) => (
          <Pressable key={n} accessibilityRole="button" onPress={() => setStars(n)} hitSlop={8}>
            <MaterialCommunityIcons name={stars >= n ? 'star' : 'star-outline'} size={40} color={stars >= n ? STAR_ON : STAR_OFF} />
          </Pressable>
        ))}
      </View>
    ),
    [stars, styles.stars],
  );

  return (
    <Screen padded={false} safeAreaBackground={canvas}>
      <View style={[styles.screen, { backgroundColor: canvas, paddingTop: insets.top + 8 }]}>
        <View style={styles.toolbar}>
          <Pressable accessibilityRole="button" onPress={() => navigation.goBack()} style={styles.backHit}>
            <MaterialCommunityIcons name="arrow-left" size={22} color={colors.accent} />
          </Pressable>
          <AppText variant="h3" style={styles.toolbarTitle}>
            Ratings
          </AppText>
          <View style={{ width: 38 }} />
        </View>

        <ScrollView contentContainerStyle={{ paddingBottom: Math.max(24, insets.bottom + 16) }} showsVerticalScrollIndicator={false}>
          <View style={styles.cardWrap}>
            <AppCard style={styles.card}>
              <AppText variant="rowTitle" style={styles.centerName}>
                {rateeName}
              </AppText>
              <AppText variant="caption" style={[styles.centerRole, { color: colors.onSurfaceVariant }]}>
                {rateeRole}
              </AppText>
              {serviceName ? (
                <AppText variant="caption" style={[styles.centerRole, { marginTop: 4, color: colors.onSurfaceVariant }]}>
                  {serviceName}
                </AppText>
              ) : null}

              <AppText variant="bodyStrong" style={[styles.qTitle, { color: colors.onSurface }]}>
                How was your Service?
              </AppText>
              <AppText variant="caption" style={[styles.qSub, { color: colors.onSurfaceVariant }]}>
                Your feedback will help improve our services
              </AppText>

              {starRow}

              <TextInput
                style={[
                  styles.input,
                  {
                    borderColor: colors.outline,
                    backgroundColor: colors.surfaceVariant,
                    color: colors.onSurface,
                  },
                ]}
                placeholder="Additional comments."
                placeholderTextColor={colors.onSurfaceVariant}
                value={comment}
                onChangeText={setComment}
                multiline
                maxLength={2000}
              />

              <GradientCtaButton
                title="Submit Review"
                loading={mutation.isPending}
                disabled={!canSubmit || mutation.isPending}
                onPress={() => mutation.mutate()}
                style={{ marginTop: 8 }}
              />
            </AppCard>

            <View style={styles.avatarFloat}>
              {rateePic ? (
                <Image source={{ uri: String(rateePic) }} style={styles.avatar} accessibilityIgnoresInvertColors />
              ) : (
                <View style={[styles.avatar, styles.avatarPh]}>
                  <MaterialCommunityIcons name="account" size={40} color={colors.onSurfaceVariant} />
                </View>
              )}
            </View>
          </View>
        </ScrollView>
      </View>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    screen: {
      flex: 1,
    },
    toolbar: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 8,
      marginBottom: 8,
    },
    backHit: {
      width: 40,
      height: 40,
      alignItems: 'center',
      justifyContent: 'center',
    },
    toolbarTitle: {
      flex: 1,
      textAlign: 'center',
      fontSize: 18,
    },
    cardWrap: {
      marginHorizontal: 16,
      marginTop: 56,
      position: 'relative',
    },
    card: {
      borderRadius: 22,
      paddingTop: 72,
      paddingHorizontal: 20,
      paddingBottom: 24,
      gap: 8,
      elevation: 6,
      shadowColor: '#000',
      shadowOpacity: 0.08,
      shadowRadius: 12,
      shadowOffset: { width: 0, height: 4 },
    },
    avatarFloat: {
      position: 'absolute',
      top: -52,
      left: 0,
      right: 0,
      alignItems: 'center',
      zIndex: 2,
    },
    avatar: {
      width: 104,
      height: 104,
      borderRadius: 52,
      borderWidth: 6,
      borderColor: '#BF43A4',
    },
    avatarPh: {
      backgroundColor: colors.surfaceVariant,
      alignItems: 'center',
      justifyContent: 'center',
    },
    centerName: {
      textAlign: 'center',
      color: colors.onSurface,
    },
    centerRole: {
      textAlign: 'center',
    },
    qTitle: {
      marginTop: 20,
      textAlign: 'center',
    },
    qSub: {
      textAlign: 'center',
      marginBottom: 4,
    },
    stars: {
      flexDirection: 'row',
      justifyContent: 'center',
      gap: 6,
      marginTop: 12,
      marginBottom: 8,
    },
    input: {
      marginTop: 8,
      minHeight: 120,
      borderRadius: 14,
      borderWidth: 1,
      padding: 14,
      textAlignVertical: 'top',
      fontSize: 15,
    },
  });
}
