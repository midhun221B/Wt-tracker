# Handover

Where the project stands, what was decided and what is open. Update this file at the end of every piece of work
(see CLAUDE.md). Last updated: 2026-10-08.

## Goal
Personal Android weight-loss tracker for one user (male, 32, 170 cm). It compares the planned trajectory
(88.3 kg on 2026-10-08 → 82.0 kg on 2027-01-07, about 0.5 kg/week) with a realistic, data-driven forecast.
- Running 4–5 days a week; food about 350 kcal/day below intake.
- All data stays on the phone.
- Metric units, ISO dates, Asia/Tokyo time.
- "Estimates, not medical advice"; suggestions never go below 1800 kcal/day of intake or above 1 kg/week of loss.

## Status

### Done and merged to `main`
| PR | What |
|---|---|
| [#1](https://github.com/midhun221B/Wt-tracker/pull/1) | Step 1: forecast engine (`core/`), tests, Room data layer, CI |
| [#2](https://github.com/midhun221B/Wt-tracker/pull/2) | Step 2 UI (Compose), reminder, CSV/JSON export and backup; step 3 Strava `activities.csv` import; `debug-latest` APK release; fixed debug signing key |
| [#3](https://github.com/midhun221B/Wt-tracker/pull/3) | CI no longer commits screenshots |
| [#4](https://github.com/midhun221B/Wt-tracker/pull/4) | "B orange" redesign (dark theme, Strava orange, Barlow fonts) of Dashboard, Today and navigation; CLAUDE.md |
| [#5](https://github.com/midhun221B/Wt-tracker/pull/5) | Runs and Body screens in "B orange" (28-day summary, weekly km bars, runs grouped by program week, "Fastest yet"; Body change tiles, trend charts, measurement cards); this HANDOVER.md |

### Not merged
- Nothing pending. Start the next change from the latest `main`.

### Features in the app today
- **Today:** weigh-in with ±0.1 steppers, hunger (None–Very), sleep, snacks, note, rest-day switch, add run.
- **Dashboard ("Trend"):**
  - Progress ring and planned vs realistic chart (7-day average, Theil–Sen fit over 21 days, 80 % band).
  - Forecast for the goal date and the goal-weight ETA, gap vs plan.
  - Energy balance with the intake change needed.
  - Weekly bars, body tiles, alerts: slow loss, fast loss, more than 5 run days in a row, no weigh-in for 3+ days.
- **Runs:** manual entry and Strava `activities.csv` import. Imports convert UTC to Tokyo time, skip duplicates, and match undated sample runs.
- **Body:** body-scale measurements (fat %, visceral, muscle, skeletal %, lean, BMR).
- **Plan:** edit checkpoints, re-baseline (flags > 0.7 kg/week, blocks > 1 kg/week), energy settings.
- **Settings:** daily reminder (default 07:30 Tokyo), CSV export, JSON backup and restore.

## Open items / next steps
1. **Strava screenshot import.** The owner wants to upload a Strava run screenshot and have the run filled in automatically.
   - Plan: on-device text recognition (ML Kit, bundled model, no cloud), plus a parser in `core/` tested on the OCR text, plus a confirm dialog before saving.
   - **Blocked:** we need 1–2 real Strava screenshots from the owner. None has been received; the only upload so far is a design-reference video.
2. **Not yet verified on a real device:**
   - status-bar icon colour and the dark launch window
   - Room v1→v2 migration on a real install
   - reminder notifications
   - the file pickers (export, restore, Strava CSV) and the date picker
   - only Strava's English date format has been tested
3. Nice-to-have ideas mentioned in design work (not requested yet): milestones/badges, a celebration screen after logging, a drag ruler for weight entry.

## Design decisions
- **Chosen direction: "B orange".**
  - Background `#0E1113`, cards `#171C20`, raised `#262D33`, text `#F2F4F5`, muted `#9AA4AC`.
  - Accent `#FC5200`; amber `#FFC857` for "behind plan"; planned line `#7FA6C9` dashed.
  - Barlow Condensed for big numbers, Barlow for the rest.
- **Owner preference:** sentence case everywhere, no all-caps.
- **Design canvases (private claude.ai artifacts, owner only):**
  - B orange, final, including the Runs/Body mockups: https://claude.ai/artifact/Jy2QCZXek1YKSDLc5pcgNP
  - Exploration A–D (calm minimal, dark athletic, soft health, BRIK-style): https://claude.ai/artifact/E3ovkCZGrnVEqyNqCKgxYR
  - Strava-inspired light version (not chosen): https://claude.ai/artifact/VmKg443fHGeVS4Bb9mCGdX

## How to work on it
- See CLAUDE.md for layout, commands, rules and CI.
- In a cloud session only `./gradlew :core:test` runs locally. App changes are verified by CI.
- To see the result:
  - Screenshots and the APK are attached to the `debug-latest` release on every push. Fetch them with curl from
    `https://github.com/midhun221B/Wt-tracker/releases/download/debug-latest/<name>`.
  - Screenshot names: `1-dashboard.png`, `2-today.png`, `3-runs.png`, `4-plan.png`, `5-body.png`, `wt-tracker-debug.apk`.
  - GitHub artifact and log downloads are blocked from the cloud sandbox; release downloads work.
- Workflow the owner uses: small steps, show results (screenshots, APK) after each one, then PR → merge with a merge commit.
  After a merge, start the next change from the latest `main`.
