# Keytrace

A local keystroke recorder for Android, built with Material 3. It captures text
typed into any app through the Accessibility API and stores everything on the
device. It requests **no network permission**, so recorded data never leaves
the phone.

> Use only on devices you own or are authorized to test.

## Highlights

- **Material 3 UI** with dynamic color (Android 12+) and light/dark themes
- **Dashboard** with live status, keystroke/app counters, and recent activity
- **Logs** screen with search and export
- **No root**, no network access — all data stays in app-private storage
- Service is enabled manually in system Accessibility settings

## Tech

| | |
|---|---|
| Package | `com.keyrecord.app` |
| compileSdk / targetSdk | 36 |
| minSdk | 24 (Android 7.0+) |
| Gradle | 8.14.6 |
| UI | Material Components for Android, RecyclerView |

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

## Usage

1. Install `app-release.apk`.
2. Open Keytrace → **Enable service** → turn it on in Accessibility settings.
3. Type in any app; entries show up in **Logs**. Export / clear from the menu.
