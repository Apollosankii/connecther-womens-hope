import { useCallback, useMemo, useState } from 'react';
import { Alert } from 'react-native';

import type { GoogleSignInBehavior } from '@/hooks/useGoogleSignIn.types';

/**
 * Native Google Sign-In is not available on web in this app.
 */
export function useGoogleSignIn(_behavior?: GoogleSignInBehavior) {
  const [busy] = useState(false);
  const ready = false;
  const canUseGoogle = false;

  const signInWithGoogle = useCallback(async () => {
    Alert.alert('Google sign-in', 'Google sign-in is only available in the Android or iOS app (development build).');
  }, []);

  return useMemo(
    () => ({
      ready,
      busy,
      signInWithGoogle,
      canUseGoogle,
    }),
    [busy, signInWithGoogle],
  );
}
