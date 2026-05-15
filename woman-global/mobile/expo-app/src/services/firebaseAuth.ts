import AsyncStorage from '@react-native-async-storage/async-storage';
import { initializeApp, getApps, type FirebaseApp } from 'firebase/app';
import { Platform } from 'react-native';
import {
  createUserWithEmailAndPassword,
  EmailAuthProvider,
  getAuth,
  GoogleAuthProvider,
  OAuthProvider,
  initializeAuth,
  linkWithCredential,
  onAuthStateChanged,
  reauthenticateWithCredential,
  sendPasswordResetEmail,
  sendEmailVerification,
  signInAnonymously,
  signInWithCredential,
  signInWithEmailAndPassword,
  signOut,
  updatePassword,
  type User,
} from 'firebase/auth';

import { AppConfig } from '@/services/config';

/**
 * Firebase v12: use `@firebase/auth` RN entry for AsyncStorage persistence (not `firebase/auth/react-native`).
 */
function getReactNativePersistenceForExpo() {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { getReactNativePersistence } = require('@firebase/auth') as {
    getReactNativePersistence: (storage: typeof AsyncStorage) => import('firebase/auth').Persistence;
  };
  return getReactNativePersistence(AsyncStorage);
}

let app: FirebaseApp | null = null;

export function isFirebaseConfigured(): boolean {
  const cfg = AppConfig.firebase();
  return Boolean(cfg.apiKey && cfg.projectId && cfg.appId);
}

function getFirebaseApp(): FirebaseApp {
  if (app) return app;
  const existing = getApps()[0];
  if (existing) {
    app = existing;
    return existing;
  }
  const cfg = AppConfig.firebase();
  if (!cfg.apiKey || !cfg.projectId || !cfg.appId) {
    throw new Error('Missing Firebase configuration (EXPO_PUBLIC_FIREBASE_*).');
  }
  app = initializeApp(cfg);
  return app;
}

/**
 * Expo-compatible Firebase Auth init.
 * On native, call `initializeAuth` with AsyncStorage **first**; `getAuth` alone uses in-memory persistence and triggers a Firebase warning.
 */
export function getFirebaseAuth() {
  const app = getFirebaseApp();
  if (Platform.OS === 'web') {
    return getAuth(app);
  }
  try {
    return initializeAuth(app, {
      persistence: getReactNativePersistenceForExpo(),
    });
  } catch {
    return getAuth(app);
  }
}

/** Google Sign-In (same flow as Android: ID token → Firebase credential). */
export async function firebaseSignInWithGoogleIdToken(idToken: string) {
  const auth = getFirebaseAuth();
  const credential = GoogleAuthProvider.credential(idToken);
  return signInWithCredential(auth, credential);
}

/** Anonymous Firebase user (Twilio phone-verify + registration linking, matches Android). */
export async function firebaseSignInAnonymously() {
  const auth = getFirebaseAuth();
  return signInAnonymously(auth);
}

/** Link Google to the current user (used when `currentUser.isAnonymous` after phone OTP). */
export async function firebaseLinkGoogleIdToken(idToken: string) {
  const auth = getFirebaseAuth();
  const user = auth.currentUser;
  if (!user) throw new Error('No Firebase user to link.');
  const credential = GoogleAuthProvider.credential(idToken);
  return linkWithCredential(user, credential);
}

/** Link Apple to the current user (registration after anonymous session). */
export async function firebaseLinkAppleIdentityToken(identityToken: string) {
  const auth = getFirebaseAuth();
  const user = auth.currentUser;
  if (!user) throw new Error('No Firebase user to link.');
  const provider = new OAuthProvider('apple.com');
  const credential = provider.credential({ idToken: identityToken, rawNonce: undefined });
  return linkWithCredential(user, credential);
}

/**
 * After Google link, attach email/password so the user can sign in with email later
 * (Android `ensurePasswordForFutureLogin`).
 */
export async function firebaseEnsureEmailPasswordForLogin(email: string, password: string) {
  const auth = getFirebaseAuth();
  const user = auth.currentUser;
  if (!user) throw new Error('No Firebase user.');
  const em = email.trim();
  if (!em) throw new Error('Email is required.');
  const hasEmailProvider = user.providerData.some((p) => p.providerId === EmailAuthProvider.PROVIDER_ID);
  if (hasEmailProvider) return updatePassword(user, password);
  return linkWithCredential(user, EmailAuthProvider.credential(em, password));
}

export async function firebaseSendEmailVerification() {
  const auth = getFirebaseAuth();
  const user = auth.currentUser;
  if (!user?.email) throw new Error('No email on this account.');
  return sendEmailVerification(user);
}

export async function firebaseSignIn(email: string, password: string) {
  const auth = getFirebaseAuth();
  return signInWithEmailAndPassword(auth, email, password);
}

export async function firebaseRegister(email: string, password: string) {
  const auth = getFirebaseAuth();
  return createUserWithEmailAndPassword(auth, email, password);
}

export async function firebaseResetPassword(email: string) {
  const auth = getFirebaseAuth();
  return sendPasswordResetEmail(auth, email);
}

/** Email/password accounts only: reauthenticate then set a new password (matches Android PasswordChangeActivity). */
export async function firebaseUpdatePasswordWithCurrent(currentPassword: string, newPassword: string): Promise<void> {
  const auth = getFirebaseAuth();
  const user = auth.currentUser;
  if (!user) throw new Error('Sign in first.');
  const email = user.email?.trim();
  if (!email) throw new Error('This account has no email on file; password change is not available.');
  if (newPassword.length < 6) throw new Error('Password must be at least 6 characters.');
  const credential = EmailAuthProvider.credential(email, currentPassword);
  await reauthenticateWithCredential(user, credential);
  await updatePassword(user, newPassword);
}

export async function firebaseSignOut() {
  if (!isFirebaseConfigured()) return;
  const auth = getFirebaseAuth();
  return signOut(auth);
}

export function listenToAuthState(cb: (user: User | null) => void) {
  if (!isFirebaseConfigured()) {
    cb(null);
    return () => {};
  }
  const auth = getFirebaseAuth();
  return onAuthStateChanged(auth, cb);
}

