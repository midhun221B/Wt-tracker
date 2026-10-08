# Wt-tracker

A personal Android app for weight loss. It compares the **planned** trajectory with a **realistic**, data-driven forecast
that updates every time you log a weight. Everything stays on the phone: no accounts, no cloud.

| Dashboard | Today | Runs | Plan |
|---|---|---|---|
| ![Dashboard](docs/screenshots/1-dashboard.jpg) | ![Today](docs/screenshots/2-today.jpg) | ![Runs](docs/screenshots/3-runs.jpg) | ![Plan](docs/screenshots/4-plan.jpg) |

Screenshots are rendered from sample data in CI (Robolectric) and refreshed automatically.

## Features
- **Today:** quick weight entry with ±0.1 steppers; optional sleep, hunger, snacks and note; rest-day switch; add a run.
- **Dashboard:**
  - Planned vs realistic chart with an 80 % band.
  - Trend weight and rate, predicted weight on the goal date, ETA for the goal weight, gap vs plan in kg and days.
  - Energy balance: actual vs expected deficit and the intake change needed.
  - Weekly table, body-fat and visceral-fat trends, alerts.
- **Runs:** manual entry and **Strava `activities.csv` import**.
  - Runs, trail runs and virtual runs are imported.
  - Re-importing the same file adds nothing new.
  - Runs you already logged are matched instead of duplicated (same day, about the same distance, or the undated sample runs).
- **Body:** body-scale measurements (fat %, visceral, muscle, skeletal %, lean mass, BMR).
- **Plan:**
  - Edit checkpoints.
  - **Re-baseline** from today's trend weight to the same goal date. The weekly loss it would need is flagged as unrealistic above 0.7 kg/week and blocked above 1 kg/week.
  - Energy settings (BMR, activity factor, food deficit).
- **Settings:** daily weigh-in reminder (Asia/Tokyo time), CSV export, JSON backup/restore.

## How the forecast works (`core/`)
- **Smoothing:** 7-day trailing moving average.
- **Trend:** a Theil–Sen robust line over the last 21 days, which ignores water-weight spikes. With fewer than 7 weigh-ins it uses all the data and is marked low-confidence.
- **Band:** an 80 % band from the line's standard error, using a MAD-based noise estimate. It widens with the forecast horizon.
- **Energy:**
  - Actual deficit = −slope × 7700 kcal/kg.
  - Expected deficit = planned food deficit + net running kcal.
  - Net running kcal = logged kcal minus the resting burn, or about 0.9 kcal/kg/km when no calories were logged.
- **Safety:** suggestions never go below 1800 kcal/day of intake or above 1 kg/week of loss.

Estimates, not medical advice.

## Modules
- `core/`: pure Kotlin. Holds the forecast, energy balance, alerts, re-baseline, weekly summary, CSV/JSON and Strava parsing.
  Unit-tested on synthetic noisy data.
- `app/`: Android app (Jetpack Compose, Room/SQLite, WorkManager).

## Build
- Core tests (JDK only): `./gradlew :core:test`
- APK (needs an Android SDK): `./gradlew :app:assembleDebug`. GitHub Actions builds it on every push;
  download `wt-tracker-debug-apk` from the run's artifacts and sideload it.
