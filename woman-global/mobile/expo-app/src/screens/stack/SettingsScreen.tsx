import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { TextField } from '@/components/ui/TextField';
import { useTheme } from '@/providers/ThemeProvider';
import { getMyUserProfile, updateMyProfile } from '@/services/api/profile';
import type { ThemeColors } from '@/theme/types';

export function SettingsScreen({ navigation }: any) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const qc = useQueryClient();
  const meQ = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyUserProfile });
  const me = meQ.data;

  const initialName = useMemo(() => [me?.first_name, me?.last_name].filter(Boolean).join(' ').trim(), [me?.first_name, me?.last_name]);
  const [fullName, setFullName] = useState(initialName);
  const [email, setEmail] = useState(me?.email ?? '');
  const [phone, setPhone] = useState(me?.phone ?? '');
  const [title, setTitle] = useState((me as any)?.title ?? '');
  const [occupation, setOccupation] = useState((me as any)?.occupation ?? '');

  useEffect(() => {
    setFullName(initialName);
    setEmail(me?.email ?? '');
    setPhone(me?.phone ?? '');
    setTitle((me as any)?.title ?? '');
    setOccupation((me as any)?.occupation ?? '');
  }, [initialName, me]);

  const saveMut = useMutation({
    mutationFn: async () => {
      const name = fullName.trim();
      if (!name) throw new Error('Full name is required');
      if (!email.trim()) throw new Error('Email is required');
      const parts = name.split(/\s+/, 2);
      const first = parts[0] ?? '';
      const last = parts[1] ?? '';
      const ok = await updateMyProfile({
        firstName: first,
        lastName: last,
        email: email.trim(),
        phone: phone.trim(),
        occupation: occupation.trim(),
        title: title.trim(),
      });
      if (!ok) throw new Error('Could not save settings');
      await qc.invalidateQueries({ queryKey: ['profile', 'me'] });
      return true;
    },
    onSuccess: () => Alert.alert('Saved', 'Your profile has been updated.'),
    onError: (e) => Alert.alert('Save failed', e instanceof Error ? e.message : 'Unknown error'),
  });

  return (
    <Screen padded={false} scroll>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3">Settings</AppText>
      </View>
      <View style={styles.body}>
        <AppCard padding="md" style={styles.formCard}>
          <TextField label="Full name" value={fullName} onChangeText={setFullName} placeholder="Jane Doe" />
          <TextField label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
          <TextField label="Phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
          <TextField label="Title" value={title} onChangeText={setTitle} placeholder="e.g., Miss" />
          <TextField label="Occupation" value={occupation} onChangeText={setOccupation} placeholder="e.g., Housekeeper" />
          <View style={{ height: 10 }} />
          <AppButton onPress={() => saveMut.mutate()} loading={saveMut.isPending}>
            Save
          </AppButton>
        </AppCard>

        <View style={{ height: 12 }} />
        <AppCard padding="none" style={styles.card}>
          <Row colors={colors} label="Terms and policies" value="" onPress={() => navigation.navigate('Terms')} />
          <Row colors={colors} label="About" value="" onPress={() => navigation.navigate('AboutUs')} last />
        </AppCard>
      </View>
    </Screen>
  );
}

function Row({
  label,
  value,
  onPress,
  last,
  colors,
}: {
  label: string;
  value: string;
  onPress: () => void;
  last?: boolean;
  colors: ThemeColors;
}) {
  const styles = useMemo(() => makeStyles(colors), [colors]);
  return (
    <Pressable onPress={onPress} style={[styles.row, last && { borderBottomWidth: 0 }]} accessibilityRole="button">
      <AppText variant="bodyStrong" style={{ flex: 1 }}>
        {label}
      </AppText>
      {value ? <AppText variant="caption">{value}</AppText> : null}
      <MaterialCommunityIcons name="chevron-right" size={22} color={colors.onSurfaceVariant} />
    </Pressable>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingTop: 12, paddingBottom: 8 },
    body: { paddingHorizontal: 16, paddingBottom: 16 },
    formCard: { borderRadius: 18, gap: 12 },
    card: { borderRadius: 18, overflow: 'hidden' },
    row: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 10,
      paddingHorizontal: 16,
      paddingVertical: 14,
      borderBottomWidth: 1,
      borderBottomColor: colors.outlineSoft,
      backgroundColor: colors.surface,
    },
  });
}
