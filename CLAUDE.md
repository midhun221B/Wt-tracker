# Wt-tracker

Personal Android weight-loss tracker. It compares the PLANNED trajectory (88.3 kg on 2026-10-08 → 82.0 kg on 2027-01-07)
with a REALISTIC forecast built from logged weights. Single user, all data on the phone: no accounts, no cloud.

## Layout
- `core/`: pure Kotlin (JVM, no Android). All the math and parsing lives here and is unit-tested.
  - `trend/`: 7-day moving average, Theil–Sen fit over the last 21 days (or 42 days for weekly weigh-ins; none until the weigh-ins span 14 days), forecast with an 80 % band and an ETA.
  - `plan/`: planned line and re-baseline.
  - `energy/`, `alerts/`, `summary/`: energy balance, alert rules, weekly table (Monday–Sunday weeks; week 1 is the week of the first weigh-in).
  - `dashboard/`: `buildDashboard()`, the single entry point the UI calls.
  - `io/`: CSV export, JSON backup/restore, Strava `activities.csv` parser and import matching.
  - `Safety.kt`: hard limits.
- `app/`: Jetpack Compose + Room (schema v3) + WorkManager.
  - `data/`: entities, DAOs, migrations, first-run setup (`startPlan`), backup and Strava import.
  - `ui/`: screens (including first-run setup, `Onboarding.kt`) and `AppViewModel`.
  - `chart/`: Canvas charts.
  - `notify/`: weigh-in-day reminder.
- Tests:
  - `core/src/test`: synthetic noisy data.
  - `app/src/test`: format helpers, plus Robolectric screenshot tests that write PNGs to `app/build/screenshots`.

## Commands
- `./gradlew :core:test`: runs anywhere with a JDK. Always run it before pushing.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: needs an Android SDK. `:app` is only included in
  `settings.gradle.kts` when `ANDROID_HOME`/`ANDROID_SDK_ROOT` or `local.properties` exists.
- **Claude Code cloud sessions:** the Android SDK and Google Maven are blocked, so the app module can't be built or tested
  locally. Verify app changes through GitHub Actions (`.github/workflows/android.yml`) and read the job logs.

## CI and delivery
- CI runs core tests, app unit tests and the debug APK build on every push and PR, and uploads the APK, screenshots and
  test reports as artifacts.
- Every push also republishes the APK to the `debug-latest` pre-release, from a separate `publish` job that runs one at a time. The phone download link is
  `https://github.com/midhun221B/Wt-tracker/releases/download/debug-latest/wt-tracker-debug.apk`.
  From a cloud session, fetch that URL with curl: the artifact and log blob hosts are blocked, but release downloads work.
- An `emulator` job boots an Android 14 emulator (Pixel 6 profile) on every push. It runs `.github/emulator/smoke.py`:
  it updates from the latest release and checks the data survives, opens every tab and Settings, runs first-run setup
  on a fresh install, and fails on a crash.
  Its screenshots go to the `emulator-latest` pre-release (download them with curl, like `debug-latest`).
- Versioned releases: bump `versionCode` and `versionName` in `app/build.gradle.kts`. When that reaches `main`, the
  `publish` job creates a `v<versionName>` tag and release with `wt-tracker-v<versionName>.apk` (once per version).
- Debug builds are signed with the committed `app/debug.keystore` (standard debug credentials) so updates install over
  the old app without wiping its data. Don't replace or remove it.
- `docs/screenshots/` is a fixed snapshot used by the README. CI no longer commits screenshots; update them by hand
  when the UI changes.

## Rules for the domain logic
- Metric units, ISO dates, timezone `Asia/Tokyo` (`todayInTokyo()`). Strava dates arrive in UTC; convert them.
- **Safety:**
  - Never suggest intake below 1800 kcal/day or loss above 1 kg/week (`Safety`).
  - Re-baseline flags anything above 0.7 kg/week as unrealistic.
- Weigh-ins are weekly, on a weigh-in day the user picks (Settings). Don't design for daily weigh-ins.
- Show the "Estimates, not medical advice" note wherever advice appears.
- 7700 kcal per kg. Net running kcal = logged kcal − resting burn, or ≈ 0.9 × kg × km when no calories are logged.
- Keep the math in `core/` with tests. The UI only formats values from `Dashboard`.
- A Room schema change needs a version bump plus a `Migration`. Never use destructive fallback: real data lives on the phone.

## UI and design
- Redesign in progress. The current favourite is **direction "B orange"**:
  - dark athletic theme: background `#0E1113`, cards `#171C20`, text `#F2F4F5`, muted `#9AA4AC`
  - Strava-orange accent `#FC5200`; amber `#FFC857` for "behind plan"; blue-grey dashed `#7FA6C9` for the planned line
  - Barlow Condensed for big numbers, Barlow for everything else
  - a progress ring toward the goal
- **Write labels, headings, buttons and tabs in sentence case. No ALL-CAPS and no wide letter spacing** (owner's preference).
- Planned vs realistic must stay distinguishable by lightness and dash, not hue alone. Touch targets ≥ 44 dp.

## Workflow
- Read `HANDOVER.md` first: current status, open items and decisions. Update it at the end of every piece of work
  (what changed, what's merged vs pending, new decisions, open questions), in the same commit or PR as the work.
- Work in small steps and show results (test output, screenshots, APK) after each one.
- Develop on a feature branch and open a PR to `main`; merge with a merge commit. After a PR merges, start the next change
  from the latest `main`.
- Keep the code simple and readable, matching the existing style (small files, KDoc on non-obvious logic).
