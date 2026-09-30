# Site Manager

A native Android app to manage and monitor multiple websites from one place.

## Features

- **Dashboard** — start/stop site monitoring, live up/down/total stats, per-site status with HTTP code, latency and last-checked time. Status comes from real HTTP checks (OkHttp HEAD with GET fallback).
- **Sites** — full CRUD for multiple sites, each with its own site URL, optional admin-panel URL (cPanel / phpMyAdmin link) and notes. Stored in Room.
- **Browser** — in-app WebView browser with progress bar, back/forward/reload and "open externally".
- **Files** — manages the app's own backup files: list with size/date, share via FileProvider, delete.
- **Logs** — Room-backed event log of checks, errors and user actions, with clear-all.
- **Settings** — monitoring interval, check timeout, autostart-on-boot (runs a check cycle after reboot), system/light/dark theme (DataStore), JSON backup to the files dir, restore via the system file picker, raw JSON config editor with validation, and an About screen.

## Build

CI builds the debug APK on every push to `main` (`.github/workflows/build-apk.yml`):
JDK 17 (Temurin) + Android SDK + Gradle 8.7 via `gradle/actions/setup-gradle`,
running `gradle assembleDebug` (no wrapper jar is committed to the repo).

The built APK is published to the `dist` branch as `app-debug.apk` after every
successful build. If a build fails, the Gradle error tail is published to the
`dist` branch as `build-error.log` instead.

Local build requires the Android SDK (compileSdk 34) and JDK 17.
