# Wt-tracker

A personal Android app for weight loss. It compares the **planned** trajectory with a **realistic**, data-driven forecast.
Everything stays on the phone: no accounts, no cloud.

## Modules
- `core/`: pure Kotlin forecast engine with no Android dependencies, unit-tested on synthetic noisy data.
  - `plan/`: piecewise-linear planned line and re-baselining.
  - `trend/`: 7-day moving average, Theil–Sen robust fit over the last 21 days, forecast with an 80 % band, ETA, gap vs plan.
  - `energy/`: deficit implied by the trend (7700 kcal/kg) vs expected (food + runs), and the intake change needed.
  - `alerts/`: slow loss, fast loss, rest needed, log reminder.
  - `Safety.kt`: intake never suggested below 1800 kcal/day, loss never above 1 kg/week.
- `app/`: Android app (Jetpack Compose, Room/SQLite).

## Build
- Core tests (JDK only): `./gradlew :core:test`
- APK (needs an Android SDK): `./gradlew :app:assembleDebug`. GitHub Actions builds it on every push;
  download `wt-tracker-debug-apk` from the run's artifacts and sideload it.

Estimates, not medical advice.
