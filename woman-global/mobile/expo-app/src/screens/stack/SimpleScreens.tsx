import { StyleSheet, Text, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { Colors } from '@/theme/colors';

function makeSimple(title: string) {
  return function SimpleScreen() {
    return (
      <Screen>
        <View style={styles.container}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>Screen scaffolded. Port the Android UX and SupabaseData calls here.</Text>
        </View>
      </Screen>
    );
  };
}

export const AllServicesScreen = makeSimple('All Services');
export const FullServicesScreen = makeSimple('Full Services');
export const SearchScreen = makeSimple('Search');
export const CategoryUsersScreen = makeSimple('Category Users');
export const BookJobScreen = makeSimple('Book Job');
export const RequestBookingScreen = makeSimple('Request Booking');
export const ConversationsScreen = makeSimple('Conversations');
export const ChatScreen = makeSimple('Chat');
export const SettingsScreen = makeSimple('Settings');
export const PasswordChangeScreen = makeSimple('Password Change');
export const AboutUsScreen = makeSimple('About Us');
export const NotificationsScreen = makeSimple('Notifications');
export const ReportProblemScreen = makeSimple('Report a Problem');
export const TermsScreen = makeSimple('Terms');
export const PanicScreen = makeSimple('Panic / SOS');
export const EmergencyContactsScreen = makeSimple('Emergency Contacts');
export const ProviderProfileScreen = makeSimple('Provider Profile');
export const ProviderApplicationScreen = makeSimple('Provider Application');
export const ProviderDocumentsScreen = makeSimple('Provider Documents');
export const ManageProviderDocumentsScreen = makeSimple('Manage Provider Documents');
export const ProviderBookingRequestsScreen = makeSimple('Provider Booking Requests');
export const SubscriptionsScreen = makeSimple('Subscriptions');

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    gap: 10,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: Colors.onBackground,
  },
  subtitle: {
    color: Colors.onSurfaceVariant,
    lineHeight: 20,
  },
});

