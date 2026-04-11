# ConnectHer (Expo) — Frontend rebuild

This is a **new Expo (React Native) app** intended to rebuild the existing Android frontend as a **cross-platform** app (iOS-first) while **leaving the existing Android codebase untouched** under `woman-global/mobile/android/`.

## Requirements / scope

- **Included**: Firebase Authentication (**email/password** + **Google**), auth-bridge exchange, Supabase-backed API layer (PostgREST/RPC/Storage), subscriptions **status**, **splash screen**, **onboarding** (aligned with Android copy).
- **Excluded**: **Paystack** (no keys, no checkout, no `/functions/v1/paystack-*` calls), App Store deployment setup.

## Setup

### 1) Install dependencies

From this folder:

```bash
npm install
```

### 2) Environment variables

Copy:

- `.env.example` → `.env`

Fill in:

- `EXPO_PUBLIC_SUPABASE_URL`
- `EXPO_PUBLIC_SUPABASE_ANON_KEY`
- `EXPO_PUBLIC_FIREBASE_*` (Firebase Web SDK config)
- Optional: `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` (same as Android `R.string.default_web_client_id`; a default is baked in if omitted)

These values are read from `app.config.ts` via Expo `extra`.

### Google Sign-In (Expo)

Uses `expo-auth-session` **ID token** flow, then Firebase `GoogleAuthProvider` (same idea as Android `LoginActivity`).

`expo-auth-session` requires **`androidClientId` on Android** and **`iosClientId` on iOS**. This app sets both to your **Web client ID** by default (same value as Android `default_web_client_id`) so Expo Go can start the flow without extra env vars. For **production / Play Store builds**, create **Android** and **iOS** OAuth clients in [Google Cloud Console](https://console.cloud.google.com/) (package name + signing SHA-256 for Android; bundle ID for iOS) and set `EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID` / `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID`.

On the **Web client**, under **Authorized redirect URIs**, add the **HTTPS** proxy URL Expo uses — **not** `exp://192.168.x.x:8082` (Google will reject that for a Web client).

1. Run `npx expo config --type public` and note `originalFullName`, or set `EXPO_PUBLIC_EXPO_OWNER` in `.env` so it becomes `@<owner>/connecther`.
2. Add exactly: `https://auth.expo.io/<originalFullName>`  
   Example: `https://auth.expo.io/@jane/connecther`  
   If you are not logged into Expo, you may see `@anonymous/connecther-…` — add that exact URL to Google Console, or run `npx expo login` and set `EXPO_PUBLIC_EXPO_OWNER` for a stable URL.

If you get “no ID token” or redirect errors, fix these redirect URIs first.

#### Why does this use a “web” redirect instead of native Google Sign-In?

Your **Kotlin Android app** uses **Google Play Services** (`GoogleSignIn` + `requestIdToken(webClientId)`). That is a **native SDK** flow: no OAuth redirect page in a browser in the same way.

This **Expo** build uses **`expo-auth-session`**, which is built on **OAuth 2.0 in the system browser** (Chrome Custom Tabs on Android, etc.). Google returns tokens by **redirecting** to a registered **redirect URI** (for Expo Go this is often a hosted proxy such as `https://auth.expo.io/...`; for a **standalone/dev build** it can be a custom scheme like `com.womanglobal.connecther:/oauthredirect`). That is normal for **managed Expo** and **Expo Go**, because the native Google Sign-In module is not part of Expo Go.

To get the **same style of native Google Sign-In** as your Android app, you typically:

1. Create a **development build** (`expo-dev-client` + `expo prebuild` / EAS Build), and  
2. Add **`@react-native-google-signin/google-signin`**, with an **Android OAuth client** in Google Cloud (package name + **release/debug SHA-1/256**), then pass the ID token to Firebase as you do today.

Until then, expect a **browser-based OAuth** step; it is still a legitimate way to obtain a Google **ID token** for Firebase.

#### “Access blocked” / “does not comply with OAuth2 policy” / redirect errors

Common causes:

1. **Redirect URI mismatch** — The exact URI Google shows in the error must be added under the OAuth client you are using (**Web** client for Expo’s proxy flow) → **Authorized redirect URIs**. Even a trailing slash or `http` vs `https` mismatch will fail.
2. **Wrong OAuth client type** — Using only a **Web** client while Google expects a **native** Android client for your redirect/scheme can cause policy or configuration errors. For **production Android**, create an **Android** OAuth client (type *Android application*) with package `com.womanglobal.connecther` and your keystore SHA-256, set `EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID`, and use a **dev build** with native Google Sign-In or ensure Expo’s redirect URIs match the **Web** client you use.
3. **OAuth consent screen** — If the project is in **Testing**, only **test users** you add in Google Cloud can sign in. Everyone else sees “access blocked” style messages. Either add testers or **publish** the app (and complete verification if Google requests it for your scopes).
4. **Using the Web client ID as `androidClientId`** — Convenient for quick Expo Go tests, but **not** what Google recommends for shipped Android apps; it often breaks or triggers policy messaging once you leave simple dev setups.

If you paste the **exact** `redirect_uri=` value from Google’s error page, you can add that URI verbatim to the correct client in Google Cloud Console.

### Splash screen

Native splash is configured in `app.config.ts` (`expo-splash-screen` plugin, white background + `assets/splash-icon.png`). The app calls `SplashScreen.preventAutoHideAsync()` until auth + first-launch prefs are ready, then hides — similar to Android `MainActivity` + `installSplashScreen()`.

## How authentication works (important)

This app mirrors the Android architecture:

1. User signs in with **Firebase Auth** (email/password)
2. App calls Supabase Edge Function **`auth-bridge`**:
   - `POST {SUPABASE_URL}/functions/v1/auth-bridge`
   - header `Authorization: Bearer <firebase_id_token>`
3. Response returns a **Supabase-signed JWT** (`supabase_jwt`) plus `user_id` and `firebase_uid`
4. App stores `supabase_jwt` in **SecureStore** and uses it as `Authorization: Bearer ...` for Supabase PostgREST/RPC calls

## Run the app

Start Metro:

```bash
npx expo start
```

### iOS Simulator (Expo)

**iOS Simulator requires macOS + Xcode.** On a Mac:

1. Install Xcode (App Store) and open it once to finish setup
2. In this folder:

```bash
npx expo start
```

3. Press **`i`** in the Expo CLI to open iOS Simulator

### Expo Go vs Dev Client

- This project is designed to work in **Expo Go** for development.
- If you later add native-only modules that Expo Go can’t load, switch to a **dev client** (`expo-dev-client`) and use `npx expo run:ios` on macOS.

## Project structure

```text
src/
  components/
    layout/
    ui/
  hooks/
  navigation/
  providers/
  screens/
    auth/
    onboarding/
    main/
    stack/
  services/
    api/
    supabase/
  theme/
  types/
  utils/
```

