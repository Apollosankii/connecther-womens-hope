import { Alert } from 'react-native';

import type { AppleSignInBehavior } from '@/hooks/useAppleSignIn.types';

export function useAppleSignIn(_behavior?: AppleSignInBehavior) {
  return {
    ready: false,
    busy: false,
    signInWithApple: async () => {
      Alert.alert('Apple sign-in', 'Sign in with Apple is only available on iOS.');
    },
  };
}

