# ConnectHer (Expo) — Frontend rebuild

This is a **new Expo (React Native) app** intended to rebuild the existing Android frontend as a **cross-platform** app (iOS-first) while **leaving the existing Android codebase untouched** under `woman-global/mobile/android/`.

## Requirements / scope

- **Included**: Firebase Authentication (**email/password** + **Google** + **Apple on iOS**), auth-bridge exchange, Supabase-backed API layer (PostgREST/RPC/Storage), **subscriptions** (Android: Paystack; iOS: App Store via RevenueCat), **splash screen**, **onboarding** (aligned with Android copy), and **EAS build profiles** (see below) for when you are ready to ship.
- **Payments**: Android uses Paystack (`paystack-checkout` edge function). iOS uses **Apple auto-renewable subscriptions** via **RevenueCat** (`react-native-purchases`) — no Paystack or external payment links on iOS (App Store compliance). Connect grants use the same Supabase `user_plan_subscriptions` tables as Kotlin.
- Use a **development build** (not Expo Go) for Paystack WebView, RevenueCat, and native sign-in.
- Apple/Google store listing and review are still your responsibility outside this repo.

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
- `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` (Web application client ID from Firebase / Google Cloud — same idea as Android `default_web_client_id`; used by `@react-native-google-signin/google-signin` as `webClientId` for the ID token Firebase expects)
- Optional: `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID` (iOS OAuth client in Google Cloud; passed to `GoogleSignin.configure` when set and different from the Web ID)

These values are read from `app.config.ts` via Expo `extra`.

### Google Sign-In (native, development build)

Google sign-in uses **`@react-native-google-signin/google-signin`** (native account picker / Play Services on Android), then the same Firebase path as the Kotlin app: **Google ID token → `GoogleAuthProvider.credential` → `runAuthBridge`**.

**Expo Go cannot load this native module.** Use a **development build** (`expo-dev-client` is included in this project):

1. After changing native config (e.g. Google plugin), run **`npx expo prebuild`** (or EAS Build) so iOS/Android projects pick up the config plugin.
2. **`iosUrlScheme`** in [`app.config.ts`](app.config.ts) must match your **reversed iOS client ID** (`com.googleusercontent.apps.<…>` from Google Cloud / `REVERSED_CLIENT_ID`).
3. **Android:** Register your **debug and release SHA-1/SHA-256** fingerprints in [Google Cloud Console](https://console.cloud.google.com/) for package `com.womanglobal.connecther` (Android OAuth client). Without this, sign-in can fail on device.
4. Start Metro for the dev client: **`npm run start:dev`** (or `npx expo start --dev-client`), then open the dev build app (not Expo Go).

On **web**, the “Continue with Google” action shows a short message that native Google sign-in is only available in the mobile app.

### Apple Sign-In (iOS, development build)

**Sign in with Apple** uses `expo-apple-authentication` on **iOS only** (Login and Register), then Firebase `OAuthProvider('apple.com')` and `runAuthBridge`. Enable the **Sign in with Apple** capability for `com.womanglobal.connecther` in Apple Developer and EAS credentials. Not available in Expo Go or on Android.

### Subscriptions and payments

| Platform | Provider | Client path |
|----------|----------|-------------|
| **Android** | Paystack | `src/services/payments/paystackService.ts` → `paystack-checkout` edge → WebView checkout |
| **iOS** | Apple IAP (RevenueCat) | `src/services/payments/iapService*.ts` + `hooks/useIAP.ts` |

**Environment (client):**

- `EXPO_PUBLIC_REVENUECAT_IOS_API_KEY` — RevenueCat public iOS SDK key

**Supabase secrets (server):**

- `REVENUECAT_WEBHOOK_SECRET` — verify webhook calls to `functions/v1/revenuecat-webhook`

**RevenueCat setup (iOS):**

1. Create a RevenueCat project and add the iOS app (`com.womanglobal.connecther`).
2. In App Store Connect, create an **auto-renewable subscription group** and products (e.g. `connecther_basic_monthly`, `connecther_premium_monthly`, `connecther_yearly`).
3. Map products in RevenueCat offerings; set `subscription_plans.apple_product_id` in Supabase to match App Store product IDs (see migration `20260620120000_store_subscriptions_iap.sql`).
4. Configure webhook URL: `https://<project-ref>.supabase.co/functions/v1/revenuecat-webhook`
5. Set RevenueCat **app user id** to Supabase `users.id` (the app calls `Purchases.logIn(userId)` after auth-bridge).
6. Build with EAS / dev client and test in **Sandbox** Apple ID.

**Deploy edge functions:**

```bash
supabase functions deploy paystack-checkout
supabase functions deploy revenuecat-webhook
supabase functions deploy iap-sync
```

Apply migration `woman-global/supabase/migrations/20260620120000_store_subscriptions_iap.sql` before using iOS IAP.

#### “Access blocked” / OAuth consent / Play Services

- **OAuth consent screen** — If the Google Cloud project is in **Testing**, only listed **test users** can sign in until you publish the consent screen.
- **Play Services** — On Android devices without Google Play services, sign-in may fail; the app surfaces a dedicated message when Play Services are missing or outdated.

### Splash screen

Native splash is configured in `app.config.ts` (`expo-splash-screen` plugin, white background + `assets/connecther-mark.png`). The app calls `SplashScreen.preventAutoHideAsync()` until auth + first-launch prefs are ready, then hides — similar to Android `MainActivity` + `installSplashScreen()`.

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

- **Google sign-in** requires a **development build** (native module). After `npx expo prebuild`, use `npx expo run:android` / `npx expo run:ios` once to install the dev client, then **`npm run start:dev`** when developing.
- Other flows may still be tried in **Expo Go** where no native-only modules are required.

## Signed builds (EAS) — Android APK/AAB + iOS TestFlight

This repo includes [`eas.json`](eas.json) with two profiles:

- **preview**: internal distribution, **Android APK**
- **production**: store distribution, **Android AAB** + iOS store build

### Prereqs

- Install EAS CLI:

```bash
npm i -g eas-cli
```

- Authenticate:

```bash
eas login
```

- Initialize EAS project (writes the EAS project ID that builds require):

```bash
eas init
```

If you prefer env vars, set `EAS_PROJECT_ID` (or `EXPO_PUBLIC_EAS_PROJECT_ID`) to the project UUID and keep `app.config.ts` as-is.

### Versioning / build numbers

`app.config.ts` reads:

- `ANDROID_VERSION_CODE` (default `1`)
- `IOS_BUILD_NUMBER` (default `"1"`)

Increment these for each store build.

### Build Android (signed)

- **Signed internal APK**:

```bash
eas build -p android --profile preview
```

- **Signed Play Store AAB**:

```bash
eas build -p android --profile production
```

### Build iOS + submit to TestFlight

On iOS you must have Apple Developer access configured in EAS.

- Build:

```bash
eas build -p ios --profile production
```

- Submit to TestFlight:

```bash
eas submit -p ios --profile production
```

### Android native build (JDK / `JAVA_HOME`)

`npx expo run:android` runs **Gradle**, which needs a **JDK** and an **Android SDK**. This repo’s config plugin (see `./plugins/withGradleJavaHome.js`) runs at **prebuild** and:

- writes **`org.gradle.java.home`** into `android/gradle.properties` when it finds a valid JDK (Android Studio **JBR**, a good `JAVA_HOME`, or **`EXPO_ANDROID_JAVA_HOME`**), and  
- writes **`sdk.dir`** into `android/local.properties` when it finds an SDK (`ANDROID_HOME`, **`EXPO_ANDROID_SDK_ROOT`**, or the default per-OS path).

Re-run **`npx expo prebuild --platform android`** after changing those env vars so the generated files update. If **`prebuild --clean`** fails with `EBUSY` on Windows, close anything locking the `android` folder (Explorer, antivirus, Gradle daemon) and retry.

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

