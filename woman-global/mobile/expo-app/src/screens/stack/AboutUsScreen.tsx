import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { useTheme } from '@/providers/ThemeProvider';
import type { ThemeColors } from '@/theme/types';

export function AboutUsScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  return (
    <Screen padded={false} scroll>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3">About Us</AppText>
      </View>
      <View style={styles.body}>
        <AppCard padding="md" style={styles.card}>
          <AppText variant="bodyStrong">ConnectHer</AppText>
          <AppText variant="caption" style={styles.p}>
            ConnectHer helps seekers find trusted service providers and offers safety tools for emergencies.
          </AppText>
          <AppText variant="caption" style={styles.p}>
            Version and support details can be added here next.
          </AppText>
        </AppCard>
      </View>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingTop: 12, paddingBottom: 8 },
    body: { paddingHorizontal: 16, paddingBottom: 16 },
    card: { borderRadius: 18 },
    p: { marginTop: 6, color: colors.onSurfaceVariant, lineHeight: 18 },
  });
}
