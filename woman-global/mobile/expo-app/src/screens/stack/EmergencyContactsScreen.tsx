import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useCallback, useEffect, useState } from 'react';
import { Alert, FlatList, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { TextField } from '@/components/ui/TextField';
import { useTheme } from '@/providers/ThemeProvider';
import {
  addEmergencyContact,
  getEmergencyContacts,
  removeEmergencyContact,
  type EmergencyContact,
} from '@/services/storage/emergencyContacts';
import { Metrics } from '@/theme/metrics';

function newId(): string {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function EmergencyContactsScreen({ navigation }: any) {
  const { colors } = useTheme();
  const [contacts, setContacts] = useState<EmergencyContact[]>([]);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setContacts(await getEmergencyContacts());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const onAdd = async () => {
    const n = name.trim();
    const p = phone.trim();
    if (!n || !p) {
      Alert.alert('Add contact', 'Enter a name and phone number.');
      return;
    }
    if (contacts.length >= 5) {
      Alert.alert('Limit reached', 'You can add up to 5 emergency contacts.');
      return;
    }
    const ok = await addEmergencyContact({ id: newId(), name: n, phone: p });
    if (!ok) {
      Alert.alert('Limit reached', 'You can add up to 5 emergency contacts.');
      return;
    }
    setName('');
    setPhone('');
    await reload();
  };

  const onRemove = async (id: string) => {
    await removeEmergencyContact(id);
    await reload();
  };

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
        <AppText variant="h3" style={{ flex: 1 }}>
          Emergency contacts
        </AppText>
      </View>

      <View style={styles.body}>
        <AppText variant="body" style={{ marginBottom: 12 }}>
          These contacts receive GBV panic SMS (ConnectHer may send SMS alerts automatically, or your phone may prompt
          you to send them from your device).
        </AppText>

        <AppCard style={{ marginBottom: 12 }} padding="md">
          <TextField label="Name" value={name} onChangeText={setName} autoCapitalize="words" />
          <TextField label="Phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
          <AppButton onPress={() => void onAdd()} disabled={loading}>
            Add contact
          </AppButton>
        </AppCard>

        <AppText variant="sectionTitle" style={{ marginBottom: 8 }}>
          Saved ({contacts.length}/5)
        </AppText>

        <FlatList
          data={contacts}
          keyExtractor={(item) => item.id}
          refreshing={loading}
          onRefresh={() => void reload()}
          ListEmptyComponent={
            <AppText variant="caption" style={{ color: colors.onSurfaceVariant }}>
              No contacts yet.
            </AppText>
          }
          renderItem={({ item }) => (
            <AppCard style={{ marginBottom: 8 }} padding="md">
              <View style={styles.row}>
                <View style={{ flex: 1 }}>
                  <AppText variant="rowTitle">{item.name}</AppText>
                  <AppText variant="caption" style={{ marginTop: 4 }}>
                    {item.phone}
                  </AppText>
                </View>
                <Pressable onPress={() => void onRemove(item.id)} accessibilityRole="button" hitSlop={10}>
                  <MaterialCommunityIcons name="delete-outline" size={22} color={colors.bookingStatus.declinedText} />
                </Pressable>
              </View>
            </AppCard>
          )}
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: Metrics.headerPaddingX,
    paddingTop: 12,
    paddingBottom: 8,
  },
  body: {
    flex: 1,
    paddingHorizontal: Metrics.headerPaddingX,
    paddingBottom: 16,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
});
