import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { useTheme } from '@/providers/ThemeProvider';

export function Spinner() {
  const { colors } = useTheme();
  return (
    <View style={styles.container}>
      <ActivityIndicator color={colors.primary} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

