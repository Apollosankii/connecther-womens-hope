import type { User } from 'firebase/auth';

export type GoogleSignInBehavior = {
  /**
   * When true, links Google to the current anonymous Firebase user (registration after phone OTP).
   */
  shouldLinkGoogleToAnonymousUser?: () => boolean;
  /**
   * Runs after successful Google sign-in/link. When omitted, performs `runAuthBridge` only (login).
   */
  onPostGoogleAuth?: (user: User) => Promise<void>;
};
