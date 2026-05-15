import type { PropsWithChildren } from 'react';
import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { User } from 'firebase/auth';

import { usePushRegistration } from '@/hooks/usePushRegistration';
import { firebaseSignOut, isFirebaseConfigured, listenToAuthState } from '@/services/firebaseAuth';
import { loginIapUser } from '@/services/payments/iapService';
import {
  clearSession,
  getFirebaseUid,
  getSupabaseJwt,
  getUserId,
  subscribeToSessionChanges,
} from '@/services/supabase/tokenStore';

type AuthContextValue = {
  firebaseUser: User | null;
  /** True only for non-anonymous users with an auth-bridge session present. */
  isLoggedIn: boolean;
  /** True while a non-anonymous Firebase user exists but bridge session isn't ready yet. */
  authTransitioning: boolean;
  initializing: boolean;
  firebaseReady: boolean;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [firebaseUser, setFirebaseUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [sessionReady, setSessionReady] = useState(false);
  const [sessionInitializing, setSessionInitializing] = useState(true);

  useEffect(() => {
    const unsub = listenToAuthState((u) => {
      setFirebaseUser(u);
      setInitializing(false);
    });
    return unsub;
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function checkSessionOnce(user: User | null) {
      if (!user || user.isAnonymous) {
        if (!cancelled) {
          setSessionReady(false);
          setSessionInitializing(false);
        }
        return;
      }

      try {
        const [jwt, userId, firebaseUid] = await Promise.all([getSupabaseJwt(), getUserId(), getFirebaseUid()]);
        const ok = Boolean(jwt && userId && firebaseUid && firebaseUid === user.uid);
        if (!cancelled) {
          setSessionReady(ok);
          setSessionInitializing(false);
        }
        if (ok && userId) {
          void loginIapUser(userId).catch(() => {});
        }
      } catch {
        if (!cancelled) {
          setSessionReady(false);
          setSessionInitializing(false);
        }
      }
    }

    // Whenever Firebase auth changes, re-check bridge session and subscribe to tokenStore updates.
    setSessionInitializing(true);
    void checkSessionOnce(firebaseUser);

    const unsubSession = subscribeToSessionChanges(() => {
      void checkSessionOnce(firebaseUser);
    });

    return () => {
      cancelled = true;
      unsubSession();
    };
  }, [firebaseUser?.uid, firebaseUser?.isAnonymous]);

  const isLoggedIn = Boolean(firebaseUser && !firebaseUser.isAnonymous && sessionReady);
  usePushRegistration(isLoggedIn);
  const authTransitioning = Boolean(
    firebaseUser &&
      !firebaseUser.isAnonymous &&
      // While we are checking or waiting for bridge tokens to be written.
      (sessionInitializing || !sessionReady),
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      firebaseUser,
      isLoggedIn,
      authTransitioning,
      initializing: initializing || sessionInitializing,
      firebaseReady: isFirebaseConfigured(),
      signOut: async () => {
        await firebaseSignOut();
        await clearSession();
      },
    }),
    [firebaseUser, isLoggedIn, authTransitioning, initializing, sessionInitializing],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

