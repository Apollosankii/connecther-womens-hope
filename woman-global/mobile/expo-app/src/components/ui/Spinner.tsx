import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { Colors } from '@/theme/colors';

export function Spinner() {
  return (
    <View style={styles.container}>
      <ActivityIndicator color={Colors.primary} />
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

