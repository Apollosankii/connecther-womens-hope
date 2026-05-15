import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as ImagePicker from 'expo-image-picker';
import { useMemo } from 'react';
import { Alert, Image, Pressable, StyleSheet, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';

import { Screen } from '@/components/layout/Screen';
import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { getMyUserProfile, uploadProfilePic } from '@/services/api/profile';
import { getProfilePicVersion } from '@/services/supabase/tokenStore';
import { useAuth } from '@/providers/AuthProvider';
import { useTheme } from '@/providers/ThemeProvider';
import type { AppStackParamList } from '@/navigation/types';
import type { ThemeColors } from '@/theme/types';

export function ProfileScreen() {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { firebaseUser, signOut } = useAuth();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList>>();
  const profileQ = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: getMyUserProfile,
  });
  const picVerQ = useQuery({
    queryKey: ['profile', 'picVersion'],
    queryFn: getProfilePicVersion,
  });

  const me = profileQ.data;
  const isProvider = Boolean(me?.service_provider);
  const isPending = Boolean(me?.provider_application_pending);

  const displayName =
    [me?.first_name, me?.last_name].filter(Boolean).join(' ').trim() || (firebaseUser?.email?.split('@')[0] ?? 'User');
  const displayEmail = me?.email ?? firebaseUser?.email ?? 'Unknown';
  const rawPhotoUrl = me?.prof_pic ?? me?.pic ?? null;
  const photoUrl =
    rawPhotoUrl && picVerQ.data
      ? `${String(rawPhotoUrl)}${String(rawPhotoUrl).includes('?') ? '&' : '?'}v=${picVerQ.data}`
      : rawPhotoUrl;

  async function pickAndUploadPhoto() {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) return;
    const res = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.85,
    });
    if (res.canceled) return;
    const asset = res.assets?.[0];
    if (!asset?.uri) return;
    const fileName = asset.fileName || `profile_${Date.now()}.jpg`;
    try {
      await uploadProfilePic({ fileUri: asset.uri, fileName });
      await profileQ.refetch();
      await picVerQ.refetch();
      Alert.alert('Profile photo', 'Your picture was updated.');
    } catch (e) {
      Alert.alert('Upload failed', e instanceof Error ? e.message : 'Unknown error');
    }
  }

  function openProviderProfile() {
    navigation.navigate('ManageProviderProfile');
  }

  return (
    <Screen padded={false} scroll useFloatingTabBarInset>
      <View style={styles.root}>
        <View style={styles.topSurface} />

        <View style={styles.content}>
          <Pressable accessibilityRole="button" onPress={pickAndUploadPhoto} style={styles.avatarWrap}>
            <Image
              source={photoUrl ? { uri: String(photoUrl) } : require('../../../assets/icon.png')}
              style={styles.avatar}
            />
            <View style={styles.avatarEdit}>
              <MaterialCommunityIcons name="camera" size={16} color={colors.onPrimary} />
            </View>
          </Pressable>
          <AppText variant="h3" style={[styles.name, { color: colors.onBackground }]}>
            {displayName}
          </AppText>
          <AppText variant="body" style={[styles.meta, { color: colors.onSurfaceVariant }]}>
            {displayEmail}
          </AppText>

          <AppCard style={styles.optionsCard} padding="none">
            <ProfileOption colors={colors} label="Notifications" icon="email-outline" onPress={() => navigation.navigate('Notifications')} />
            <ProfileOption
              colors={colors}
              label="Subscriptions"
              icon="credit-card-outline"
              onPress={() => navigation.navigate('Subscriptions')}
            />
            <ProfileOption colors={colors} label="Profile & account" icon="account-edit-outline" onPress={() => navigation.navigate('Settings')} />
            <ProfileOption colors={colors} label="Terms and policies" icon="file-document-outline" onPress={() => navigation.navigate('Terms')} />
            <ProfileOption colors={colors} label="Report a problem" icon="alert-circle-outline" onPress={() => navigation.navigate('ReportProblem')} />
            <ProfileOption colors={colors} label="Change password" icon="lock-outline" onPress={() => navigation.navigate('PasswordChange')} />
            {isProvider ? (
              <ProfileOption colors={colors} label="Provider profile & availability" icon="account-outline" onPress={openProviderProfile} />
            ) : isPending ? (
              <ProfileOption colors={colors} label="Application Pending" icon="clock-outline" onPress={() => {}} disabled />
            ) : (
              <ProfileOption
                colors={colors}
                label="Become a Provider"
                icon="briefcase-outline"
                onPress={() => navigation.navigate('ProviderApplication')}
              />
            )}
            {isProvider ? (
              <ProfileOption
                colors={colors}
                label="Manage documents"
                icon="file-upload-outline"
                onPress={() => navigation.navigate('ManageProviderDocuments')}
              />
            ) : null}
            <ProfileOption colors={colors} label="Logout" icon="close" onPress={signOut} last />
          </AppCard>
        </View>
      </View>
    </Screen>
  );
}

function ProfileOption({
  label,
  icon,
  onPress,
  last,
  colors,
  disabled,
}: {
  label: string;
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  onPress: () => void;
  last?: boolean;
  colors: ThemeColors;
  disabled?: boolean;
}) {
  const styles = useMemo(() => makeStyles(colors), [colors]);
  return (
    <Pressable
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.optionRow,
        last && styles.optionLast,
        pressed && !disabled && { opacity: 0.9 },
        disabled && { opacity: 0.55 },
      ]}
    >
      <MaterialCommunityIcons name={icon} size={22} color={colors.onSurfaceVariant} />
      <AppText variant="bodyStrong" style={{ flex: 1, color: colors.onSurface }}>
        {label}
      </AppText>
      <MaterialCommunityIcons name="chevron-right" size={22} color={colors.onSurfaceVariant} />
    </Pressable>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    root: {
      flex: 1,
      backgroundColor: colors.background,
    },
    topSurface: {
      height: 150,
      backgroundColor: colors.surfaceVariant,
    },
    content: {
      marginTop: -50,
      paddingHorizontal: 16,
      paddingBottom: 16,
      alignItems: 'center',
    },
    avatarWrap: {
      width: 100,
      height: 100,
      borderRadius: 50,
      marginBottom: 16,
    },
    avatar: {
      width: 100,
      height: 100,
      borderRadius: 50,
      borderWidth: 2,
      borderColor: colors.surface,
      backgroundColor: colors.surface,
    },
    avatarEdit: {
      position: 'absolute',
      right: -2,
      bottom: -2,
      width: 32,
      height: 32,
      borderRadius: 16,
      backgroundColor: colors.primary,
      alignItems: 'center',
      justifyContent: 'center',
      borderWidth: 2,
      borderColor: colors.surface,
      shadowColor: '#000',
      shadowOpacity: 0.14,
      shadowRadius: 10,
      shadowOffset: { width: 0, height: 6 },
      elevation: 4,
    },
    name: {
      textAlign: 'center',
    },
    meta: {
      textAlign: 'center',
      marginTop: 6,
      marginBottom: 16,
    },
    optionsCard: {
      alignSelf: 'stretch',
      borderRadius: 18,
      overflow: 'hidden',
    },
    optionRow: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 16,
      paddingVertical: 14,
      gap: 12,
      borderBottomWidth: 1,
      borderBottomColor: colors.outlineSoft,
      backgroundColor: colors.surface,
    },
    optionLast: {
      borderBottomWidth: 0,
    },
    subCard: {
      alignSelf: 'stretch',
      borderRadius: 18,
      marginTop: 16,
    },
  });
}
