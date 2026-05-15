import type { ExpoConfig } from 'expo/config';

function parseAndroidVersionCode(raw: string | undefined): number {
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 1) return 1;
  return Math.floor(n);
}

/**
 * NOTE:
 * - This app is intentionally a new Expo project.
 * - The existing Android app under woman-global/mobile/android is not modified.
 * - Environment variables used at runtime must be prefixed with EXPO_PUBLIC_.
 * - Registration (Kotlin parity): enable Firebase **Anonymous** sign-in so phone-verify + Google linking works.
 * - `phone-verify` Edge Function uses the same `EXPO_PUBLIC_SUPABASE_URL` / anon key as the Supabase client.
 * - Native Google Sign-In requires a dev/standalone build (not Expo Go). Set a real `iosUrlScheme` (reversed iOS client ID) before shipping iOS.
 */
const config: ExpoConfig = {
  name: 'ConnectHer',
  slug: 'connecther',
  ...(process.env.EXPO_PUBLIC_EXPO_OWNER ? { owner: process.env.EXPO_PUBLIC_EXPO_OWNER } : {}),
  scheme: 'connecther',
  version: '1.0.0',
  orientation: 'portrait',
  icon: './assets/connecther-mark.png',
  userInterfaceStyle: 'automatic',
  platforms: ['ios', 'android', 'web'],
  plugins: [
    './plugins/withGradleJavaHome',
    './plugins/withAndroidFcm',
    'expo-font',
    'expo-secure-store',
    'expo-web-browser',
    [
      'expo-location',
      {
        locationWhenInUsePermission:
          'ConnectHer uses your location when you choose “Use my location” on a booking request.',
      },
    ],
    [
      'expo-splash-screen',
      {
        image: './assets/connecther-mark.png',
        resizeMode: 'contain',
        backgroundColor: '#ffffff',
        imageWidth: 240,
      },
    ],
    [
      '@react-native-google-signin/google-signin',
      {
        iosUrlScheme: 'com.googleusercontent.apps.759332294805-rg0ngantsorhslb0va862pemc0i54oj3',
      },
    ],
    'expo-apple-authentication',
    [
      'expo-notifications',
      {
        icon: './assets/connecther-mark.png',
        color: '#E91E8C',
        defaultChannel: 'FCM_CHANNEL_ID',
      },
    ],
  ],
  ios: {
    // Set to your real bundle identifier before producing iOS builds.
    bundleIdentifier: 'com.womanglobal.connecther',
    icon: './assets/connecther-mark.png',
    supportsTablet: true,
    buildNumber: process.env.IOS_BUILD_NUMBER ?? '1',
  },
  android: {
    package: 'com.womanglobal.connecther',
    googleServicesFile: './google-services.json',
    adaptiveIcon: {
      foregroundImage: './assets/connecther-mark.png',
      backgroundColor: '#ffffff',
    },
    edgeToEdgeEnabled: true,
    predictiveBackGestureEnabled: false,
    softwareKeyboardLayoutMode: 'resize',
    versionCode: parseAndroidVersionCode(process.env.ANDROID_VERSION_CODE),
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
    revenueCatIosApiKey: process.env.EXPO_PUBLIC_REVENUECAT_IOS_API_KEY,

    eas: {
      projectId: process.env.EAS_PROJECT_ID ?? process.env.EXPO_PUBLIC_EAS_PROJECT_ID,
    },
  },
};

export default config;

