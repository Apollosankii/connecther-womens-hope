import type { ExpoConfig } from 'expo/config';

/**
 * NOTE:
 * - This app is intentionally a new Expo project.
 * - The existing Android app under woman-global/mobile/android is not modified.
 * - Environment variables used at runtime must be prefixed with EXPO_PUBLIC_.
 */
const config: ExpoConfig = {
  name: 'ConnectHer',
  slug: 'connecther',
  // Set EXPO_PUBLIC_EXPO_OWNER so `originalFullName` is @owner/slug (stable https://auth.expo.io/... for Google OAuth).
  ...(process.env.EXPO_PUBLIC_EXPO_OWNER
    ? { owner: process.env.EXPO_PUBLIC_EXPO_OWNER }
    : {}),
  scheme: 'connecther',
  version: '1.0.0',
  orientation: 'portrait',
  userInterfaceStyle: 'automatic',
  platforms: ['ios', 'android', 'web'],
  plugins: [
    'expo-web-browser',
    [
      'expo-splash-screen',
      {
        image: './assets/splash-icon.png',
        resizeMode: 'contain',
        backgroundColor: '#ffffff',
      },
    ],
  ],
  ios: {
    // Set to your real bundle identifier before producing iOS builds.
    bundleIdentifier: 'com.womanglobal.connecther',
  },
  android: {
    package: 'com.womanglobal.connecther',
  },
  extra: {
    // Non-secret, safe to expose to the client.
    supabaseUrl: process.env.EXPO_PUBLIC_SUPABASE_URL,
    supabaseAnonKey: process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY,

    // Firebase Web SDK config (non-secret).
    firebaseApiKey: process.env.EXPO_PUBLIC_FIREBASE_API_KEY,
    firebaseAuthDomain: process.env.EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN,
    firebaseProjectId: process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID,
    firebaseStorageBucket: process.env.EXPO_PUBLIC_FIREBASE_STORAGE_BUCKET,
    firebaseMessagingSenderId: process.env.EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
    firebaseAppId: process.env.EXPO_PUBLIC_FIREBASE_APP_ID,
    googleWebClientId: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID,
    googleAndroidClientId: process.env.EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID,
    googleIosClientId: process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID,
  },
};

export default config;

