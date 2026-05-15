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
import { firebaseUpdatePasswordWithCurrent } from '@/services/firebaseAuth';
import type { ThemeColors } from '@/theme/types';

function mapPasswordError(e: unknown): string {
  const code = typeof e === 'object' && e && 'code' in e ? String((e as { code?: string }).code) : '';
  if (code === 'auth/invalid-credential' || code === 'auth/wrong-password') return 'Current password is incorrect.';
  if (code === 'auth/weak-password') return 'New password is too weak.';
  if (code === 'auth/requires-recent-login') return 'Please sign out and sign in again, then try changing your password.';
  if (e instanceof Error) return e.message;
  return 'Could not update password.';
}

export function PasswordChangeScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');

  const mut = useMutation({
    mutationFn: async () => {
      if (!current.trim()) throw new Error('Enter your current password.');
      if (!next.trim() || !confirm.trim()) throw new Error('Enter and confirm your new password.');
      if (next !== confirm) throw new Error('New passwords do not match.');
      if (next.length < 6) throw new Error('New password must be at least 6 characters.');
      await firebaseUpdatePasswordWithCurrent(current.trim(), next.trim());
    },
    onSuccess: () => {
      Alert.alert('Success', 'Your password has been updated.');
      navigation.goBack();
    },
    onError: (e) => Alert.alert('Failed', mapPasswordError(e)),
  });

  return (
    <Screen padded={false} scroll>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3">Change Password</AppText>
      </View>
      <View style={styles.body}>
        <AppCard padding="md" style={styles.card}>
          <AppText variant="caption" style={{ color: colors.onSurfaceVariant }}>
            Enter your current password, then choose a new one. Google-only accounts may need to add a password from Firebase or use reset email from login.
          </AppText>
          <View style={{ height: 10 }} />
          <TextField label="Current password" value={current} onChangeText={setCurrent} autoCapitalize="none" secureTextEntry />
          <TextField label="New password" value={next} onChangeText={setNext} autoCapitalize="none" secureTextEntry />
          <TextField label="Confirm new password" value={confirm} onChangeText={setConfirm} autoCapitalize="none" secureTextEntry />
          <View style={{ height: 14 }} />
          <AppButton
            onPress={() => mut.mutate()}
            loading={mut.isPending}
            disabled={!current.trim() || !next.trim() || !confirm.trim()}
          >
            Update password
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
    card: { borderRadius: 18 },
  });
}
