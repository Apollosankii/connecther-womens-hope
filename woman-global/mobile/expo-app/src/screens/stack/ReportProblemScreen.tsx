import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMutation } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { TextField } from '@/components/ui/TextField';
import { useTheme } from '@/providers/ThemeProvider';
import { reportProblem } from '@/services/api/support';
import type { ThemeColors } from '@/theme/types';

export function ReportProblemScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const [text, setText] = useState('');

  const mut = useMutation({
    mutationFn: () => reportProblem(text),
    onSuccess: (ok) => {
      if (ok) {
        Alert.alert('Sent', 'Thanks. Your report has been submitted.');
        navigation.goBack();
      } else {
        Alert.alert('Failed', 'Could not submit your report.');
      }
    },
    onError: (e) => Alert.alert('Failed', e instanceof Error ? e.message : 'Unknown error'),
  });

  return (
    <Screen padded={false} scroll>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3">Report a Problem</AppText>
      </View>
      <View style={styles.body}>
        <AppCard padding="md" style={styles.card}>
          <AppText variant="caption" style={{ color: colors.onSurfaceVariant }}>
            Describe what happened.
          </AppText>
          <View style={{ height: 8 }} />
          <TextField label="Problem" value={text} onChangeText={setText} multiline placeholder="e.g., I can’t open provider documents…" />
          <View style={{ height: 12 }} />
          <AppButton onPress={() => mut.mutate()} loading={mut.isPending} disabled={!text.trim()}>
            Submit
          </AppButton>
        </AppCard>
      </View>
    </Screen>
  );
}

function makeStyles(_colors: ThemeColors) {
  return StyleSheet.create({
    header: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingTop: 12, paddingBottom: 8 },
    body: { paddingHorizontal: 16, paddingBottom: 16 },
    card: { borderRadius: 16 },
  });
}
