import Constants from 'expo-constants';

type Extra = {
  supabaseUrl?: string;
  supabaseAnonKey?: string;
  firebaseApiKey?: string;
  firebaseAuthDomain?: string;
  firebaseProjectId?: string;
  firebaseStorageBucket?: string;
  firebaseMessagingSenderId?: string;
  firebaseAppId?: string;
  /** Web client ID from Firebase (Android `default_web_client_id`) — used for Google ID token in Expo. */
  googleWebClientId?: string;
  /** Native OAuth client IDs (Google Cloud). If unset, Expo auth falls back to web client for dev (see README). */
  googleAndroidClientId?: string;
  googleIosClientId?: string;
};

function getExtra(): Extra {
  const expoConfig = Constants.expoConfig ?? Constants.manifest2?.extra?.expoClient?.extra;
  return (expoConfig?.extra ?? {}) as Extra;
}

export const AppConfig = {
  supabaseUrl: () => (getExtra().supabaseUrl ?? '').trim(),
  supabaseAnonKey: () => (getExtra().supabaseAnonKey ?? '').trim(),
  firebase: () => {
    const e = getExtra();
    return {
      apiKey: (e.firebaseApiKey ?? '').trim(),
      authDomain: (e.firebaseAuthDomain ?? '').trim(),
      projectId: (e.firebaseProjectId ?? '').trim(),
      storageBucket: (e.firebaseStorageBucket ?? '').trim(),
      messagingSenderId: (e.firebaseMessagingSenderId ?? '').trim(),
      appId: (e.firebaseAppId ?? '').trim(),
    };
  },
  /** Matches Android `strings.xml` `default_web_client_id` when `extra.googleWebClientId` is empty. */
  googleWebClientId: () => {
    const v = (getExtra().googleWebClientId ?? '').trim();
    if (v) return v;
    return '759332294805-dkfvhfje3m4pfube8f1jhnr5ssepj5ku.apps.googleusercontent.com';
  },
  /** Expo requires `androidClientId` on Android; default to web client if you have no separate Android OAuth client yet. */
  googleAndroidClientId: () => {
    const v = (getExtra().googleAndroidClientId ?? '').trim();
    return v || AppConfig.googleWebClientId();
  },
  /** Expo requires `iosClientId` on iOS; default to web client until you add an iOS OAuth client. */
  googleIosClientId: () => {
    const v = (getExtra().googleIosClientId ?? '').trim();
    return v || AppConfig.googleWebClientId();
  },
} as const;

