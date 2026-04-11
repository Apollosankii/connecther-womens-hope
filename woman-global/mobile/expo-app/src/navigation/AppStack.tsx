import { createNativeStackNavigator } from '@react-navigation/native-stack';

import type { AppStackParamList } from '@/navigation/types';
import { MainTabs } from '@/navigation/MainTabs';
import {
  AboutUsScreen,
  AllServicesScreen,
  BookJobScreen,
  CategoryUsersScreen,
  ChatScreen,
  ConversationsScreen,
  EmergencyContactsScreen,
  FullServicesScreen,
  ManageProviderDocumentsScreen,
  NotificationsScreen,
  PanicScreen,
  PasswordChangeScreen,
  ProviderApplicationScreen,
  ProviderBookingRequestsScreen,
  ProviderDocumentsScreen,
  ProviderProfileScreen,
  ReportProblemScreen,
  RequestBookingScreen,
  SearchScreen,
  SettingsScreen,
  SubscriptionsScreen,
  TermsScreen,
} from '@/screens/stack/SimpleScreens';

const Stack = createNativeStackNavigator<AppStackParamList>();

export function AppStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="MainTabs" component={MainTabs} />
      <Stack.Screen name="AllServices" component={AllServicesScreen} />
      <Stack.Screen name="FullServices" component={FullServicesScreen} />
      <Stack.Screen name="Search" component={SearchScreen} />
      <Stack.Screen name="CategoryUsers" component={CategoryUsersScreen} />
      <Stack.Screen name="BookJob" component={BookJobScreen} />
      <Stack.Screen name="RequestBooking" component={RequestBookingScreen} />
      <Stack.Screen name="Conversations" component={ConversationsScreen} />
      <Stack.Screen name="Chat" component={ChatScreen} />
      <Stack.Screen name="Settings" component={SettingsScreen} />
      <Stack.Screen name="PasswordChange" component={PasswordChangeScreen} />
      <Stack.Screen name="AboutUs" component={AboutUsScreen} />
      <Stack.Screen name="Notifications" component={NotificationsScreen} />
      <Stack.Screen name="ReportProblem" component={ReportProblemScreen} />
      <Stack.Screen name="Terms" component={TermsScreen} />
      <Stack.Screen name="Panic" component={PanicScreen} />
      <Stack.Screen name="EmergencyContacts" component={EmergencyContactsScreen} />
      <Stack.Screen name="ProviderProfile" component={ProviderProfileScreen} />
      <Stack.Screen name="ProviderApplication" component={ProviderApplicationScreen} />
      <Stack.Screen name="ProviderDocuments" component={ProviderDocumentsScreen} />
      <Stack.Screen name="ManageProviderDocuments" component={ManageProviderDocumentsScreen} />
      <Stack.Screen name="ProviderBookingRequests" component={ProviderBookingRequestsScreen} />
      <Stack.Screen name="Subscriptions" component={SubscriptionsScreen} />
    </Stack.Navigator>
  );
}

