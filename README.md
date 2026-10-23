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
| Latest release | **v1.5** ([download](../../releases/latest)) |
| Version name | 1.5 |
| Version code | 6 |
| Package | `com.keyrecord.app` |
| Release date | 2026-10-23 |
| APK | `app-release.apk` (~6.1 MB) |
| Min / target SDK | 24 / 36 |
| Permissions | `RECORD_AUDIO` (keyboard dictation only) + an AndroidX-generated local broadcast permission. **No `INTERNET` permission.** |

### Changelog

**v1.5** (2026-10-23)

- **Pixel-accurate iOS keyboard replica** — rebuilt the built-in keyboard to match
  Apple's system keyboard using reverse-engineered iOS geometry (key height/width,
  gap, corner radius and special-key width ratios), the iconic iOS palette
  (light-gray keys, darker special keys, blue accent, wide space bar), and the
  exact bottom row: globe · 123 · space · mic · return.
- **Much better Chinese pinyin** — the offline engine now uses the
  [rime-ice](https://github.com/iDvel/rime-ice) (GPL-3.0) phrase dictionaries
  plus pypinyin, with a curated essential-words seed. The candidate bar does
  **prefix associate**: typing `ni` already suggests `你好 / 你们 / 你的 …`;
  `nihao → 你好`, `women → 我们`, `beijing → 北京 背景 北境 …`.
- **Voice input** — the microphone key triggers on-device / system speech
  dictation (no `INTERNET` permission is declared; recognition is delegated to the
  system speech service, which needs `RECORD_AUDIO` and is requested on first use).
- Same local-only, real-time recording as before.

- **Chinese pinyin input** — the built-in keyboard now handles 中文拼音 with a
  real offline engine (dictionaries derived from the pypinyin data plus a corpus
  frequency table, packaged as local assets, no network).
- **Candidate bar (联想条)** — an iOS-style suggestion strip above the keys shows
  the typed pinyin and live candidates: full-phrase matches (e.g. `nihao → 你好`,
  `women → 我们`) first, then single-character candidates for the first syllable,
  ordered by real usage frequency.
- **Long-press symbols** — hold a key to reveal alternate characters (accented
  letters like áàâä for `a`, and `…•` for `.`, `、` for `,`, `€£¥¢` for `$`,
  etc.), then slide to pick. Matches the iOS long-press popup.
- **中 / EN switch** — the globe key now toggles between 中文拼音 and English;
  the space bar label shows the active mode (`拼音` / `ABC`).
- Same local-only, real-time recording as before.

**v1.3** (2026-10-23)

- **iOS-style keyboard** — rebuilt the built-in recording keyboard to match
  Apple's system keyboard: rounded gray keys, a long space bar, a
  globe / 123 / comma / space / period / return bottom bar, shift
  capitalization (tap = one letter, double-tap = caps lock), 123 / #+=
  number & symbol pages, and the iOS key-pop animation on press.
- Same real-time local recording as before.

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
- **Built-in iOS-style keyboard** with Chinese pinyin input, a live candidate bar
  (联想条), and long-press symbol popups; globe key switches 中文 / English
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
