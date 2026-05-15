import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Image, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import type { User } from 'firebase/auth';

import { TermsAcceptanceRow } from '@/components/auth/TermsAcceptanceRow';
import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { TextField } from '@/components/ui/TextField';
import { useAppleSignIn } from '@/hooks/useAppleSignIn';
import { useGoogleSignIn } from '@/hooks/useGoogleSignIn';
import type { AuthStackParamList } from '@/navigation/types';
import { useAuth } from '@/providers/AuthProvider';
import { useTheme } from '@/providers/ThemeProvider';
import { updateMyProfile } from '@/services/api/profile';
import {
  firebaseEnsureEmailPasswordForLogin,
  firebaseSignInAnonymously,
  getFirebaseAuth,
  isFirebaseConfigured,
} from '@/services/firebaseAuth';
import { runAuthBridge } from '@/services/session';
import { normalizeKenyaE164 } from '@/utils/phone';
import type { ThemeColors } from '@/theme/types';

type Props = NativeStackScreenProps<AuthStackParamList, 'Register'>;

export function RegisterScreen({ navigation }: Props) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { isLoggedIn, authTransitioning } = useAuth();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [title, setTitle] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [busy, setBusy] = useState(false);
  const [termsAccepted, setTermsAccepted] = useState(false);

  const onPostSocialAuth = useCallback(
    async (user: User) => {
      const e164 = normalizeKenyaE164(phone.trim());
      if (!e164) {
        throw new Error('Enter a valid phone number (Kenya formats supported).');
      }
      const email = user.email?.trim();
      if (!email) {
        throw new Error(
          'No email was returned for this account. Use Google sign-in, or sign in with Apple again and choose to share your email.',
        );
      }
      await firebaseEnsureEmailPasswordForLogin(email, password);
      const token = await user.getIdToken(true);
      await runAuthBridge(token);
      await updateMyProfile({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        phone: e164,
        email,
        title: title.trim(),
        occupation: '',
      });
    },
    [phone, password, firstName, lastName, title],
  );

  const { ready, busy: googleBusy, signInWithGoogle, canUseGoogle } = useGoogleSignIn({
    shouldLinkGoogleToAnonymousUser: () => true,
    onPostGoogleAuth: onPostSocialAuth,
  });
  const { ready: appleReady, busy: appleBusy, signInWithApple } = useAppleSignIn({
    shouldLinkAppleToAnonymousUser: () => true,
    onPostAppleAuth: onPostSocialAuth,
  });

  const [awaitingAuth, setAwaitingAuth] = useState(false);
  const awaitingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const authBusy = busy || googleBusy || appleBusy || awaitingAuth || authTransitioning;
  const currentE164 = useMemo(() => normalizeKenyaE164(phone.trim()), [phone]);

  const formValid = useMemo(() => {
    if (!firstName.trim() || !lastName.trim() || !title.trim()) return false;
    if (!currentE164) return false;
    if (password.length < 6) return false;
    if (password !== confirmPassword) return false;
    if (!termsAccepted) return false;
    return true;
  }, [firstName, lastName, title, currentE164, password, confirmPassword, termsAccepted]);

  const canContinueWithGoogle = Boolean(formValid && ready && canUseGoogle && !authBusy);
  const canContinueWithApple = Boolean(formValid && appleReady && !authBusy);

  const socialDisabledReason = useMemo(() => {
    if (authBusy) return 'Please wait…';
    if (!termsAccepted) return 'Accept the Terms of Service and Privacy Policy to continue.';
    if (!firstName.trim() || !lastName.trim()) return 'Enter your first and last name.';
    if (!title.trim()) return 'Select or enter a title (e.g. Mr, Mrs, Miss).';
    if (!currentE164) return 'Enter a valid phone number (Kenya formats supported).';
    if (password.length < 6) return 'Password must be at least 6 characters.';
    if (password !== confirmPassword) return 'Passwords do not match.';
    return null;
  }, [authBusy, termsAccepted, firstName, lastName, title, currentE164, password, confirmPassword]);

  const googleDisabledReason = useMemo(() => {
    if (socialDisabledReason) return socialDisabledReason;
    if (!ready || !canUseGoogle) return 'Google sign-in is not ready.';
    return null;
  }, [socialDisabledReason, ready, canUseGoogle]);

  const appleDisabledReason = useMemo(() => {
    if (socialDisabledReason) return socialDisabledReason;
    if (!appleReady) return 'Apple sign-in is only available on iOS.';
    return null;
  }, [socialDisabledReason, appleReady]);

  useEffect(() => {
    if (isLoggedIn) {
      setAwaitingAuth(false);
      if (awaitingTimerRef.current) {
        clearTimeout(awaitingTimerRef.current);
        awaitingTimerRef.current = null;
      }
    }
  }, [isLoggedIn]);

  async function ensureAnonymousForRegistration(): Promise<boolean> {
    const auth = getFirebaseAuth();
    const cur = auth.currentUser;
    if (cur && !cur.isAnonymous) {
      Alert.alert(
        'Sign out required',
        'You are already signed in. Sign out from the app (or use another device) before registering a new account.',
      );
      return false;
    }
    if (!cur) {
      await firebaseSignInAnonymously();
    }
    return true;
  }

  return (
    <Screen>
      <View style={styles.container}>
        <View style={styles.brandHeader}>
          <Image source={require('../../../assets/connecther-mark.png')} style={styles.brandMark} />
          <View style={styles.brandText}>
            <Text style={styles.brandName}>ConnectHer</Text>
            <Text style={styles.subtitle}>Create your account</Text>
          </View>
        </View>

        <View style={styles.form}>
          <TextField label="First name" value={firstName} onChangeText={setFirstName} autoCapitalize="words" />
          <TextField label="Last name" value={lastName} onChangeText={setLastName} autoCapitalize="words" />
          <TextField
            label="Title"
            value={title}
            onChangeText={setTitle}
            placeholder="Mr, Mrs, Miss, Sir…"
            autoCapitalize="words"
          />
          <TextField label="Phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
          <TextField
            label="Password"
            value={password}
            onChangeText={setPassword}
            autoCapitalize="none"
            secureTextEntry
            textContentType="newPassword"
          />
          <TextField
            label="Confirm password"
            value={confirmPassword}
            onChangeText={setConfirmPassword}
            autoCapitalize="none"
            secureTextEntry
            textContentType="newPassword"
          />

          <TermsAcceptanceRow
            checked={termsAccepted}
            onToggle={setTermsAccepted}
            onOpenTerms={() => navigation.navigate('Terms')}
          />

          <AppButton
            variant="outline"
            onPress={async () => {
              if (!isFirebaseConfigured()) {
                Alert.alert(
                  'Firebase not configured',
                  'Set EXPO_PUBLIC_FIREBASE_* in woman-global/mobile/expo-app/.env and restart Expo.',
                );
                return;
              }
              if (!canContinueWithGoogle && googleDisabledReason) {
                Alert.alert('Continue with Google', googleDisabledReason);
                return;
              }
              setBusy(true);
              try {
                const okAnon = await ensureAnonymousForRegistration();
                if (!okAnon) return;
              } catch (e) {
                Alert.alert('Register', e instanceof Error ? e.message : 'Could not start registration.');
                return;
              } finally {
                setBusy(false);
              }

              setAwaitingAuth(true);
              if (awaitingTimerRef.current) clearTimeout(awaitingTimerRef.current);
              awaitingTimerRef.current = setTimeout(() => {
                setAwaitingAuth(false);
                Alert.alert('Google sign-in', 'Sign-in is taking longer than expected. Please try again.');
              }, 20_000);
              void signInWithGoogle().finally(() => {
                if (!isLoggedIn) {
                  setAwaitingAuth(false);
                  if (awaitingTimerRef.current) {
                    clearTimeout(awaitingTimerRef.current);
                    awaitingTimerRef.current = null;
                  }
                }
              });
            }}
            loading={authBusy}
            disabled={!canContinueWithGoogle}
          >
            Continue with Google
          </AppButton>

          {Platform.OS === 'ios' ? (
            <AppButton
              variant="outline"
              onPress={async () => {
                if (!isFirebaseConfigured()) {
                  Alert.alert(
                    'Firebase not configured',
                    'Set EXPO_PUBLIC_FIREBASE_* in woman-global/mobile/expo-app/.env and restart Expo.',
                  );
                  return;
                }
                if (!canContinueWithApple && appleDisabledReason) {
                  Alert.alert('Continue with Apple', appleDisabledReason);
                  return;
                }
                setBusy(true);
                try {
                  const okAnon = await ensureAnonymousForRegistration();
                  if (!okAnon) return;
                } catch (e) {
                  Alert.alert('Register', e instanceof Error ? e.message : 'Could not start registration.');
                  return;
                } finally {
                  setBusy(false);
                }

                setAwaitingAuth(true);
                if (awaitingTimerRef.current) clearTimeout(awaitingTimerRef.current);
                awaitingTimerRef.current = setTimeout(() => {
                  setAwaitingAuth(false);
                  Alert.alert('Apple sign-in', 'Sign-in is taking longer than expected. Please try again.');
                }, 20_000);
                void signInWithApple().finally(() => {
                  if (!isLoggedIn) {
                    setAwaitingAuth(false);
                    if (awaitingTimerRef.current) {
                      clearTimeout(awaitingTimerRef.current);
                      awaitingTimerRef.current = null;
                    }
                  }
                });
              }}
              loading={authBusy}
              disabled={!canContinueWithApple}
            >
              <View style={styles.socialRow}>
                <MaterialCommunityIcons name="apple" size={20} color={colors.onBackground} />
                <Text style={styles.socialLabel}>Continue with Apple</Text>
              </View>
            </AppButton>
          ) : null}

          <TouchableOpacity onPress={() => navigation.navigate('Login')} accessibilityRole="button">
            <Text style={styles.link}>Already have an account? Sign in</Text>
          </TouchableOpacity>
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
      fontSize: 14,
      color: colors.onSurfaceVariant,
      lineHeight: 20,
    },
    form: {
      gap: 12,
    },
    link: {
      marginTop: 8,
      color: colors.primary,
      fontWeight: '700',
      textAlign: 'center',
    },
    socialRow: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 10,
    },
    socialLabel: {
      fontSize: 16,
      fontWeight: '600',
      color: colors.primary,
    },
  });
}
