# SEELI — Android

See more. Live more. The Android version of SEELI (Kotlin + Jetpack Compose), built to match the iOS app
screen for screen and talking to the same backend (`seeli-api`).

- See → shoot → let go: the photo stays hidden and comes back after ~30 days (reunion).
- Friends only via QR. A friend's photo shows once in *Let's connect*, then it's gone.
- Vietnamese and Japanese (shared `Localizable.strings` tables in `app/src/main/assets/l10n`), vertical Japanese text.
- Home-screen widgets (Glance): *Today* and *Words of the day*. Reunion and friend-post notifications (WorkManager).
- Google sign-in via Credential Manager (`seeli.googleWebClientId` in `gradle.properties`).

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:bundleRelease        # Play Store bundle (needs keystore.properties + upload key, not in git)
```

`keystore.properties`:

```
storeFile=seeli-upload.jks
storePassword=…
keyAlias=seeli-upload
keyPassword=…
```
