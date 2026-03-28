# ConnectHer Android

ConnectHer mobile app for Android. Part of the ConnectHer platform.

## Setup

1. Open in Android Studio
2. Configure `gradle.properties` with your Supabase URL/anon key and Clerk publishable key
3. Add `google-services.json` to `app/` (for push notifications)
4. Run `./gradlew assembleDebug` or build from Android Studio

## Build

```bash
./gradlew assembleDebug   # Debug APK
./gradlew assembleRelease # Release APK (requires keystore)
```

## Related repos

- [connecther-admin-portal](https://github.com/Apollosankii/connecther-admin-portal) - Admin dashboard
