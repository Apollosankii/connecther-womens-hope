import AsyncStorage from '@react-native-async-storage/async-storage';
import { initializeApp, getApps, type FirebaseApp } from 'firebase/app';
import { Platform } from 'react-native';
import {
  createUserWithEmailAndPassword,
  getAuth,
  GoogleAuthProvider,
  initializeAuth,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithCredential,
  signInWithEmailAndPassword,
  signOut,
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

