import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppText } from '@/components/ui/AppText';
import { IconButton } from '@/components/ui/IconButton';
import { useTheme } from '@/providers/ThemeProvider';
import type { ThemeColors } from '@/theme/types';

// Real, ported screens
export { AllServicesScreen } from '@/screens/stack/AllServicesScreen';
export { ServiceMenuScreen } from '@/screens/stack/ServiceMenuScreen';
export { CategoryUsersScreen } from '@/screens/stack/CategoryUsersScreen';
export { ProviderRecommendationScreen } from '@/screens/stack/ProviderRecommendationScreen';
export { ConnectionSuccessScreen } from '@/screens/stack/ConnectionSuccessScreen';
export { ProviderProfileScreen } from '@/screens/stack/ProviderProfileScreen';
export { RequestBookingScreen } from '@/screens/stack/RequestBookingScreen';
export { ChatScreen } from '@/screens/stack/ChatScreen';
export { ProviderApplicationScreen } from '@/screens/stack/ProviderApplicationScreen';
export { ProviderBookingRequestsScreen } from '@/screens/stack/ProviderBookingRequestsScreen';
export { ManageProviderDocumentsScreen } from '@/screens/stack/ManageProviderDocumentsScreen';
export { ManageProviderProfileScreen } from '@/screens/stack/ManageProviderProfileScreen';
export { ProviderDocumentsScreen } from '@/screens/stack/ProviderDocumentsScreen';
export { SearchScreen } from '@/screens/stack/SearchScreen';
export { ReportProblemScreen } from '@/screens/stack/ReportProblemScreen';
export { PanicScreen } from '@/screens/stack/PanicScreen';
export { PasswordChangeScreen } from '@/screens/stack/PasswordChangeScreen';
export { NotificationsScreen } from '@/screens/stack/NotificationsScreen';
export { SettingsScreen } from '@/screens/stack/SettingsScreen';
export { TermsScreen } from '@/screens/stack/TermsScreen';
export { AboutUsScreen } from '@/screens/stack/AboutUsScreen';
export { EmergencyContactsScreen } from '@/screens/stack/EmergencyContactsScreen';
export { JobRatingScreen } from '@/screens/stack/JobRatingScreen';
export { SubscriptionsScreen } from '@/screens/stack/SubscriptionsScreen';
export { PaystackCheckoutScreen } from '@/screens/stack/PaystackCheckoutScreen';

function makeSimple(title: string, body: string) {
  return function SimpleScreen({ navigation }: any) {
    const { colors } = useTheme();
    const styles = useMemo(() => makeSimpleStyles(colors), [colors]);
    return (
      <Screen padded={false}>
        <View style={styles.header}>
          <IconButton variant="surface" accessibilityLabel="Back" onPress={() => navigation?.goBack?.()}>
            <MaterialCommunityIcons name="arrow-left" size={20} color={colors.onSurface} />
          </IconButton>
          <AppText variant="h3">{title}</AppText>
        </View>
        <View style={styles.body}>
          <AppText variant="body" style={{ color: colors.onSurface }}>
            {body}
          </AppText>
        </View>
      </Screen>
    );
  };
}

export const FullServicesScreen = makeSimple('Full Services', 'Full services screen (Android: FullServicesActivity).');
export const BookJobScreen = makeSimple('Book Job', 'Calendar booking screen (Android: BookJobActivity / BookNowFragment).');
export const ConversationsScreen = makeSimple('Conversations', 'Conversations list (Android: ConversationsActivity).');
// ProviderApplicationScreen is ported above.
// ProviderDocuments / ManageProviderDocuments / ProviderBookingRequests are ported above.

function makeSimpleStyles(colors: ThemeColors) {
  return StyleSheet.create({
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 8,
      backgroundColor: colors.background,
    },
    body: {
      paddingHorizontal: 16,
      paddingVertical: 12,
    },
  });
}
