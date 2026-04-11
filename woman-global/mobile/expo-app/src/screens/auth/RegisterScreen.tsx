import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { Screen } from '@/components/layout/Screen';
import { AppButton } from '@/components/ui/AppButton';
import { TextField } from '@/components/ui/TextField';
import type { AuthStackParamList } from '@/navigation/types';
import { firebaseRegister, getFirebaseAuth, isFirebaseConfigured } from '@/services/firebaseAuth';
import { runAuthBridge } from '@/services/session';
import { Colors } from '@/theme/colors';

type Props = NativeStackScreenProps<AuthStackParamList, 'Register'>;

export function RegisterScreen({ navigation }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const onRegister = async () => {
    if (!isFirebaseConfigured()) {
      Alert.alert(
        'Firebase not configured',
        'Set EXPO_PUBLIC_FIREBASE_* in woman-global/mobile/expo-app/.env and restart Expo.',
      );
      return;
    }
    const e = email.trim();
    if (!e || !password) {
      Alert.alert('Register', 'Email and password are required.');
      return;
    }

    setLoading(true);
    try {
      await firebaseRegister(e, password);
      const user = getFirebaseAuth().currentUser;
      if (!user) throw new Error('Registration succeeded but no user was returned.');
      const idToken = await user.getIdToken(true);
      await runAuthBridge(idToken);
      // RootNavigator will route into the app automatically via auth state.
    } catch (err) {
      Alert.alert('Registration failed', err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <View style={styles.container}>
        <Text style={styles.title}>Create account</Text>
        <Text style={styles.subtitle}>Sign up with email and password.</Text>

        <View style={styles.form}>
          <TextField
            label="Email"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            keyboardType="email-address"
            textContentType="emailAddress"
          />
          <TextField
            label="Password"
            value={password}
            onChangeText={setPassword}
            autoCapitalize="none"
            secureTextEntry
            textContentType="newPassword"
          />

          <AppButton onPress={onRegister} loading={loading}>
            Register
          </AppButton>

          <TouchableOpacity onPress={() => navigation.navigate('Login')} accessibilityRole="button">
            <Text style={styles.link}>Already have an account? Sign in</Text>
          </TouchableOpacity>
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
    paddingVertical: 8,
  },
});

