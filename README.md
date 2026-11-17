# Day by Day — native Android habit tracker

Kotlin and Jetpack Compose, Android 8.0+ (API 26). Daily habits, selectable emoji, daily check-ins with undo, seven-day history, current streaks, weekly completion totals, editing, and confirmed deletion. Three starter habits can be edited or removed. All data stays in app-private SharedPreferences; no account or internet permission. Uninstalling clears history. Android backup is disabled.

## Open and run

1. Install Android Studio, then open this folder as a project.
2. Use JDK 17 or 21, Android SDK 35 and Gradle 9.3.1 (AGP 9.1.1).
3. This source distribution does not include a Gradle wrapper. With Gradle 9.3.1 installed, run `gradle wrapper --gradle-version 9.3.1` in this folder. Android Studio can use a locally installed Gradle distribution in its Gradle settings.
4. Sync, select an Android emulator or connected phone, and run `app`.
5. Run `./gradlew testDebugUnitTest assembleDebug`. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Behavior

Habits repeat every day starting on their creation date. Tap a date in the last seven days to check or correct completions. Streaks include consecutive completed days ending today, or yesterday while today is still pending. Weekly totals only count days on or after each habit's creation. Progress shows seven dots per habit, oldest to newest, with dashes before creation. Dates follow the device's local timezone and refresh when the app resumes or the date changes.

## Verification status

Source and configuration have been reviewed. Unit tests cover streak gaps, a pending current day, and reversible check-ins. They have not been executed: this environment has Java but no Android SDK, Gradle, or Kotlin compiler, and downloads failed because its configured proxy was unreachable. No APK has been built or device UI test performed.

## Scope

This first version is offline and daily-only. Reminders, cloud sync, custom schedules, data export, widgets, and Play Store signing are not implemented.


## Automatic APK downloads with GitHub Actions

Upload the contents of this folder to the root of your GitHub repository, including `.github/workflows/build-apk.yml` and `gradle/libs.versions.toml`. Files such as `.github` and `.gitignore` may be hidden in your file manager: enable showing hidden files. Do not upload the ZIP as a single repository file. Commit to `main`, or run **Actions → Build and publish debug APK → Run workflow**.

The workflow uses Temurin Java 17, exactly Gradle 9.3.1, and `gradle :app:assembleDebug --stacktrace --no-daemon`. It uploads `app/build/outputs/apk/debug/app-debug.apk` as the Actions artifact **app-debug-apk**, then creates a published latest GitHub Release with a directly downloadable APK. Tags use `debug-apk-build-<run-number>-<run-attempt>`; asset filenames use `<sanitized-repository-name>-debug-build-<run-number>.apk`. Release errors produce warnings and leave the successful Actions artifact available. Only the built-in GitHub token is used.

AGP 9 uses built-in Kotlin support; the old Kotlin Android plugin and legacy `kotlinOptions` configuration were removed. The version catalog pins AGP 9.1.1 and Compose compiler plugin 2.2.10. The app source, features, application ID and Android resources were not changed for this migration.

A fresh temporary debug signing key is generated on each runner and copied to AGP's default debug-key location. The keys are not cached or uploaded and are removed by a final cleanup step. APKs from different runs may have different signing keys, so installing an update may require uninstalling the previous app, which clears its local habit data.

This migration and workflow have been checked statically, but no build or GitHub workflow run has been performed here. No APK or release exists yet. The first GitHub Actions run must confirm build compatibility. The updated ZIP excludes Git history and contains uncommitted working-tree changes for you to upload manually.
