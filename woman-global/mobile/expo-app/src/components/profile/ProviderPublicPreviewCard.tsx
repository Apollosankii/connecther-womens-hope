import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Image, StyleSheet, View } from 'react-native';

import { AppCard } from '@/components/ui/AppCard';
import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/providers/ThemeProvider';
import type { UserProfile } from '@/types/models';
import type { ThemeColors } from '@/theme/types';

type Props = {
  profile: UserProfile | null | undefined;
  headline: string;
  experience: string;
  workingHours: string;
  photoUrl?: string | null;
};

function notSpecified(value: string) {
  const v = value.trim();
  return v.length ? v : 'Not specified';
}

export function ProviderPublicPreviewCard({ profile, headline, experience, workingHours, photoUrl }: Props) {
  const { colors } = useTheme();
  const styles = makeStyles(colors);

  const displayName =
    [profile?.first_name, profile?.last_name].filter(Boolean).join(' ').trim() ||
    profile?.email?.split('@')[0] ||
    'Your name';

  const location = [profile?.area_name, profile?.county, profile?.country].filter(Boolean).join(', ');

  return (
    <AppCard padding="md">
      <AppText variant="caption" style={styles.overline}>
        HOW CLIENTS SEE YOU
      </AppText>
      <AppText variant="caption" style={{ color: colors.onSurfaceVariant, marginBottom: 12 }}>
        Preview of your public listing. Save changes below to update what seekers see.
      </AppText>

      <View style={styles.heroRow}>
        {photoUrl ? (
          <Image source={{ uri: String(photoUrl) }} style={styles.avatar} accessibilityIgnoresInvertColors />
        ) : (
          <View style={[styles.avatar, styles.avatarPlaceholder]}>
            <MaterialCommunityIcons name="account" size={36} color={colors.onSurfaceVariant} />
          </View>
        )}
        <View style={{ flex: 1, minWidth: 0 }}>
          <AppText variant="rowTitle" numberOfLines={2}>
            {displayName}
          </AppText>
          <AppText variant="caption" style={{ marginTop: 6, color: colors.onSurfaceVariant }}>
            {location ? location : 'Location not set'}
          </AppText>
        </View>
      </View>

      <PreviewField label="Professional headline" value={notSpecified(headline)} />
      <PreviewField label="Experience" value={experience.trim() || 'Not added yet'} multiline />
      {workingHours.trim() ? <PreviewField label="Working hours" value={workingHours.trim()} multiline /> : null}
    </AppCard>
  );
}

function PreviewField({ label, value, multiline }: { label: string; value: string; multiline?: boolean }) {
  const { colors } = useTheme();
  return (
    <View style={{ marginTop: 12 }}>
      <AppText variant="caption" style={{ color: colors.onSurfaceVariant, fontWeight: '600' }}>
        {label}
      </AppText>
      <AppText
        variant="body"
        style={{ marginTop: 4, color: colors.onSurface, lineHeight: multiline ? 22 : undefined }}
      >
        {value}
      </AppText>
    </View>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    overline: {
      fontSize: 11,
      fontWeight: '700',
      letterSpacing: 1.1,
      color: colors.primary,
      marginBottom: 4,
    },
    heroRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 14,
    },
    avatar: {
      width: 72,
      height: 72,
      borderRadius: 14,
    },
    avatarPlaceholder: {
      backgroundColor: colors.surfaceVariant,
      alignItems: 'center',
      justifyContent: 'center',
    },
  });
}
