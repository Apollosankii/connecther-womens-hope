import type { User } from 'firebase/auth';

export type AppleSignInBehavior = {
  /**
   * When true, links Apple to the current anonymous Firebase user (registration flow).
   */
  shouldLinkAppleToAnonymousUser?: () => boolean;
  /** Runs after successful Apple sign-in/link. When omitted, performs `runAuthBridge` only (login). */
  onPostAppleAuth?: (user: User) => Promise<void>;
};
