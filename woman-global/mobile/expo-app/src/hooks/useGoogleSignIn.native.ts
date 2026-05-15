import { GoogleSignin, statusCodes } from '@react-native-google-signin/google-signin';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Platform } from 'react-native';

import type { GoogleSignInBehavior } from '@/hooks/useGoogleSignIn.types';
import { AppConfig } from '@/services/config';
import {
  firebaseLinkGoogleIdToken,
  firebaseSignInWithGoogleIdToken,
  getFirebaseAuth,
  isFirebaseConfigured,
} from '@/services/firebaseAuth';
import { runAuthBridge } from '@/services/session';

function errorCode(e: unknown): string {
  if (e && typeof e === 'object' && 'code' in e) return String((e as { code: unknown }).code);
  return '';
}

function extractIdToken(signInResult: unknown): string | undefined {
  if (!signInResult || typeof signInResult !== 'object') return undefined;

  // Some versions/types model this as { type: 'success'|'cancelled', data: { idToken } }
  if ('type' in signInResult) {
    const type = String((signInResult as { type?: unknown }).type ?? '');
    if (type === 'cancelled') return undefined;
    const data = (signInResult as { data?: unknown }).data;
    if (data && typeof data === 'object' && 'idToken' in data) {
      const token = (data as { idToken?: unknown }).idToken;
      return typeof token === 'string' ? token : undefined;
    }
  }

  // Other versions return a "userInfo" object with `idToken` at the top-level.
  if ('idToken' in signInResult) {
    const token = (signInResult as { idToken?: unknown }).idToken;
    return typeof token === 'string' ? token : undefined;
  }

  return undefined;
}

/**
 * Google ID token (native) → Firebase (sign-in or link) → auth-bridge by default.
 * Pass `behavior` from `RegisterScreen` for Kotlin-like anonymous + phone + Google completion.
 */
export function useGoogleSignIn(behavior?: GoogleSignInBehavior) {
  const [busy, setBusy] = useState(false);
  const [configured, setConfigured] = useState(false);
  const behaviorRef = useRef(behavior);
  behaviorRef.current = behavior;

  const webClientId = AppConfig.googleWebClientId();
  const iosClientId = AppConfig.googleIosClientId();

  useEffect(() => {
    if (Platform.OS === 'web') return;
    if (!webClientId.trim()) return;
    try {
      GoogleSignin.configure({
        webClientId: webClientId.trim(),
        ...(iosClientId.trim() && iosClientId.trim() !== webClientId.trim()
          ? { iosClientId: iosClientId.trim() }
          : {}),
      });
      setConfigured(true);
    } catch (e) {
      console.warn('[Google Sign-In] configure failed', e);
      setConfigured(false);
    }
  }, [webClientId, iosClientId]);

  const ready = useMemo(
    () => Platform.OS !== 'web' && configured && Boolean(webClientId.trim()),
    [configured, webClientId],
  );

  const canUseGoogle = ready;

  const signInWithGoogle = useCallback(async () => {
    if (!ready) return;
    if (!isFirebaseConfigured()) {
      Alert.alert('Firebase not configured', 'Set EXPO_PUBLIC_FIREBASE_* in .env and restart Expo.');
      return;
    }

    setBusy(true);
    try {
      if (Platform.OS === 'android') {
        await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
      }

      const signInResult = await GoogleSignin.signIn();
      let idToken = extractIdToken(signInResult);
      if (!idToken) {
        const tokens = await GoogleSignin.getTokens();
        idToken = tokens.idToken;
      }
      if (!idToken) {
        Alert.alert('Google sign-in', 'No ID token returned. Check Web client ID in Google Cloud / Firebase.');
        return;
      }

      const b = behaviorRef.current;
      const link = b?.shouldLinkGoogleToAnonymousUser?.() === true;
      const auth = getFirebaseAuth();
      const current = auth.currentUser;
      if (link) {
        if (!current?.isAnonymous) {
          throw new Error('Could not prepare registration. Go back and tap Continue with Google again.');
        }
        await firebaseLinkGoogleIdToken(idToken);
      } else {
        await firebaseSignInWithGoogleIdToken(idToken);
      }
      const user = getFirebaseAuth().currentUser;
      if (!user) throw new Error('Google sign-in succeeded but no Firebase user.');
      if (b?.onPostGoogleAuth) {
        await b.onPostGoogleAuth(user);
      } else {
        const token = await user.getIdToken(true);
        await runAuthBridge(token);
      }
    } catch (e) {
      const code = errorCode(e);
      if (code === statusCodes.SIGN_IN_CANCELLED) {
        return;
      }
      if (code === statusCodes.IN_PROGRESS) {
        Alert.alert('Google sign-in', 'Sign-in is already in progress.');
        return;
      }
      if (code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
        Alert.alert(
          'Google Play services',
          'Google Play services are missing or outdated. Update them, or use a device with Google Play services.',
        );
        return;
      }

      console.warn('[Google Sign-In] signIn failed', { code, error: e });
      const message = e instanceof Error ? e.message : typeof e === 'string' ? e : 'Unknown error';
      Alert.alert('Google sign-in failed', code ? `${message}\n\n(code: ${code})` : message);
    } finally {
      setBusy(false);
    }
  }, [ready]);

  return {
    ready,
    busy,
    signInWithGoogle,
    canUseGoogle,
  };
}
