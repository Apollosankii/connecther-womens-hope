import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { FlatList, Image, Pressable, StyleSheet, TextInput, useWindowDimensions, View } from 'react-native';
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { ListEmpty } from '@/components/ui/ListEmpty';
import { Spinner } from '@/components/ui/Spinner';
import type { AppStackParamList } from '@/navigation/types';
import { useTheme } from '@/providers/ThemeProvider';
import { listServices } from '@/services/api/services';
import { Metrics } from '@/theme/metrics';
import type { Service } from '@/types/models';
import { getFloatingTabBarScrollInset } from '@/navigation/floatingTabBar';

const LIST_H_PAD = 12;
const COL_GAP = 12;

export function ServicesScreen() {
  const { width: windowWidth } = useWindowDimensions();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const { colors, toggle, effectiveMode } = useTheme();
  const canvas = colors.softCanvas;
  const insets = useSafeAreaInsets();
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['services'],
    queryFn: listServices,
  });

  const services = data ?? [];
  const count = services.length;

  const { cardWidth, fullRowWidth } = useMemo(() => {
    const inner = windowWidth - LIST_H_PAD * 2;
    const w = Math.floor((inner - COL_GAP) / 2);
    return { cardWidth: Math.max(w, 140), fullRowWidth: inner };
  }, [windowWidth]);

  return (
    <Screen padded={false} safeAreaBackground={canvas}>
      <View style={styles.header}>
        <View style={{ flex: 1 }}>
          <AppText variant="h2">Marketplace</AppText>
          <AppText variant="body" style={{ marginTop: 4, color: colors.onSurfaceVariant }}>
            Find and book trusted service providers
          </AppText>
        </View>
        <IconButton onPress={toggle} accessibilityLabel="Toggle theme">
          <MaterialCommunityIcons
            name={effectiveMode === 'dark' ? 'white-balance-sunny' : 'moon-waning-crescent'}
            size={18}
            color={colors.onPrimary}
          />
        </IconButton>
      </View>

      <AppCard style={styles.searchCard} padding="none">
        <View style={styles.searchInner}>
          <MaterialCommunityIcons name="magnify" size={20} color={colors.onSurfaceVariant} />
          <TextInput
            style={[styles.searchInput, { color: colors.onBackground }]}
            placeholder="Search services…"
            placeholderTextColor={colors.onSurfaceVariant}
          />
          <View style={[styles.micWrap, { backgroundColor: colors.primary }]}>
            <MaterialCommunityIcons name="microphone" size={18} color={colors.onPrimary} />
          </View>
        </View>
      </AppCard>

      {count > 0 ? (
        <AppText variant="caption" style={[styles.count, { color: colors.onSurfaceVariant }]}>
          {count} service{count === 1 ? '' : 's'} available
        </AppText>
      ) : null}

      {isLoading ? <Spinner /> : null}
      {error ? (
        <AppText style={[styles.error, { color: colors.bookingStatus.declinedText }]} onPress={() => refetch()}>
          Failed to load services. Tap to retry.
        </AppText>
      ) : null}
      <FlatList
        data={services}
        keyExtractor={(item) => String(item.id)}
        numColumns={2}
        columnWrapperStyle={[styles.columns, { gap: COL_GAP }]}
        contentContainerStyle={[
          styles.list,
          { paddingBottom: Math.max(16, getFloatingTabBarScrollInset(insets.bottom)) },
        ]}
        extraData={{ cardWidth, fullRowWidth, count }}
        ListEmptyComponent={
          isLoading ? null : <ListEmpty title="No services found" body="Try again later or check your connection." />
        }
        renderItem={({ item, index }) => {
          const lastOdd = count % 2 === 1 && index === count - 1;
          const tileWidth = lastOdd ? fullRowWidth : cardWidth;
          return (
            <ServiceCard
              item={item}
              cardWidth={tileWidth}
              onPress={() => navigation.navigate('ServiceMenu', { serviceId: item.id, serviceName: item.name })}
            />
          );
        }}
      />
    </Screen>
  );
}

function ServiceCard({ item, cardWidth, onPress }: { item: Service; cardWidth: number; onPress: () => void }) {
  const { colors } = useTheme();
  const price = item.min_price != null ? Math.round(item.min_price) : null;
  return (
    <Pressable onPress={onPress} accessibilityRole="button" style={{ width: cardWidth }}>
      <AppCard padding="none" style={[styles.card, { minHeight: 248 }]}>
        <View style={[styles.media, { backgroundColor: colors.surfaceVariant }]}>
          {item.service_pic ? (
            <Image source={{ uri: item.service_pic }} style={styles.mediaImg} resizeMode="cover" />
          ) : (
            <View style={styles.mediaFallback}>
              <MaterialCommunityIcons name="image-outline" size={34} color={colors.mutedIcon} />
            </View>
          )}
          {price != null ? (
            <View style={[styles.pricePill, { backgroundColor: colors.primary }]}>
              <AppText style={[styles.priceText, { color: colors.onPrimary }]}>KES {price}</AppText>
            </View>
          ) : null}
        </View>
        <View style={styles.cardBody}>
          <AppText variant="rowTitle" numberOfLines={1}>
            {item.name}
          </AppText>
          <AppText variant="caption" style={[styles.desc, { color: colors.onSurfaceVariant }]} numberOfLines={2}>
            {item.description || 'Trusted local provider available for this service.'}
          </AppText>
          {price != null ? (
            <AppText variant="caption" style={[styles.fromPrice, { color: colors.primaryVariant }]}>
              From KES {price}
            </AppText>
          ) : null}
        </View>
      </AppCard>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Metrics.headerPaddingX,
    paddingTop: 20,
    paddingBottom: 4,
    gap: 10,
  },
  searchCard: {
    marginHorizontal: 16,
    marginTop: 12,
    marginBottom: 8,
    borderRadius: 12,
  },
  searchInner: {
    height: 48,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
  },
  searchInput: {
    flex: 1,
    fontSize: 14,
  },
  micWrap: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.12,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
    elevation: 4,
  },
  count: {
    paddingHorizontal: Metrics.headerPaddingX,
    marginTop: 8,
    marginBottom: 4,
  },
  error: {
    marginHorizontal: Metrics.headerPaddingX,
    marginBottom: 6,
    fontWeight: '600',
  },
  list: {
    paddingHorizontal: LIST_H_PAD,
    paddingTop: 4,
    paddingBottom: 16,
    gap: COL_GAP,
  },
  columns: {
    justifyContent: 'flex-start',
  },
  card: {
    borderRadius: 18,
    overflow: 'hidden',
  },
  media: {
    height: Metrics.mediaHeightMd,
  },
  mediaImg: {
    width: '100%',
    height: '100%',
  },
  mediaFallback: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pricePill: {
    position: 'absolute',
    top: 10,
    right: 10,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: Metrics.radiusPill,
  },
  priceText: {
    fontWeight: '800',
    fontSize: 12,
  },
  cardBody: {
    padding: 12,
    gap: 4,
    minHeight: 88,
  },
  desc: {
    marginTop: 2,
  },
  fromPrice: {
    marginTop: 2,
    fontWeight: '700',
  },
});
