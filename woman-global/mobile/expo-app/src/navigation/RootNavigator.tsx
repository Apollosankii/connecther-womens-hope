import { useEffect } from 'react';
import * as SplashScreen from 'expo-splash-screen';

import { Spinner } from '@/components/ui/Spinner';
import { useFirstLaunch } from '@/hooks/useFirstLaunch';
import { AppStack } from '@/navigation/AppStack';
import { AuthStack } from '@/navigation/AuthStack';
import { useAuth } from '@/providers/AuthProvider';
import { OnboardingScreen } from '@/screens/onboarding/OnboardingScreen';

export function RootNavigator() {
  const { user, initializing } = useAuth();
  const { loading, isFirstLaunch, completeOnboarding } = useFirstLaunch();

  useEffect(() => {
    if (!initializing && !loading) {
      void SplashScreen.hideAsync();
    }
  }, [initializing, loading]);

  if (initializing || loading) return <Spinner />;
  if (isFirstLaunch) return <OnboardingScreen onDone={completeOnboarding} />;
  if (!user) return <AuthStack />;
  return <AppStack />;
}

