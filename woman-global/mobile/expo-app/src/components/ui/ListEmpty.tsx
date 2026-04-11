import { StyleSheet, Text, View } from 'react-native';

import { Colors } from '@/theme/colors';

export function ListEmpty({ title = 'Nothing here yet.' }: { title?: string }) {
  return (
    <View style={styles.container}>
      <Text style={styles.text}>{title}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    color: Colors.onSurfaceVariant,
    fontSize: 14,
  },
});

