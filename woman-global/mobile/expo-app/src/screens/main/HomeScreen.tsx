import { MaterialCommunityIcons } from '@expo/vector-icons';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { CompositeNavigationProp } from '@react-navigation/native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { Image, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { SectionHeader } from '@/components/ui/SectionHeader';
import { ShockwaveRings } from '@/components/ui/ShockwaveRings';
import { useTheme } from '@/providers/ThemeProvider';
import type { AppStackParamList, MainTabParamList } from '@/navigation/types';
import { getMyUserProfile } from '@/services/api/profile';
import { Metrics } from '@/theme/metrics';

/** Home banner cards: 316×149dp max, centered, 8dp corner radius. */
const HOME_BANNER_IMAGE_WIDTH = 316;
const HOME_BANNER_IMAGE_HEIGHT = 149;
const HOME_BANNER_BORDER_RADIUS = 8;

type HomeNav = CompositeNavigationProp<
  BottomTabNavigationProp<MainTabParamList, 'Home'>,
  NativeStackNavigationProp<AppStackParamList>
>;

export function HomeScreen() {
  const navigation = useNavigation<HomeNav>();
  const { colors, toggle, effectiveMode } = useTheme();
  const { width: windowWidth } = useWindowDimensions();

  const profileQ = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyUserProfile });
  const isProvider = Boolean(profileQ.data?.service_provider);
  const isPendingApplication = Boolean(profileQ.data?.provider_application_pending);

  const bodyPadX = 12;
  const bannerWidth = Math.min(HOME_BANNER_IMAGE_WIDTH, windowWidth - bodyPadX * 2);
  const bannerLayout = { width: bannerWidth, height: HOME_BANNER_IMAGE_HEIGHT };

  return (
    <Screen padded={false} scroll useFloatingTabBarInset>
      <View style={styles.root}>
        <View style={styles.appBar}>
          <Image
            source={require('../../../assets/connecther-mark.png')}
            style={styles.logo}
            resizeMode="contain"
            accessibilityLabel="ConnectHer"
          />
          <AppText variant="appTitle" style={[styles.appName, { color: colors.onBackground }]}>
            ConnectHer
          </AppText>
          <View style={{ flex: 1 }} />
          <IconButton onPress={toggle} accessibilityLabel="Toggle theme">
            <MaterialCommunityIcons
              name={effectiveMode === 'dark' ? 'white-balance-sunny' : 'moon-waning-crescent'}
              size={20}
              color={colors.onPrimary}
            />
          </IconButton>
        </View>

        <View style={styles.body}>
          <AppCard style={styles.welcomeCard} padding="none">
            <View style={styles.welcomeMedia}>
              <Image
                source={{ uri: 'https://images.unsplash.com/photo-1521791055366-0d553872125f?auto=format&fit=crop&w=1200&q=60' }}
                style={styles.welcomeImg}
                resizeMode="cover"
                accessibilityIgnoresInvertColors
              />
              <View style={styles.welcomeOverlay}>
                <AppText style={[styles.welcomeText, { color: colors.onPrimary }]}>
                  Welcome to ConnectHer 👋
                </AppText>
              </View>
            </View>
          </AppCard>

          <AppText
            variant="h3"
            style={{ color: colors.onBackground, marginTop: 12 }}
            accessibilityRole="header"
          >
            Safety first — help one tap away
          </AppText>
          <AppText variant="body" style={{ marginTop: 4, marginBottom: 14 }}>
            Use SOS for emergencies. Browse services below to book vetted providers.
          </AppText>

          <AppCard variant="elevated" style={styles.sosCard} padding="none">
            <View style={styles.sosFrame}>
              <View style={styles.sosStage}>
                <ShockwaveRings
                  outerSize={176}
                  middleSize={152}
                  outerColor={colors.sos.pulse_outer}
                  middleColor={colors.sos.pulse_middle}
                  style={styles.sosRings}
                />
                <Pressable
                  style={[styles.sosButton, { backgroundColor: colors.primary }]}
                  accessibilityRole="button"
                  onPress={() => navigation.navigate('Panic')}
                >
                  <MaterialCommunityIcons name="shield-outline" size={36} color={colors.onPrimary} />
                  <AppText variant="rowTitle" style={styles.sosLabel}>
                    SOS
                  </AppText>
                </Pressable>
              </View>
            </View>
          </AppCard>

          <Pressable onPress={() => navigation.navigate('EmergencyContacts')} accessibilityRole="button">
            <AppCard style={styles.emergencyRow} padding="md">
              <View style={styles.emergencyRowInner}>
                <View style={styles.emergencyIconWrap}>
                  <MaterialCommunityIcons name="phone" size={22} color={colors.primary} />
                </View>
                <View style={{ flex: 1 }}>
                  <AppText variant="rowTitle">Emergency contacts</AppText>
                  <AppText variant="caption" style={{ marginTop: 2 }}>
                    Helplines &amp; support
                  </AppText>
                </View>
                <MaterialCommunityIcons name="open-in-new" size={20} color={colors.onSurfaceVariant} />
              </View>
            </AppCard>
          </Pressable>

          <SectionHeader
            overline="Services"
            title="Book a provider"
            actionLabel="See all"
            onPressAction={() => navigation.navigate('AllServices')}
            style={{ marginTop: 6 }}
          />

          <View style={styles.homeBannerGroup}>
            <HomeImageButton
              label="Our Services"
              imageSource={require('../../../assets/home-our-services.png')}
              layout={bannerLayout}
              onPress={() => navigation.navigate('Services')}
            />

            <HomeImageButton
              label={isProvider ? 'Manage profile' : 'Become a provider'}
              imageSource={require('../../../assets/home-become-provider.png')}
              layout={bannerLayout}
              onPress={() =>
                isProvider
                  ? navigation.navigate('ManageProviderProfile')
                  : navigation.navigate('ProviderApplication')
              }
              accessibilityHint={
                isProvider
                  ? 'Opens your provider profile and availability settings'
                  : isPendingApplication
                    ? 'Opens your pending provider application'
                    : 'Starts the provider application'
              }
            />
          </View>
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    // background is handled by Screen safe area
  },
  appBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingTop: 8,
    paddingBottom: 8,
  },
  logo: {
    width: 36,
    height: 36,
  },
  appName: {
    marginLeft: 10,
  },
  body: {
    paddingHorizontal: 12,
    paddingBottom: 16,
  },
  welcomeCard: {
    borderRadius: 18,
    overflow: 'hidden',
    marginBottom: 0,
  },
  welcomeMedia: {
    height: 130,
  },
  welcomeImg: {
    width: '100%',
    height: '100%',
  },
  welcomeOverlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(233,30,99,0.25)',
  },
  welcomeText: {
    fontWeight: '900',
    fontSize: 22,
    textAlign: 'center',
    letterSpacing: 0.2,
  },
  sosCard: {
    borderRadius: Metrics.radiusLg,
    marginBottom: 12,
  },
  sosFrame: {
    minHeight: 190,
    paddingVertical: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sosStage: {
    width: 176,
    height: 176,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sosRings: {
    left: 0,
    top: 0,
  },
  sosButton: {
    width: 112,
    height: 112,
    borderRadius: 56,
    // set inline via theme where needed
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
    shadowColor: '#000',
    shadowOpacity: 0.18,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
    elevation: 6,
  },
  sosLabel: {
    color: '#FFFFFF',
    letterSpacing: 0.08,
    fontSize: 16,
    fontWeight: '800',
  },
  emergencyRow: {
    borderRadius: 16,
    marginBottom: 16,
  },
  emergencyRowInner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  emergencyIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#EAF2FF',
  },
  homeBannerGroup: {
    alignItems: 'center',
    marginTop: 12,
    marginBottom: 24,
    gap: 12,
  },
  homeBannerButton: {
    borderRadius: HOME_BANNER_BORDER_RADIUS,
    overflow: 'hidden',
    alignSelf: 'center',
    backgroundColor: '#F5F5F5',
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
  },
  homeBannerPressed: {
    opacity: 0.92,
    transform: [{ scale: 0.99 }],
  },
  homeBannerImage: {
    width: '100%',
    height: '100%',
  },
  homeBannerLabelWrap: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    paddingHorizontal: 18,
    backgroundColor: 'rgba(233, 30, 99, 0.82)',
  },
  homeBannerLabel: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '800',
    letterSpacing: 0.2,
  },
});

type HomeImageButtonProps = {
  label: string;
  imageSource: number;
  layout: { width: number; height: number };
  onPress: () => void;
  style?: object;
  accessibilityHint?: string;
};

/** Full-width image card with bottom label bar — used for Our Services & Become a provider. */
function HomeImageButton({ label, imageSource, layout, onPress, style, accessibilityHint }: HomeImageButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      onPress={onPress}
      style={({ pressed }) => [
        styles.homeBannerButton,
        layout,
        style,
        pressed && styles.homeBannerPressed,
      ]}
    >
      <Image
        source={imageSource}
        style={styles.homeBannerImage}
        resizeMode="cover"
        accessibilityIgnoresInvertColors
      />
      <View style={styles.homeBannerLabelWrap}>
        <AppText variant="rowTitle" style={styles.homeBannerLabel}>
          {label}
        </AppText>
        <MaterialCommunityIcons name="chevron-right" size={22} color="#FFFFFF" />
      </View>
    </Pressable>
  );
}

