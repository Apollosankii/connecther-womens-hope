import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/providers/ThemeProvider';
import type { ThemeColors } from '@/theme/types';

type Props = {
  checked: boolean;
  onToggle: (value: boolean) => void;
  onOpenTerms: () => void;
  onOpenPrivacy?: () => void;
};

export function TermsAcceptanceRow({ checked, onToggle, onOpenTerms, onOpenPrivacy }: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const openPrivacy = onOpenPrivacy ?? onOpenTerms;

  return (
    <View style={styles.row}>
      <Pressable
        accessibilityRole="checkbox"
        accessibilityState={{ checked }}
        accessibilityLabel="I agree to the Terms of Service and Privacy Policy"
        onPress={() => onToggle(!checked)}
        hitSlop={8}
        style={({ pressed }) => [styles.checkbox, pressed && { opacity: 0.85 }]}
      >
        <MaterialCommunityIcons
          name={checked ? 'checkbox-marked' : 'checkbox-blank-outline'}
          size={24}
          color={checked ? colors.primary : colors.onSurfaceVariant}
        />
      </Pressable>
      <Text style={[styles.text, { color: colors.onSurfaceVariant }]}>
        I agree to the{' '}
        <Text style={styles.link} onPress={onOpenTerms} accessibilityRole="link">
          Terms of Service
        </Text>{' '}
        and{' '}
        <Text style={styles.link} onPress={openPrivacy} accessibilityRole="link">
          Privacy Policy
        </Text>
        .
      </Text>
    </View>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    row: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: 8,
      marginTop: 4,
    },
    checkbox: {
      marginTop: -2,
    },
    text: {
      flex: 1,
      lineHeight: 20,
    },
    link: {
      fontSize: 13,
      lineHeight: 20,
      color: colors.primary,
      fontWeight: '700',
      textDecorationLine: 'underline',
    },
  });
}
