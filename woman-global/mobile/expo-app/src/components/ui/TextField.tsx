import type { ComponentProps } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';

import { useTheme } from '@/providers/ThemeProvider';
import { Metrics } from '@/theme/metrics';

type Props = {
  label: string;
  errorText?: string;
} & ComponentProps<typeof TextInput>;

export function TextField({ label, errorText, style, multiline, ...rest }: Props) {
  const { colors } = useTheme();
  return (
    <View style={styles.container}>
      <Text style={[styles.label, { color: colors.onSurface }]}>{label}</Text>
      <TextInput
        style={[
          styles.input,
          multiline ? styles.inputMultiline : styles.inputSingleLine,
          { borderColor: colors.outlineSoft, backgroundColor: colors.surface, color: colors.onSurface },
          style,
        ]}
        placeholderTextColor={colors.onSurfaceVariant}
        multiline={multiline}
        {...rest}
      />
      {errorText ? <Text style={[styles.error, { color: colors.bookingStatus.declinedText }]}>{errorText}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 6,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
  },
  input: {
    borderRadius: Metrics.radiusSm,
    borderWidth: 1,
    paddingHorizontal: 12,
  },
  inputSingleLine: {
    height: Metrics.inputHeight,
  },
  inputMultiline: {
    minHeight: 96,
    paddingTop: 12,
    paddingBottom: 12,
    textAlignVertical: 'top',
  },
  error: {
    fontSize: 13,
  },
});

