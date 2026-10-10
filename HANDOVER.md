# Handover

Where the project stands, what was decided and what is open. Update this file at the end of every piece of work
(see CLAUDE.md). Last updated: 2026-10-09.

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
| [#6](https://github.com/midhun221B/Wt-tracker/pull/6) | Screenshot import: Strava run and body-scale screenshots read on the phone (ML Kit, offline), confirm dialog before saving |
| [#8](https://github.com/midhun221B/Wt-tracker/pull/8) | "Import from a … screenshot" button inside the Add run (Runs, Today) and Add measurement dialogs |
| [#9](https://github.com/midhun221B/Wt-tracker/pull/9) | Weekly weigh-in (weigh-in day setting, Room v3, forecast falls back to 42 days with 4+ weigh-ins over 3+ weeks, alert after 8 days); Mon–Sun weeks (runs before week 1 count as week 0, −1, …); collapsed older run weeks; `FormDialog` and dark fields for every pop-up; top-bar import removed (Strava CSV moved into Add run); Plan tab in "B orange" |
| [#10](https://github.com/midhun221B/Wt-tracker/pull/10) | Energy tiles show daily intake ("Eating now", "To get back on plan", rounded to 50 kcal) with deficits as sub-lines and the plan's assumed intake below |
| [#11](https://github.com/midhun221B/Wt-tracker/pull/11) | Today: "Change day" on the Next weigh-in card, one running card (run, rest day, week numbers), hunger no longer asked; Settings in the new style; `8-settings` screenshot |
| [#12](https://github.com/midhun221B/Wt-tracker/pull/12) | Body: weekly measurement card (scale screenshot first) and muscle chart; weigh-in is weight only; Trend Weeks/Body sections collapsible; Runs opens only this week |
| [#13](https://github.com/midhun221B/Wt-tracker/pull/13) | Weigh-in status by Mon–Sun week (`weighInStatus`: no prompt once the week has a weight, due until logged) for Today, Body and the reminder; one-line Body card unless due; collapsible Plan sections; screenshots at 411 dp (Nothing Phone (3a)) |
| [#14](https://github.com/midhun221B/Wt-tracker/pull/14) | Today: small card once the day/week is logged (big card only when due or on Edit); scale measurements count as the weekly weigh-in; Body counts a typed weight; sample runs removed (startup cleanup); reminder time picker |
| [#15](https://github.com/midhun221B/Wt-tracker/pull/15) | Today: "Run done" / rest-day done state, Mon–Sun week dots with "N runs · X km this week", 3 s "Run logged" banner after saving a run |
| [#16](https://github.com/midhun221B/Wt-tracker/pull/16) | Today polish: one-line Next weigh-in card (tap to change day), pace and "+ Add another" on one row, dash for missed days in the week dots, "Run logged" banner under the top bar for every new run (form or screenshot) |
| [#17](https://github.com/midhun221B/Wt-tracker/pull/17) | Today: "Edit" on the weigh-in card once the week is logged (opens the logged day); Runs: no "plan: 4–5 runs a week"; Trend: top bar title "Trend" with "Week N of M" in the small line |
| [#18](https://github.com/midhun221B/Wt-tracker/pull/18) | Weigh-in drag ruler (plan and last-week marks, haptics) instead of − / +; "Weigh-in saved" card with the goal ring filling (`weighInProgress`); check pulse after a new run; scale-only "Edit" opens the measurement; tighter run card; CI `publish` job serialized |
| [#19](https://github.com/midhun221B/Wt-tracker/pull/19) | One trend-based "kg lost" and "over plan" (`goalProgress`, `gapKg`; the live weigh-in line is labelled "Scale"); fresh-install empty states (blank days before start, "Trend in N more weigh-ins", no empty km chart, Body hint card, re-baseline waits for a trend); ruler notch for marks; banner word order; screenshots `9a`–`9e` |
| [#20](https://github.com/midhun221B/Wt-tracker/pull/20) | Version 0.2.0 (`versionCode` 2); CI creates a `v<versionName>` tag and release with `wt-tracker-v<versionName>.apk` when a new version reaches `main` |
| [#22](https://github.com/midhun221B/Wt-tracker/pull/22) | Settings › About shows the app version (`BuildConfig.VERSION_NAME`); version 0.2.1 (`versionCode` 3), released as `v0.2.1` |
| [#23](https://github.com/midhun221B/Wt-tracker/pull/23) | Plan tab redesign (design B2): goal sentence, checkpoint timeline with today (dot per date, orange line up to today), re-baseline / energy / history as list rows with pop-ups; `Dashboard.todayVsPlan()`; version 0.3.0 (`v0.3.0` release) |
| [#24](https://github.com/midhun221B/Wt-tracker/pull/24) | Plan pop-up screenshots (`4b`–`4e`); checkpoint editor shows one decimal (84.0); re-baseline shows "Keep goal date" first with the 0.5 kg/week option below (`FormDialog` `below` slot); version 0.3.1 (`v0.3.1` release) |
| [#25](https://github.com/midhun221B/Wt-tracker/pull/25) | One plan-gap wording ("over plan" / "under plan" / "on plan") on Trend, the Plan timeline and the weigh-in card; no "×" on the activity factor field; grey empty fastest pace; version 0.3.2 (`v0.3.2` release) |

### Not merged
Nothing pending.

### Features in the app today
- **Today:** one running card (today's run, add run, rest day, week numbers); while the week's weigh-in is due, the weigh-in card (drag ruler in 0.1 kg steps with plan and last-week marks, typing, or scale screenshot), weight only, then a "Weigh-in saved" card with the goal ring filling to the new weight; otherwise a one-line "Next weigh-in" card (tap to change the day; "Edit" opens the week's logged entry).
- **Dashboard ("Trend", program week in the top bar's small line):**
  - Progress ring and planned vs realistic chart (7-day average, Theil–Sen fit over 21 days or 42 days for weekly weigh-ins, 80 % band).
  - Forecast for the goal date and the goal-weight ETA, gap vs plan.
  - Energy: "Eating now ≈ 2,350" and "To get back on plan ≈ 2,100" kcal/day (rounded to 50), deficits as sub-lines, the plan's assumed intake below.
  - Weekly bars, body tiles, alerts: slow loss, fast loss, more than 5 run days in a row, no weigh-in for 8+ days.
- **Screenshot import:** buttons inside the "Add run" and "Add measurement" forms, and "Fill from a scale screenshot" on the Today weigh-in card.
  Reads a Strava share image or the body-scale app screen on the phone and opens a pre-filled confirm dialog.
- **Runs:** "Last 28 days" summary (runs, distance, fastest pace, weekly km bars); manual entry, screenshot, and Strava `activities.csv` import (all from Add run), grouped by Mon–Sun week. Imports convert UTC to Tokyo time, skip duplicates, and match undated sample runs.
- **Body:** body-scale measurements (fat %, visceral, muscle, skeletal %, lean, BMR).
- **Plan:** "82 kg by 7 January. 9 weeks to go."; checkpoint timeline with today (orange line up to today; tap to edit checkpoints); list rows for re-baseline (flags > 0.7 kg/week, blocks > 1 kg/week), energy estimate and history, each opening a pop-up.
- **Settings:** weigh-in day and reminder (default Monday 07:30 Tokyo), CSV export, JSON backup and restore, app version under About.

## Open items / next steps
1. **Screenshot import: merged via #6, confirmed working on the owner's phone with both screenshots.**
   - The owner sent two real screenshots on 2026-10-08: a Strava share image (3.45 km, 7:02 /km, 24m 19s, 377 Cal,
     no date) and a Japanese body-scale app screen (測定データ, 2026/10/08: BMI 30.6, 体脂肪率 29.2 %, 内臓脂肪 16.0,
     筋肉量 60.1 kg, 骨格筋率 37.0 %, 除脂肪体重 62.5 kg, 基礎代謝量 1818 kcal). The tests in `core/.../ScreenshotTest.kt`
     use their text.
   - How it works:
     - ML Kit Japanese text recognition (bundled model, offline; it also reads Latin text) gives lines with boxes.
     - `ocrRows()` joins them into rows, and `readScreenshot()` in `core/io/Screenshot.kt` decides "run" or "body".
     - The user confirms the values in the run or body dialog before saving.
   - Decisions:
     - Strava share images have no date, so the run defaults to today and the dialog asks the user to check it.
     - If the route line hides the time or distance, it's worked out from pace, with a note. A distance/time/pace mismatch shows a warning.
     - Saving a screenshot run replaces a run on the same day with about the same distance (±0.05 km), so the same image twice doesn't duplicate.
     - The body screen has no weight row. Weight = lean / (1 − fat %) (88.3 kg for the sample, about ±0.1 kg), with BMI × height² as the fallback.
       It's shown as an optional field with a note. When saved, it updates that day's weigh-in and keeps the sleep, hunger and notes.
   - Not tested yet: Strava in Japanese (labels 距離/ペース/時間 are handled, but the
     units are guessed), and other scale apps. The confirm dialogs filled from a screenshot aren't in the Robolectric screenshots; the add forms are.
2. **Not yet verified on a real device** (owner's phone: Nothing Phone (3a)):
   - status-bar icon colour and the dark launch window
   - Room v1→v2→v3 migrations on a real install
   - reminder notifications
   - the file pickers (export, restore, Strava CSV) and the date picker
   - only Strava's English date format has been tested
3. Extras mockups (2026-10-09, https://claude.ai/artifact/E4vLaBMyhRWExCXBDSbfSw): the owner chose 2B (goal ring after a weigh-in), 2C (check pulse) and 3B (ruler with marks); built on the branch. The ruler's drag feel and haptics still need checking on the phone.

## Product decisions
- **No food logging (2026-10-09).** The owner doesn't track calories, and rough logs would add noise. Intake is inferred
  from the weight trend plus BMR × activity, shown rounded to 50 kcal and labelled as an estimate.
- **Kg lost toward the goal always uses the trend** (2026-10-09): the Trend ring and the "Weigh-in saved" ring share `goalProgress` (first weigh-in → today's trend, or the latest weight before a trend exists). The raw scale weight shows only as the change since the last weigh-in.
- **Over/under plan uses the trend** (2026-10-09), like kg lost; the live weigh-in line is the only scale-based gap and is labelled "Scale".
- **One wording for the plan gap** (2026-10-10): "over plan" / "under plan" / "on plan" everywhere (Trend, Plan timeline, weigh-in card), never "behind", "ahead" or "above plan".
- **No weekly review card** (2026-10-09): built for v0.3 (trend, runs, plan gap and one suggestion on the weigh-in day), but the owner didn't like it; removed before merging.
- **No milestones or badges** (2026-10-09): the owner skipped them after seeing the mockups.
- **No sample runs** (2026-10-09): the owner's real runs come from Strava screenshots/CSV.
- **Weekly weigh-ins** on a chosen day, weight only. Hunger, sleep, snacks and notes are no longer asked (2026-10-09); old values stay in the database and backups.

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
  - Screenshot names: `1-dashboard.png`, `2-today.png`, `2b-weigh-in.png` (ruler), `2g-weigh-in-saved.png`, `3-runs.png`, `4-plan.png` (pop-ups `4b`–`4e`), `5-body.png`, `wt-tracker-debug.apk`.
  - GitHub artifact and log downloads are blocked from the cloud sandbox; release downloads work.
  - Publishing runs in a separate `publish` job, one at a time; the release notes name the version, branch and commit.
  - Versioned releases: bump `versionCode` and `versionName`; once merged to `main`, CI tags `v<versionName>` and publishes
    `https://github.com/midhun221B/Wt-tracker/releases/download/v<versionName>/wt-tracker-v<versionName>.apk`.
- Workflow the owner uses: small steps, show results (screenshots, APK) after each one, then PR → merge with a merge commit.
  After a merge, start the next change from the latest `main`.
