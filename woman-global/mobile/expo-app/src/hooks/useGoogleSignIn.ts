/**
 * TypeScript resolves this file. At bundle time, Metro prefers `useGoogleSignIn.native.ts`
 * (iOS/Android) or `useGoogleSignIn.web.ts` (web) instead of this shim.
 */
export type { GoogleSignInBehavior } from './useGoogleSignIn.types';
export { useGoogleSignIn } from './useGoogleSignIn.native';
