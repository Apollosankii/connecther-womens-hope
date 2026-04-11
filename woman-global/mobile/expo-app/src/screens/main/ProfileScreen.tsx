import { StyleSheet, Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { getMyActiveSubscription } from '@/services/api/subscriptions';
import { useAuth } from '@/providers/AuthProvider';
import { Colors } from '@/theme/colors';

export function ProfileScreen() {
  const { user, signOut } = useAuth();
  const { data } = useQuery({
    queryKey: ['subscriptions', 'active'],
    queryFn: getMyActiveSubscription,
  });

  return (
    <Screen>
      <View style={styles.container}>
        <Text style={styles.title}>Profile</Text>
        <Text style={styles.label}>Signed in as</Text>
        <Text style={styles.value}>{user?.email ?? 'Unknown'}</Text>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Subscription</Text>
          <Text style={styles.cardValue}>
            {data ? `Plan #${data.plan_id} (${data.status ?? 'active'})` : 'No active subscription'}
          </Text>
        </View>

        <AppButton onPress={signOut} variant="outline">
          Sign out
        </AppButton>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    gap: 10,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: Colors.onBackground,
    marginBottom: 8,
  },
  label: {
    color: Colors.onSurfaceVariant,
    fontWeight: '600',
  },
  value: {
    color: Colors.onSurface,
    fontSize: 16,
    fontWeight: '700',
  },
  card: {
    marginTop: 12,
    backgroundColor: Colors.surface,
    borderColor: Colors.outline,
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    gap: 6,
  },
  cardTitle: {
    fontWeight: '800',
    color: Colors.onSurface,
  },
  cardValue: {
    color: Colors.onSurfaceVariant,
  },
});

