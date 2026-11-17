# Day by Day 2.0

Native Kotlin / Jetpack Compose habit tracker, Android 8.0+ (API 26).

## Features

- Forest-green dashboard with curved cards, daily completion ring, next reminder, seven-day bar chart, current streaks, and 30-day completion counts. Every metric uses actual habit data.
- Custom sprout/check launcher logo and notification icon; dark forest and light cream themes.
- Create, edit, delete and check off habits; select weekdays; undo check-ins for the last seven days. Streaks count consecutive scheduled days and skip rest days.
- Local notification reminders, precise timing with optional system permission, and a 10-minute snooze action. Reschedules on reboot or timezone/time changes. Completed habits are skipped.
- Optional per-habit spoken reminders using Android Text-to-Speech. Enable notifications and precise alarms before selecting voice. Preferences: include current time, prefer Hausa (English fallback), quiet hours 22:00–07:00. Voice respects silent mode, Do Not Disturb, active music and audio focus. Preview voice in Settings. Requires an installed working TTS engine; background restrictions can suppress voice while leaving the normal notification available. Approximate alarms can be delayed.
- Export JSON backup through Android's document picker; validate and confirm before replacing habits from a backup. Maximum 1 MB / 1000 habits. Voice is disabled on restore until explicitly enabled. Backups include names, schedules and check-ins, not device preferences; store them privately.
- Existing version-1 saved habits migrate in memory with daily schedules and no reminders; no data reset is performed. Unreadable data is left untouched, editing is blocked, and backup restore remains available.

## Signing and updates — read before installing

The requested workflow generates a new temporary debug key on each run. APKs from different runs are therefore signed differently. Android may require uninstalling the older APK before installing a new one, which DELETES its local data. Version 1 did not include an export screen; record any important check-ins before uninstalling it. Version 2 has export/restore, so export before future updates and restore afterwards. Automatic backups are disabled. A stable securely managed signing key is needed for future seamless updates; this project does not contain one.

## Build and APK download

Open the folder in Android Studio with Android SDK 35, Java 17+, and exactly Gradle 9.3.1. The version catalog pins AGP 9.1.1 and Compose compiler 2.2.10, using AGP's built-in Kotlin support. There is no Gradle wrapper in this source distribution.

GitHub Actions runs on pushes to main and manual dispatch. It installs supported SDK packages (not the removed `tools` package), sets Temurin Java 17 and Gradle 9.3.1, runs `gradle :app:testDebugUnitTest --stacktrace --no-daemon`, then builds with `gradle :app:assembleDebug --stacktrace --no-daemon`.

The expected APK is `app/build/outputs/apk/debug/app-debug.apk`. The workflow uploads only that file as artifact `app-debug-apk`, then publishes a latest release with tag `debug-apk-build-<run-number>-<run-attempt>` and asset `<repository-name>-debug-build-<run-number>.apk`. Release failures leave the Actions artifact available. Uses only the built-in GitHub token. Temporary signing keys are never cached or uploaded and are removed by cleanup.

## Verification

Unit tests cover scheduled-day streaks, rest days, missed days, reversible check-ins, creation boundaries and future reminder times. Workflow shell syntax and Android XML are checked during development. CI verifies compilation, tests and APK packaging; reminders, TTS, document-picker backups, accessibility and responsive UI still require real-device verification. No emulator or device is attached in the development environment.

## Scope

The interface is currently English; Hausa is an optional TTS preference, dependent on voices installed on the device. No account, cloud sync, ads, widget or quantitative habit targets. No internet permission. Force-stopping the app disables scheduled alarms until the app is reopened. Quiet-hour reminders are skipped, not replayed later.
