import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Image, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { TextField } from '@/components/ui/TextField';
import { useAppleSignIn } from '@/hooks/useAppleSignIn';
import { useGoogleSignIn } from '@/hooks/useGoogleSignIn';
import type { AuthStackParamList } from '@/navigation/types';
import { useAuth } from '@/providers/AuthProvider';
import { useTheme } from '@/providers/ThemeProvider';
import {
  firebaseResetPassword,
  firebaseSendEmailVerification,
  firebaseSignIn,
  getFirebaseAuth,
  isFirebaseConfigured,
} from '@/services/firebaseAuth';
import { runAuthBridge } from '@/services/session';
import type { ThemeColors } from '@/theme/types';

type Props = NativeStackScreenProps<AuthStackParamList, 'Login'>;

export function LoginScreen({ navigation }: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { isLoggedIn, authTransitioning } = useAuth();
  const { ready, busy: googleBusy, signInWithGoogle, canUseGoogle } = useGoogleSignIn();
  const { ready: appleReady, busy: appleBusy, signInWithApple } = useAppleSignIn();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [awaitingAuth, setAwaitingAuth] = useState(false);
  const [emailError, setEmailError] = useState<string | undefined>();
  const [passwordError, setPasswordError] = useState<string | undefined>();
  const awaitingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const authBusy = loading || googleBusy || appleBusy || awaitingAuth || authTransitioning;

  useEffect(() => {
    if (isLoggedIn) {
      setAwaitingAuth(false);
      if (awaitingTimerRef.current) {
        clearTimeout(awaitingTimerRef.current);
        awaitingTimerRef.current = null;
      }
    }
  }, [isLoggedIn]);

  const onLogin = async () => {
    if (!isFirebaseConfigured()) {
      Alert.alert(
        'Firebase not configured',
        'Set EXPO_PUBLIC_FIREBASE_* in woman-global/mobile/expo-app/.env and restart Expo.',
      );
      return;
    }
    setEmailError(undefined);
    setPasswordError(undefined);
    const e = email.trim();
    if (!e) {
      setEmailError('Email is required.');
      return;
    }
    if (!password) {
      setPasswordError('Password is required.');
      return;
    }

    setLoading(true);
    try {
      await firebaseSignIn(e, password);
      const user = getFirebaseAuth().currentUser;
      if (!user) throw new Error('Login succeeded but no user was returned.');
      const idToken = await user.getIdToken(true);
      await runAuthBridge(idToken);
      const hasPasswordProvider = user.providerData.some((p) => p.providerId === 'password');
      if (hasPasswordProvider && user.email && !user.emailVerified) {
        Alert.alert(
          'Verify your email',
          'Your email address is not verified yet. You can continue, or resend a verification link.',
          [
            { text: 'OK', style: 'default' },
            {
              text: 'Send verification email',
              onPress: async () => {
                try {
                  await firebaseSendEmailVerification();
                  Alert.alert('Check your inbox', 'We sent a verification email.');
                } catch (e) {
                  Alert.alert('Could not send', e instanceof Error ? e.message : 'Unknown error');
                }
              },
            },
          ],
        );
      }
      // RootNavigator will route into the app automatically via auth state.
    } catch (err) {
      Alert.alert('Login failed', err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  const onForgotPassword = async () => {
    const e = email.trim();
    if (!e) {
      Alert.alert('Reset password', 'Enter your email above first.');
      return;
    }
    try {
      await firebaseResetPassword(e);
      Alert.alert('Reset password', 'Password reset email sent.');
    } catch (err) {
      Alert.alert('Reset password failed', err instanceof Error ? err.message : 'Unknown error');
    }
  };

  const onGoogle = async () => {
    if (authBusy) return;
    try {
      setAwaitingAuth(true);
      // Safety: don't spin forever if bridge/session fails.
      if (awaitingTimerRef.current) clearTimeout(awaitingTimerRef.current);
      awaitingTimerRef.current = setTimeout(() => {
        setAwaitingAuth(false);
        Alert.alert('Google sign-in', 'Sign-in is taking longer than expected. Please try again.');
      }, 20_000);

      await signInWithGoogle();
      // keep spinner until `isLoggedIn` flips (RootNavigator switches to AppStack)
    } finally {
      // If signInWithGoogle throws, we stop awaiting immediately (alerts handled inside hook + here).
      // If it succeeds, the useEffect above will clear awaitingAuth when isLoggedIn becomes true.
      if (!isLoggedIn) {
        setAwaitingAuth(false);
        if (awaitingTimerRef.current) {
          clearTimeout(awaitingTimerRef.current);
          awaitingTimerRef.current = null;
        }
      }
    }
  };

  const onApple = async () => {
    if (authBusy) return;
    try {
      setAwaitingAuth(true);
      if (awaitingTimerRef.current) clearTimeout(awaitingTimerRef.current);
      awaitingTimerRef.current = setTimeout(() => {
        setAwaitingAuth(false);
        Alert.alert('Apple sign-in', 'Sign-in is taking longer than expected. Please try again.');
      }, 20_000);

      await signInWithApple();
    } finally {
      if (!isLoggedIn) {
        setAwaitingAuth(false);
        if (awaitingTimerRef.current) {
          clearTimeout(awaitingTimerRef.current);
          awaitingTimerRef.current = null;
        }
      }
    }
  };

  return (
    <Screen>
      <View style={styles.container}>
        <View style={styles.brandHeader}>
          <Image source={require('../../../assets/connecther-mark.png')} style={styles.brandMark} />
          <View style={styles.brandText}>
            <Text style={styles.brandName}>ConnectHer</Text>
            <Text style={styles.subtitle}>Sign in to continue</Text>
          </View>
        </View>

        <View style={styles.form}>
          <TextField
            label="Email"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            keyboardType="email-address"
            textContentType="emailAddress"
            errorText={emailError}
          />
          <TextField
            label="Password"
            value={password}
            onChangeText={setPassword}
            autoCapitalize="none"
            secureTextEntry
            textContentType="password"
            errorText={passwordError}
          />

          <TouchableOpacity onPress={onForgotPassword} accessibilityRole="button">
            <Text style={styles.link}>Forgot password?</Text>
          </TouchableOpacity>

          <AppButton onPress={onLogin} loading={authBusy} disabled={googleBusy}>
            Login
          </AppButton>

          <AppButton
            variant="outline"
            onPress={() => void onGoogle()}
            disabled={!ready || !canUseGoogle || authBusy}
            loading={googleBusy || awaitingAuth || authTransitioning}
          >
            <View style={styles.googleRow}>
              <MaterialCommunityIcons name="google" size={20} color={colors.primary} />
              <Text style={styles.googleLabel}>Continue with Google</Text>
            </View>
          </AppButton>

          {Platform.OS === 'ios' ? (
            <AppButton
              variant="outline"
              onPress={() => void onApple()}
              disabled={!appleReady || authBusy}
              loading={appleBusy || awaitingAuth || authTransitioning}
            >
              <View style={styles.googleRow}>
                <MaterialCommunityIcons name="apple" size={20} color={colors.onBackground} />
                <Text style={styles.googleLabel}>Continue with Apple</Text>
              </View>
            </AppButton>
          ) : null}

          <View style={styles.footerRow}>
            <Text style={styles.footerText}>Don&apos;t have an account?</Text>
            <TouchableOpacity onPress={() => navigation.navigate('Register')} accessibilityRole="button">
              <Text style={[styles.link, styles.footerLink]}>Register</Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.termsFooter}>
            By signing in you agree to our{' '}
            <Text style={styles.termsLink} onPress={() => navigation.navigate('Terms')} accessibilityRole="link">
              Terms of Service
            </Text>{' '}
            and{' '}
            <Text style={styles.termsLink} onPress={() => navigation.navigate('Terms')} accessibilityRole="link">
              Privacy Policy
            </Text>
            .
          </Text>
        </View>
      </View>
    </Screen>
  );
}

function makeStyles(colors: ThemeColors) {
  return StyleSheet.create({
    container: {
      flex: 1,
      justifyContent: 'center',
      gap: 12,
    },
    brandHeader: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      alignSelf: 'center',
      maxWidth: 420,
      width: '100%',
    },
    brandMark: {
      width: 40,
      height: 40,
      resizeMode: 'contain',
    },
    brandText: {
      flex: 1,
      gap: 2,
    },
    brandName: {
      fontSize: 20,
      fontWeight: '800',
      color: colors.onBackground,
      letterSpacing: 0.2,
    },
    subtitle: {
      color: colors.onSurfaceVariant,
      lineHeight: 20,
    },
    form: {
      marginTop: 10,
      gap: 12,
    },
    link: {
      color: colors.accent,
      fontWeight: '600',
    },
    footerRow: {
      marginTop: 8,
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
    },
    footerText: {
      color: colors.onSurfaceVariant,
    },
    footerLink: {
      paddingVertical: 8,
    },
    googleRow: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 10,
    },
    googleLabel: {
      fontSize: 16,
      fontWeight: '600',
      color: colors.primary,
    },
    termsFooter: {
      marginTop: 16,
      fontSize: 12,
      lineHeight: 18,
      color: colors.onSurfaceVariant,
      textAlign: 'center',
    },
    termsLink: {
      color: colors.primary,
      fontWeight: '600',
      textDecorationLine: 'underline',
    },
  });
}
