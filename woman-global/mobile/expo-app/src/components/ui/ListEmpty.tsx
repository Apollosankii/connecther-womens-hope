import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { Spacing } from '@/theme/spacing';

type Props = {
  title?: string;
  body?: string;
};

export function ListEmpty({ title = 'Nothing here yet', body }: Props) {
  return (
    <View style={styles.container}>
      <AppText variant="sectionTitle" style={styles.title}>
        {title}
      </AppText>
      {body ? (
        <AppText variant="body" style={styles.body}>
          {body}
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: 32,
    paddingHorizontal: 24,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
  },
  title: {
    textAlign: 'center',
  },
  body: {
    textAlign: 'center',
  },
});

