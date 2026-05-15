import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { ListEmpty } from '@/components/ui/ListEmpty';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { listServices } from '@/services/api/services';
import { Spacing } from '@/theme/spacing';
import type { ThemeColors } from '@/theme/types';
type Props = NativeStackScreenProps<AppStackParamList, 'AllServices'>;

export function AllServicesScreen({ navigation }: Props) {
  const { colors } = useTheme();
  const canvas = colors.softCanvas;
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['services'], queryFn: listServices });
  const services = data ?? [];
  return (
    <Screen padded={false} safeAreaBackground={canvas}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <IconButton
            variant="surface"
            accessibilityLabel="Back"
            onPress={() => navigation.goBack()}
            style={{ borderRadius: 12 }}
          >
            <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
          </IconButton>
          <View style={{ flex: 1 }}>
            <AppText variant="h3">Services</AppText>
            <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }}>
              Choose a category, then pick a professional.
            </AppText>
          </View>
        </View>
      </View>

      {error ? (
        <AppText style={styles.error} onPress={() => refetch()}>
          Failed to load services. Tap to retry.
        </AppText>
      ) : null}

      <FlatList
        data={services}
        keyExtractor={(s) => String(s.id)}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          isLoading ? null : <ListEmpty title="No services found" body="Try again later or check your connection." />
        }
        renderItem={({ item }) => (
          <Pressable
            onPress={() => navigation.navigate('ServiceMenu', { serviceId: item.id, serviceName: item.name })}
          >
            <AppCard padding="sm" style={styles.row}>
              <AppText variant="rowTitle">{item.name}</AppText>
              <AppText variant="caption" style={{ marginTop: 2, color: colors.onSurfaceVariant }} numberOfLines={2}>
                {item.description || 'Trusted local provider available for this service.'}
              </AppText>
            </AppCard>
          </Pressable>
        )}
      />
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
    },
    headerLeft: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
    },
    error: {
      color: colors.bookingStatus.declinedText,
      marginHorizontal: 16,
      marginBottom: 8,
      fontWeight: '600',
    },
    list: {
      paddingHorizontal: 12,
      paddingTop: 12,
      paddingBottom: 16,
      gap: Spacing.md,
    },
    row: {
      borderRadius: 22,
    },
  });
}
