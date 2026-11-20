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


## Version 2.1

Application ID / namespace: `com.novastack.daybyday`. This is a separate Android app from the previous `com.example` build: export a backup from 2.0, install 2.1 alongside it, then restore your backup. Device-specific notification/alarm permissions must be granted again.

Habit setup now has a native calendar picker (today by default) and native 12-hour time picker with AM/PM. Reminder times are stored internally as 24-hour `HH:mm`; cards display AM/PM. Start dates may be past or future, but editing cannot move the start after an existing check-in. Future habits begin on the first selected weekday on or after that date.


## Version 2.2 — Focus timer

Optional per-habit duration (1 minute to 24 hours), Start action alongside the 10-minute reminder snooze, and a dedicated focus screen. Shows total duration, remaining and elapsed time, a main progress ring and millisecond ring, pause/resume, and confirmed reset. One active session; replacing another session requires confirmation. Completion offers an explicit check-in, only on a scheduled day.

The countdown uses Android's monotonic elapsed clock (including sleep), persisted state, a system notification chronometer and an AlarmManager completion alarm; it does not rely on a continuously running app or foreground service. Exact-alarm permission improves completion alert timing; otherwise the alert can be delayed. Displayed milliseconds are visual progress, not precision timing guarantees. After reboot, the session pauses using a bounded wall-clock estimate; force-stop prevents alerts until the app is reopened. Timer state is not part of backups; habit durations are. Old backup formats 1 and 2 remain readable.

Before installing another debug build, export your habits: the temporary signing key still changes between builds and an uninstall may be needed, which clears local data.


## DailyStride 2.3

Renamed the visible app to DailyStride, “Small steps. Lasting change.” Application ID remains `com.novastack.daybyday`; backup app identifier remains `DayByDay` for compatibility.

Optional planned work start is separate from reminder time. When no planned start is specified, a reminder anchors the focus duration. Weekly overlap checking rejects intersecting work intervals, duplicate instantaneous reminders, or reminder instants inside another task's work interval. Checks include creation dates, weekday recurrence, future schedules, different AM/PM times and midnight/week-boundary spillover. Exact end-to-start adjacency is allowed. No-duration habits occupy a point, not a whole interval. Unscheduled habits do not reserve time. Existing conflicts are preserved and surfaced for editing; conflicting backups are rejected before any replacement. A single focus timer still runs at a time. Checks use device local civil time; daylight-saving transitions can affect actual elapsed work duration.


## DailyStride 2.4 — Lock-screen timer and completion voice

The ongoing focus notification now uses public lock-screen visibility by default, an alarm category, a live system chronometer and View timer / Pause / Resume actions. Settings offers Timer on lock screen (task names may be visible). View timer opens the full countdown above the keyguard on supported devices; if that screen was already open when locking, it can remain visible when the screen is switched on. It never bypasses device authentication: marking a habit complete requires unlocking. No full-screen-intent, overlay, accessibility-service or media-session permission is requested, and the timer does not pretend to be a music player. Android/OEM notification privacy and silent-notification settings can hide the timer. It does not forcibly launch a full-screen activity when the display wakes.

Speak when timer ends is opt-in in Settings. It announces completion once per completed session using the existing TTS service, Hausa preference with English fallback, and current-time preference. It respects quiet hours, silent mode, DND, active music and audio focus; precise alarms and notifications must be allowed. Background service restrictions or missing TTS voices can suppress speech, leaving the normal completion notification. UI frame polling now stops when the timer activity is not visible; the monotonic clock and system alarm continue without it. Real-device lock-screen and speech tests are still needed.


## DailyStride 2.5 — First-open guide

Five onboarding pages explain the dashboard, habit schedules and conflict prevention, focus timers / lock-screen access, voice / permissions, and progress / backups. Next, Back, Skip and Start my stride controls; content scrolls on smaller displays. The current page survives rotation, and completion is saved locally. The guide appears once for fresh installs (and once when upgrading from versions without it). Settings → Open app guide replays it without changing habits. It does not request permissions automatically. Illustrations are explanatory examples, not fake user statistics.


## DailyStride 2.6 — Policy and support links

Settings → About & support contains Privacy Policy, Terms of Use, Help & Support, the product website and Email Nova Stack. Web links open with Android's external browser; email opens the user's mail composer without sending a message. The links use the published `https://dayly-stride-website.vercel.app/` origin with `/privacy/`, `/terms/` and `/support/` paths. Missing handlers or blocked intents show an error and the address. No WebView, network SDK or Android internet permission was added. Website source remains in the separate DaylyStride-website repository.


## DailyStride 2.7 — Automatic focus check-in

A running countdown reaching zero now saves its habit completion automatically, including from the background completion alarm or when the expired session is reopened. The stored session start date identifies the correct day for overnight sessions. Pause and reset do not award check-ins; a session restored paused after reboot requires review. Completion is idempotent, so another finish callback or another full session cannot toggle a checked habit off. Deleted or no-longer-scheduled habits are left untouched; save failures show a retry action. The dashboard observes stored check-ins so its tick, totals and streaks refresh immediately. These automatic completions implement user consent given when starting the timer, including while locked; manual retry still requires unlocking.


## DailyStride 2.8 — Navy / cyan presentation

Redesigned the app presentation around the supplied reference: navy backgrounds, cyan completion ring, emerald streak panel and checkboxes, selectable seven-day history bars, compact task cards and a round add button. Bottom navigation now has Today, Progress and Settings; All tasks remains available from Today and the header menu to manage future/rest-day habits. Each task has an accessible three-dot dropdown containing Edit and Delete, with the existing delete confirmation. Focus timers remain available on timed tasks. The progress screen, settings, timer screen and onboarding use the updated palette; the light theme is retained.

Habit storage, schedule conflict checks, automatic timer completion, notifications, voice, backups, package ID and legal URLs are unchanged. Summary values are computed from actual habits; the streak card shows the best current habit streak. On small screens or enlarged text the summary stacks vertically. Android XML and compilation are checked; device visual/touch validation remains necessary.


## DailyStride 2.9 — Task schedule details

Task cards retain the navy/cyan layout and overflow Edit/Delete menu. They now show labeled work start/end times, reminder time, next-day markers for overnight durations, future start dates, and compact wrapping badges for Focus duration, Voice reminder and repeat weekdays. Timers without a scheduled start are labeled Any time. Time labels use 12-hour AM/PM and the same reminder fallback used by conflict checking. No habit storage, scheduling, timer, notification or backup behavior changed.
