/**
 * Feature parity checklist (legacy Kotlin app removed; Expo is the mobile client).
 * Update as features are ported. See plan "Expo parity with Kotlin Android".
 */
export const KOTLIN_EXPO_PARITY_ROWS = [
  { kotlin: 'MainActivity / tabs / Home', expo: 'MainTabs, HomeScreen', status: 'implemented' },
  { kotlin: 'ServicesFragment / AllServicesActivity', expo: 'ServicesScreen, AllServicesScreen', status: 'implemented' },
  { kotlin: 'CategoryUsersActivity', expo: 'CategoryUsersScreen', status: 'implemented' },
  { kotlin: 'ProviderRecommendationActivity', expo: 'ProviderRecommendationScreen', status: 'implemented' },
  { kotlin: 'ProviderProfileActivity', expo: 'ManageProviderProfileScreen', status: 'done' },
  { kotlin: 'RequestBookingActivity', expo: 'RequestBookingScreen', status: 'implemented' },
  { kotlin: 'JobsFragment (unified bookings + arrival photo + refresh)', expo: 'JobsScreen (jobs + booking teaser)', status: 'partial' },
  { kotlin: 'ProviderBookingRequestsActivity', expo: 'ProviderBookingRequestsScreen', status: 'implemented' },
  { kotlin: 'ChatActivity', expo: 'ChatScreen', status: 'implemented' },
  { kotlin: 'ConversationsActivity', expo: 'MessagesScreen', status: 'implemented' },
  { kotlin: 'ProfileFragment (provider gating)', expo: 'ProfileScreen', status: 'implemented' },
  { kotlin: 'PasswordChangeActivity (reauth + update)', expo: 'PasswordChangeScreen', status: 'implemented' },
  { kotlin: 'TermsActivity', expo: 'TermsScreen + legal/termsCopy', status: 'implemented' },
  {
    kotlin: 'SubscriptionsActivity / PaystackNativePaymentActivity',
    expo: 'SubscriptionsScreen (Android Paystack + iOS RevenueCat IAP)',
    status: 'implemented',
  },
  { kotlin: 'PanicActivity / EmergencyContacts', expo: 'PanicScreen, EmergencyContactsScreen', status: 'review' },
  { kotlin: 'AppOfflineCache (bookings/messages offline)', expo: 'None equivalent', status: 'missing' },
  { kotlin: 'ServiceMenuActivity (task_menu JSON)', expo: 'ServiceMenuScreen', status: 'implemented' },
  { kotlin: 'BookJobActivity / FullServicesActivity', expo: 'SimpleScreens placeholders', status: 'missing' },
  { kotlin: 'SearchActivity', expo: 'SearchScreen', status: 'implemented' },
  { kotlin: 'NotificationsActivity', expo: 'NotificationsScreen', status: 'partial' },
  { kotlin: 'Splash / ic_splash_logo / mipmap launcher', expo: 'assets/*.png + app.config; replace from Android res when exported', status: 'partial' },
] as const;
