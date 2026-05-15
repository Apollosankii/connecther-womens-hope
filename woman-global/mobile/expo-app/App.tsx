import { DefaultTheme, NavigationContainer, type Theme as NavTheme } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { navigationRef } from '@/navigation/navigationRef';
import { RootNavigator } from '@/navigation/RootNavigator';
import { configurePushNotifications } from '@/services/push/pushNotifications';

configurePushNotifications();
import { AuthProvider } from '@/providers/AuthProvider';
import { ThemeProvider, useTheme } from '@/providers/ThemeProvider';

void SplashScreen.preventAutoHideAsync();

const queryClient = new QueryClient();

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <ThemeProvider>
          <AuthProvider>
            <ThemedNav />
          </AuthProvider>
        </ThemeProvider>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}

function ThemedNav() {
  const { effectiveMode, colors } = useTheme();
  const navTheme: NavTheme = {
    ...DefaultTheme,
    dark: effectiveMode === 'dark',
    colors: {
      ...DefaultTheme.colors,
      background: colors.background,
      card: colors.surface,
      text: colors.onSurface,
      border: colors.outlineSoft,
      primary: colors.primary,
      notification: colors.accent,
    },
  };
  return (
    <>
      <NavigationContainer ref={navigationRef} theme={navTheme}>
        <RootNavigator />
      </NavigationContainer>
      <StatusBar style={effectiveMode === 'dark' ? 'light' : 'dark'} />
    </>
  );
}
