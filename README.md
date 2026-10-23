# Keytrace

A local keystroke recorder for Android, built with Material 3. It captures text
typed into any app through the Accessibility API **and** a built-in keyboard
(InputMethodService), and stores everything on the device. It requests
**no network permission**, so recorded data never leaves the phone.

> Use only on devices you own or are authorized to test.

## Release information

This section is updated with every release. The table always reflects the
latest published version.

| | |
|---|---|
| Latest release | **v1.2** ([download](../../releases/latest)) |
| Version name | 1.2 |
| Version code | 3 |
| Package | `com.keyrecord.app` |
| Release date | 2026-10-23 |
| APK | `app-release.apk` (~4.7 MB) |
| Min / target SDK | 24 / 36 |
| Permissions | none (except an AndroidX-generated local broadcast permission) |

### Changelog

**v1.2** (2026-10-23)

- **New: built-in keyboard (Keytrace 键盘)** — an InputMethodService that
  records keystrokes in real time as they are committed. It works everywhere,
  including floating windows and split screen, where accessibility events can
  be delayed or missing.
- **Fixed: dashboard status** — the service state is now read via the official
  `AccessibilityManager` API, so an enabled service no longer shows as
  "paused" on some ROMs.
- **New: keyboard status card** on the dashboard showing whether the keyboard
  is enabled and currently selected, with a shortcut to input-method settings.
- Word-level aggregation: consecutive keys are grouped into one readable
  entry (space / enter / backspace flush the buffer).

**v1.1** (2026-10-23)

- Localized the entire app UI to Simplified Chinese (app name stays "Keytrace")
- Service lifecycle events (started / interrupted / stopped) are now recorded
  in Chinese as well
- Replaced all hand-drawn icons with official Material Icons from
  [google/material-design-icons](https://github.com/google/material-design-icons)
  (50k+ stars), including a new launcher icon
- Added icons to dashboard stat cards, recent-activity header, and menus

**v1.0** (2026-10-23)

- Initial release: Material 3 dashboard, logs with search/export, settings
- Accessibility-based capture, local-only storage, no network permission

## Highlights

- **Material 3 UI** with dynamic color (Android 12+) and light/dark themes
- **Simplified Chinese interface** across dashboard, logs, and settings
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
| Icons | [Material Icons](https://github.com/google/material-design-icons) |

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

## Usage

1. Install `app-release.apk` from the [latest release](../../releases/latest).
   > Note: every app update makes Android reset its Accessibility and
   > input-method grants for that app — re-enable them once after installing.
2. Open Keytrace → **开启服务** (Enable service) → turn it on in Accessibility
   settings.
3. For real-time capture, tap **开启键盘** (Enable keyboard) → enable
   **Keytrace 键盘** in system input-method settings → switch your current
   keyboard to it (use the 切换 key on the keyboard or the keyboard-switch
   button in the navigation bar).
4. Type in any app; entries show up in **记录** (Logs). Export / clear from the
   menu.
