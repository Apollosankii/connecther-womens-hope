import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMemo } from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { useTheme } from '@/providers/ThemeProvider';
import { PRIVACY_POLICY_ONLINE_URL, PRIVACY_TEXT, TERMS_TEXT } from '@/legal/termsCopy';
import type { ThemeColors } from '@/theme/types';

export function TermsScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3">Terms and policies</AppText>
      </View>
      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <View style={styles.body}>
          <AppCard padding="md" style={styles.card}>
            <AppText variant="bodyStrong">Terms of Service</AppText>
            <AppText variant="caption" style={styles.block}>
              {TERMS_TEXT}
            </AppText>
            <AppText variant="bodyStrong" style={{ marginTop: 18 }}>
              Privacy Policy
            </AppText>
            <AppText variant="caption" style={styles.block}>
              {PRIVACY_TEXT}
            </AppText>
            <Pressable onPress={() => Linking.openURL(PRIVACY_POLICY_ONLINE_URL)} accessibilityRole="link">
              <AppText variant="caption" style={[styles.link, { color: colors.primary }]}>
                View privacy policy online (Google Doc)
              </AppText>
            </Pressable>
            <View style={{ height: 12 }} />
            <AppButton variant="outline" onPress={() => Linking.openURL(PRIVACY_POLICY_ONLINE_URL)}>
              Open in browser
            </AppButton>
          </AppCard>
        </View>
      </ScrollView>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingTop: 12, paddingBottom: 8 },
    scroll: { paddingBottom: 24 },
    body: { paddingHorizontal: 16 },
    card: { borderRadius: 18 },
    block: { marginTop: 8, color: colors.onSurfaceVariant, lineHeight: 20 },
    link: { marginTop: 10, fontWeight: '700', textDecorationLine: 'underline' },
  });
}
