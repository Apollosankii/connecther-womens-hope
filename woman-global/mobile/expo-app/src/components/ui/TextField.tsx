import type { ComponentProps } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';

import { Colors } from '@/theme/colors';

type Props = {
  label: string;
  errorText?: string;
} & ComponentProps<typeof TextInput>;

export function TextField({ label, errorText, style, ...rest }: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={[styles.input, style]}
        placeholderTextColor={Colors.onSurfaceVariant}
        {...rest}
      />
      {errorText ? <Text style={styles.error}>{errorText}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 6,
  },
  label: {
    fontSize: 14,
    color: Colors.onSurface,
    fontWeight: '600',
  },
  input: {
    height: 48,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Colors.outline,
    paddingHorizontal: 12,
    backgroundColor: Colors.surface,
    color: Colors.onSurface,
  },
  error: {
    color: Colors.bookingStatus.declinedText,
    fontSize: 13,
  },
});

