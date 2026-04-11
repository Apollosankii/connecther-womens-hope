import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { TextField } from '@/components/ui/TextField';
import { useGoogleSignIn } from '@/hooks/useGoogleSignIn';
import type { AuthStackParamList } from '@/navigation/types';
import {
  firebaseResetPassword,
  firebaseSignIn,
  getFirebaseAuth,
  isFirebaseConfigured,
} from '@/services/firebaseAuth';
import { runAuthBridge } from '@/services/session';
import { Colors } from '@/theme/colors';

type Props = NativeStackScreenProps<AuthStackParamList, 'Login'>;

export function LoginScreen({ navigation }: Props) {
  const { request, busy: googleBusy, signInWithGoogle, canUseGoogle } = useGoogleSignIn();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [emailError, setEmailError] = useState<string | undefined>();
  const [passwordError, setPasswordError] = useState<string | undefined>();
  const authBusy = loading || googleBusy;

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

  return (
    <Screen>
      <View style={styles.container}>
        <Text style={styles.title}>Sign in</Text>
        <Text style={styles.subtitle}>Welcome back. Use your email and password.</Text>

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
            onPress={() => void signInWithGoogle()}
            disabled={!request || !canUseGoogle || authBusy}
            loading={googleBusy}
          >
            <View style={styles.googleRow}>
              <MaterialCommunityIcons name="google" size={20} color={Colors.primary} />
              <Text style={styles.googleLabel}>Continue with Google</Text>
            </View>
          </AppButton>

          <View style={styles.footerRow}>
            <Text style={styles.footerText}>Don&apos;t have an account?</Text>
            <TouchableOpacity onPress={() => navigation.navigate('Register')} accessibilityRole="button">
              <Text style={[styles.link, styles.footerLink]}>Register</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    gap: 14,
  },
  title: {
    fontSize: 28,
    fontWeight: '800',
    color: Colors.onBackground,
  },
  subtitle: {
    color: Colors.onSurfaceVariant,
    lineHeight: 20,
  },
  form: {
    marginTop: 12,
    gap: 12,
  },
  link: {
    color: Colors.accent,
    fontWeight: '600',
  },
  footerRow: {
    marginTop: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  footerText: {
    color: Colors.onSurfaceVariant,
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
    color: Colors.primary,
  },
});

