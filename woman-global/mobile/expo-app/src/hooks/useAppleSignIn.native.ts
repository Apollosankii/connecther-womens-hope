import * as AppleAuthentication from 'expo-apple-authentication';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Alert, Platform } from 'react-native';
import { OAuthProvider, signInWithCredential } from 'firebase/auth';

import type { AppleSignInBehavior } from '@/hooks/useAppleSignIn.types';
import {
  firebaseLinkAppleIdentityToken,
  getFirebaseAuth,
  isFirebaseConfigured,
} from '@/services/firebaseAuth';
import { runAuthBridge } from '@/services/session';

/**
 * Apple Sign-In (iOS) → Firebase OAuthProvider('apple.com') → auth-bridge.
 * Note: Apple Sign-In is not available on Android/web.
 */
export function useAppleSignIn(behavior?: AppleSignInBehavior) {
  const [busy, setBusy] = useState(false);
  const behaviorRef = useRef(behavior);
  behaviorRef.current = behavior;

  const ready = useMemo(() => Platform.OS === 'ios', []);

  const signInWithApple = useCallback(async () => {
    if (Platform.OS !== 'ios') return;
    if (!isFirebaseConfigured()) {
      Alert.alert('Firebase not configured', 'Set EXPO_PUBLIC_FIREBASE_* in .env and restart Expo.');
      return;
    }

    setBusy(true);
    try {
      const credential = await AppleAuthentication.signInAsync({
        requestedScopes: [
          AppleAuthentication.AppleAuthenticationScope.FULL_NAME,
          AppleAuthentication.AppleAuthenticationScope.EMAIL,
        ],
      });

      const identityToken = credential.identityToken;
      if (!identityToken) {
        Alert.alert('Apple sign-in', 'No identity token returned.');
        return;
      }

      const b = behaviorRef.current;
      const link = b?.shouldLinkAppleToAnonymousUser?.() === true;
      const auth = getFirebaseAuth();
      const current = auth.currentUser;
      if (link) {
        if (!current?.isAnonymous) {
          throw new Error('Could not prepare registration. Go back and tap Continue with Apple again.');
        }
        await firebaseLinkAppleIdentityToken(identityToken);
      } else {
        const provider = new OAuthProvider('apple.com');
        const firebaseCred = provider.credential({
          idToken: identityToken,
          rawNonce: undefined,
        });
        await signInWithCredential(auth, firebaseCred);
      }

      const user = auth.currentUser;
      if (!user) throw new Error('Apple sign-in succeeded but no Firebase user.');
      if (b?.onPostAppleAuth) {
        await b.onPostAppleAuth(user);
      } else {
        const token = await user.getIdToken(true);
        await runAuthBridge(token);
      }
    } catch (e) {
      if (
        e &&
        typeof e === 'object' &&
        'code' in e &&
        (String((e as { code?: unknown }).code) === 'ERR_REQUEST_CANCELED' ||
          String((e as { code?: unknown }).code) === 'ERR_CANCELED')
      ) {
        return;
      }
      const message = e instanceof Error ? e.message : typeof e === 'string' ? e : 'Unknown error';
      Alert.alert('Apple sign-in failed', message);
    } finally {
      setBusy(false);
    }
  }, []);

  return {
    ready,
    busy,
    signInWithApple,
  };
}
