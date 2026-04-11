import Constants from 'expo-constants';
import * as AuthSession from 'expo-auth-session';
import * as Google from 'expo-auth-session/providers/google';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Platform } from 'react-native';

import { AppConfig } from '@/services/config';
import {
  firebaseSignInWithGoogleIdToken,
  getFirebaseAuth,
  isFirebaseConfigured,
} from '@/services/firebaseAuth';
import { runAuthBridge } from '@/services/session';

const AUTH_EXPO_IO = 'https://auth.expo.io';

/**
 * Google Web OAuth only allows HTTPS (or platform-specific native) redirect URIs.
 * Expo Go's default `makeRedirectUri()` is `exp://…`, which Google rejects for a **Web** client.
 * Use the hosted AuthSession proxy URL so `redirect_uri` is `https://auth.expo.io/@owner/slug`.
 *
 * @see https://docs.expo.dev/guides/authentication/#google
 */
function resolveGoogleRedirectUriForNative(): string | undefined {
  if (Platform.OS === 'web') return undefined;

  const fromEnv = process.env.EXPO_PUBLIC_EXPO_AUTH_PROXY_FULL_NAME?.trim();
  if (fromEnv) {
    const fullName = fromEnv.startsWith('@') ? fromEnv : `@${fromEnv}`;
    return `${AUTH_EXPO_IO}/${fullName}`;
  }

  const fullName = Constants.expoConfig?.originalFullName;
  if (fullName) {
    return `${AUTH_EXPO_IO}/${fullName}`;
  }

  try {
    return AuthSession.getRedirectUrl();
  } catch {
    return undefined;
  }
}

/**
 * Google ID token → Firebase `GoogleAuthProvider` → auth-bridge (same as Android `LoginActivity.signInWithGoogle`).
 */
export function useGoogleSignIn() {
  const [busy, setBusy] = useState(false);
  const processedIdToken = useRef<string | null>(null);
  const webClientId = AppConfig.googleWebClientId();
  const androidClientId = AppConfig.googleAndroidClientId();
  const iosClientId = AppConfig.googleIosClientId();

  const redirectUri = useMemo(() => resolveGoogleRedirectUriForNative(), []);

  useEffect(() => {
    if (Platform.OS === 'web' || !webClientId) return;
    if (redirectUri) return;
    console.warn(
      '[Google Sign-In] No HTTPS Expo proxy redirect URI (originalFullName missing). Google may block exp:// redirects. ' +
        'Run `npx expo login`, set `owner` in app config, or set EXPO_PUBLIC_EXPO_AUTH_PROXY_FULL_NAME=@your-expo-account/connecther in .env.',
    );
  }, [redirectUri, webClientId]);

  const [request, response, promptAsync] = Google.useIdTokenAuthRequest({
    webClientId,
    // expo-auth-session requires platform-specific client IDs; we default them to the Web client when unset.
    androidClientId,
    iosClientId,
    ...(redirectUri ? { redirectUri } : {}),
  });

  useEffect(() => {
    if (!response) return;
    if (response.type === 'dismiss' || response.type === 'cancel') return;
    if (response.type === 'error') {
      Alert.alert('Google sign-in', response.error?.message ?? 'Something went wrong.');
      return;
    }
    if (response.type !== 'success') return;

    const idToken =
      response.params.id_token ?? (response.authentication as { idToken?: string } | null)?.idToken;
    if (!idToken) {
      Alert.alert(
        'Google sign-in',
        'No ID token returned. Add authorized redirect URIs for Expo in Google Cloud Console (see README).',
      );
      return;
    }
    if (processedIdToken.current === idToken) return;
    processedIdToken.current = idToken;

    if (!isFirebaseConfigured()) {
      Alert.alert('Firebase not configured', 'Set EXPO_PUBLIC_FIREBASE_* in .env and restart Expo.');
      return;
    }

    (async () => {
      setBusy(true);
      try {
        await firebaseSignInWithGoogleIdToken(idToken);
        const user = getFirebaseAuth().currentUser;
        if (!user) throw new Error('Google sign-in succeeded but no Firebase user.');
        const token = await user.getIdToken(true);
        await runAuthBridge(token);
      } catch (e) {
        Alert.alert('Google sign-in failed', e instanceof Error ? e.message : 'Unknown error');
      } finally {
        setBusy(false);
      }
    })();
  }, [response]);

  return {
    request,
    busy,
    signInWithGoogle: () => promptAsync(),
    canUseGoogle: Boolean(webClientId),
  };
}
