import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import * as Location from 'expo-location';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { ShockwaveRings } from '@/components/ui/ShockwaveRings';
import { useTheme } from '@/providers/ThemeProvider';
import { sendPanicSmsViaEdge } from '@/services/api/panicSms';
import { getMyUserProfile } from '@/services/api/profile';
import { reportGbvEmergency } from '@/services/api/support';
import { getFirebaseAuth } from '@/services/firebaseAuth';
import { sendGbvDeviceSms } from '@/services/sms/gbvDeviceSms';
import { ensureSupabaseSession } from '@/services/supabase/session';
import { getEmergencyContacts } from '@/services/storage/emergencyContacts';
import { Metrics } from '@/theme/metrics';
import type { ThemeColors } from '@/theme/types';
import { normalizeKenyaE164 } from '@/utils/phone';

const HELPLINE_DIAL = '0800720565'; // Android: helpline_nairobi_womens_number_dial

const E164_RE = /^\+[1-9]\d{6,14}$/;

export function PanicScreen({ navigation }: any) {
  const [isGbvSelected, setIsGbvSelected] = useState(true);
  const [busy, setBusy] = useState(false);
  const { colors } = useTheme();

  const subtitle = useMemo(() => 'Select emergency type and press the\npanic button', []);

  async function triggerGbvPanic() {
    setBusy(true);
    try {
      const sessionOk = await ensureSupabaseSession();
      if (!sessionOk) {
        Alert.alert('Session', 'Could not refresh your session. Please sign in again.');
        return;
      }

      const perm = await Location.requestForegroundPermissionsAsync();
      let lat: number | null = null;
      let lng: number | null = null;
      if (perm.status === 'granted') {
        try {
          const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
          lat = pos.coords.latitude;
          lng = pos.coords.longitude;
        } catch {
          /* ignore */
        }
      }

      const reportOk = await reportGbvEmergency({
        latitude: lat,
        longitude: lng,
        locationText: lat != null && lng != null ? `${lat},${lng}` : null,
      });
      Alert.alert(
        reportOk ? 'Emergency report sent' : 'Could not send report',
        reportOk ? 'Authorities have been notified.' : 'Try again in a moment.',
      );

      const contacts = await getEmergencyContacts();
      if (!contacts.length) {
        Alert.alert(
          'Emergency contacts',
          'Add up to 5 trusted contacts so they can be notified. Open Emergency contacts from Home.',
        );
        return;
      }

      const recipientsE164 = [
        ...new Set(
          contacts
            .map((c) => normalizeKenyaE164(c.phone))
            .filter((p): p is string => !!p && E164_RE.test(p)),
        ),
      ].slice(0, 5);

      if (!recipientsE164.length) {
        Alert.alert(
          'Invalid phone numbers',
          'Fix your emergency contact numbers (use formats like 0712…, 2547…, or +254…).',
        );
        return;
      }

      const rawPhones = contacts.map((c) => c.phone.trim()).filter(Boolean).slice(0, 5);

      let displayName = 'A ConnectHer user';
      try {
        const profile = await getMyUserProfile();
        if (profile) {
          const fn = (profile.first_name ?? '').trim();
          const ln = (profile.last_name ?? '').trim();
          const joined = [fn, ln].filter(Boolean).join(' ').trim();
          if (joined) displayName = joined;
        }
      } catch {
        /* keep default */
      }

      const firebaseUser = getFirebaseAuth().currentUser;

      if (firebaseUser) {
        const edge = await sendPanicSmsViaEdge({
          recipientsE164,
          latitude: lat,
          longitude: lng,
        });
        if (edge.ok) {
          Alert.alert('Contacts notified', 'ConnectHer sent SMS alerts to your emergency contacts.');
          return;
        }
        if (edge.code === 'TWILIO_DISABLED' || edge.code === 'NOT_SUBSCRIBED') {
          const dev = await sendGbvDeviceSms({
            phones: rawPhones,
            displayName,
            latitude: lat,
            longitude: lng,
          });
          if (dev.ok) Alert.alert('Contacts notified', 'SMS was sent from your device.');
          else if (dev.reason === 'cancelled') Alert.alert('SMS', 'Sending was cancelled.');
          else Alert.alert('SMS', 'Could not send SMS from this device.');
          return;
        }
        if (
          edge.code === 'RATE_LIMIT' ||
          edge.code === 'RATE_LIMIT_COOLDOWN' ||
          edge.code === 'RATE_LIMIT_GLOBAL'
        ) {
          Alert.alert('Rate limit', edge.message);
          return;
        }
        if (edge.code === 'NO_FIREBASE_USER') {
          Alert.alert('Sign in required', edge.message);
          return;
        }
        Alert.alert('ConnectHer SMS failed', edge.message);
        return;
      }

      const dev = await sendGbvDeviceSms({
        phones: rawPhones,
        displayName,
        latitude: lat,
        longitude: lng,
      });
      if (dev.ok) Alert.alert('Contacts notified', 'SMS was sent from your device.');
      else if (dev.reason === 'cancelled') Alert.alert('SMS', 'Sending was cancelled.');
      else Alert.alert('SMS', 'Could not send SMS. Check numbers and try again.');
    } catch (e) {
      Alert.alert('Emergency', e instanceof Error ? e.message : 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  }

  async function triggerMedical() {
    await Linking.openURL(`tel:${HELPLINE_DIAL}`);
  }

  function triggerPanic() {
    if (busy) return;
    if (isGbvSelected) void triggerGbvPanic();
    else triggerMedical().catch(() => Alert.alert('Hotline', 'Could not open dialer.'));
  }

  return (
    <Screen padded={false}>
      <View style={styles.topBar}>
        <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
        </IconButton>
      </View>

      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <AppText variant="h2" style={styles.title}>
          Emergency Panic
        </AppText>
        <AppText variant="body" style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
          {subtitle}
        </AppText>

        <View style={styles.cardRow}>
          <EmergencyTypeCard
            title="GBV Emergency"
            body="Gender-based\nviolence support"
            icon="shield-alert-outline"
            selected={isGbvSelected}
            onPress={() => setIsGbvSelected(true)}
            colors={colors}
          />
          <EmergencyTypeCard
            title="Medical"
            body="Medical emergency\nhelp"
            icon="heart-outline"
            selected={!isGbvSelected}
            onPress={() => setIsGbvSelected(false)}
            colors={colors}
          />
        </View>

        <View style={styles.panicFrame}>
          <ShockwaveRings
            outerSize={220}
            middleSize={190}
            outerColor={colors.sos.pulse_outer}
            middleColor={colors.sos.pulse_middle}
            style={{ left: '50%', top: '50%', transform: [{ translateX: -110 }, { translateY: -110 }] }}
          />
          <Pressable
            onPress={triggerPanic}
            accessibilityRole="button"
            style={[styles.panicButton, busy && { opacity: 0.65 }]}
            disabled={busy}
          >
            {busy ? (
              <ActivityIndicator color={colors.onPrimary} />
            ) : (
              <>
                <MaterialCommunityIcons name="alert" size={40} color={colors.onPrimary} />
                <AppText style={styles.panicText}>PANIC</AppText>
              </>
            )}
          </Pressable>
        </View>

        <Pressable onPress={() => navigation?.navigate?.('EmergencyContacts')} accessibilityRole="button">
          <AppText variant="link" style={styles.manageLink}>
            Manage emergency contacts
          </AppText>
        </Pressable>

        <AppText variant="caption" style={[styles.footer, { color: colors.onSurfaceVariant }]}>
          Press the panic button to alert emergency services.{'\n'}Your location will be shared when permitted.
        </AppText>
      </ScrollView>
    </Screen>
  );
}

function EmergencyTypeCard({
  title,
  body,
  icon,
  selected,
  onPress,
  colors,
}: {
  title: string;
  body: string;
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  selected: boolean;
  onPress: () => void;
  colors: ThemeColors;
}) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={{ flex: 1 }}>
      <AppCard
        padding="md"
        style={[
          styles.typeCard,
          selected
            ? { borderWidth: 1, borderColor: colors.primary }
            : { borderWidth: 1, borderColor: colors.outlineSoft },
          { minHeight: 140 },
        ]}
      >
        <View style={styles.typeHeader}>
          <View style={[styles.typeIcon, { backgroundColor: colors.surfaceVariant, borderColor: colors.outlineSoft }]}>
            <MaterialCommunityIcons name={icon} size={26} color={selected ? colors.primary : colors.onSurfaceVariant} />
          </View>
          <View style={{ flex: 1 }} />
          {selected ? (
            <MaterialCommunityIcons name="check-circle" size={20} color={colors.primary} />
          ) : (
            <View style={{ width: 20, height: 20 }} />
          )}
        </View>
        <AppText variant="bodyStrong" style={[styles.typeTitle, selected && { color: colors.primary }]}>
          {title}
        </AppText>
        <AppText variant="caption" style={[styles.typeBody, { color: colors.onSurfaceVariant }]}>
          {body}
        </AppText>
      </AppCard>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  topBar: {
    paddingStart: 8,
    paddingEnd: Metrics.headerPaddingX,
    paddingTop: 12,
    paddingBottom: 4,
    flexDirection: 'row',
    alignItems: 'center',
  },
  scroll: {
    flexGrow: 1,
    alignItems: 'center',
    paddingBottom: 24,
  },
  title: {
    marginTop: 4,
    textAlign: 'center',
  },
  subtitle: {
    marginTop: 8,
    marginBottom: 18,
    textAlign: 'center',
    paddingHorizontal: 32,
  },
  cardRow: {
    flexDirection: 'row',
    gap: 12,
    paddingHorizontal: Metrics.headerPaddingX,
    width: '100%',
    marginBottom: 24,
  },
  typeCard: {
    borderRadius: 18,
  },
  typeHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  typeIcon: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  typeTitle: {
    textAlign: 'center',
    fontWeight: '800',
  },
  typeBody: {
    marginTop: 6,
    textAlign: 'center',
  },
  panicFrame: {
    width: '100%',
    minHeight: 230,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  panicButton: {
    width: 160,
    height: 160,
    borderRadius: 80,
    backgroundColor: '#D32F2F',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    shadowColor: '#000',
    shadowOpacity: 0.18,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 8 },
    elevation: 8,
  },
  panicText: {
    color: '#FFFFFF',
    fontSize: 20,
    fontWeight: '900',
    letterSpacing: 0.9,
  },
  manageLink: {
    marginBottom: 12,
    textAlign: 'center',
  },
  footer: {
    textAlign: 'center',
    paddingHorizontal: 32,
    lineHeight: 18,
  },
});
