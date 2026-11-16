# Design: "Try a scenario" on the Plan page

## Context and intent
The owner wants to play "what if" on the Plan page: change food, runs or the goal and see where they'd end up,
**without changing the real plan**. It's a sandbox only. Nothing is saved, and Re-baseline and Checkpoints stay the
way to change the real plan.

- **Goals:**
  - Answer "where do I land, and when?" for any mix of the levers.
  - Answer the reverse question: "what would it take to reach goal X by date Y?"
- **Non-goals:**
  - No saved or named scenarios, no "make it my plan" button.
  - No new Room table and no backup or format changes.
- **Anti-goals:**
  - Never suggest food under 1800 kcal/day or a loss rate over 1 kg/week.
  - The scenario line must never be mistaken for the real plan or forecast.

## Approaches (owner picked A)
- **A. Straight energy balance (picked).**
  - Daily deficit = maintenance + run burn − food, and kg/week = deficit × 7 / 7700.
  - It's a straight line from today's trend weight.
  - It matches `EnergyBalance.kt` and works from day one.
  - It's slightly optimistic over long spans.
- **B. A plus a slowing burn.** More realistic over 10+ weeks, but it changes the result by only ~0.1–0.2 kg over the
  remaining ~7 weeks, for extra maths.
- **C. Nudge the existing forecast.** Cheapest, but it needs 2 weeks of weigh-ins, and goal mode is awkward.

## What the owner sees
A **"Try a scenario"** row in the Plan page list, under "Energy estimate", opens a full-screen sheet:

```
 Try a scenario                        Reset   ✕
 ┌──────────────────────────────────────────┐
 │  chart: planned (blue-grey dashed),      │
 │  my forecast (orange), scenario (dotted) │
 └──────────────────────────────────────────┘
 82.0 kg by 10 Jan — 3 days after your goal.
 About 0.62 kg a week.

 [ Change my habits | Change my goal ]
   Food per day      − 2,100 kcal +
   Runs per week     −    4       +
   Km per run        −   5.0      +
 Estimates, not medical advice.
```

- **"Change my habits"**
  - The levers are food (steps of 50 kcal, minimum 1800), runs per week (0–7) and km per run (steps of 0.5, 1–21).
  - The sheet shows the arrival date at the goal weight, the weight on the goal date, the kg per week, and how many
    days early or late that is.
  - If the rate goes over 1 kg/week, it shows an amber "Faster than 1 kg a week. Not suggested." and gives no date.
  - If the levers mean no loss, it says "At this rate you won't reach X kg."
- **"Change my goal"**
  - The levers are goal kg and goal date, and the runs stay as set in the habits tab.
  - The sheet shows the food per day needed.
  - If that would be under 1800, it shows the extra km a week needed at 1800 kcal instead.
  - If the rate needed is over 1 kg/week, it says so and suggests a later date. That date is the earliest one at
    0.7 kg/week, the re-baseline "realistic" limit.
- **Starting values are the real numbers**, so the untouched scenario is the realistic forecast:
  - food = `energy.estimatedIntake`, or, before 2 weeks of weigh-ins, maintenance − planned food deficit;
  - runs per week and km per run = averages over the last 4 weeks, or 0 runs if there are none;
  - goal = the plan's goal checkpoint.
- **Telling the lines apart:** the scenario line is a dotted, lighter orange line, so it differs from the solid forecast
  and the dashed blue-grey plan by lightness and dash, not hue alone. Touch targets are ≥ 44 dp, and all text is in
  sentence case.

## How it's built
- **Core, pure and tested:** `core/src/main/kotlin/wt/core/plan/Scenario.kt`.
  - `simulate(start: kg+date, maintenanceKcal, foodKcal, runsPerWeek, kmPerRun, goalKg, goalDate)` returns:
    the deficit, kg/week, `tooFast` (> `Safety.MAX_LOSS_KG_PER_WEEK`), the arrival date (or null), the kg on the goal
    date, days early or late, and weekly points for the chart.
  - Run burn uses the existing 0.9 × kg × km rule (`runNetKcal` logic in `EnergyBalance.kt`).
  - `solveForGoal(...)` returns the food needed (floored by `Safety.floorIntake`), the extra km a week when floored,
    `tooFast`, and the suggested date at 0.7 kg/week.
  - `scenarioStart(dashboard, runs)` returns the starting values described above.
  - Uses the `Safety` constants throughout and adds no new magic numbers.
- **Tests:** `core/src/test/.../plan/ScenarioTest.kt`.
  - Cases: on-plan, early, late, no loss, too fast, the 1800 floor with extra km, goal already reached, and
    start-from-forecast (the untouched scenario matches `energy`).
- **UI:**
  - New `app/src/main/java/wt/app/ui/ScenarioSheet.kt` (state held with `remember`, nothing persisted).
  - In `PlanScreen.kt`, the new `ListRow` and a `PlanSheet.Scenario` case.
  - The chart reuses the existing Canvas trend chart drawing in `chart/`, with an extra dotted series.
  - The UI only formats values from core.
- **Screenshot tests:** `9-plan-scenario` (habits) and `9b-plan-scenario-goal`, in the existing Robolectric screenshot
  test.
- **Housekeeping:**
  - Version bump to 0.9.0 (versionCode 18) with `fastlane/.../changelogs/18.txt`.
  - Update HANDOVER.
  - No Room change.

## Left to the builder
- Exact copy wording, and lever step sizes within the ranges above.
- How the sheet animates.

## Verification
- `./gradlew :core:test`, with ScenarioTest red then green, and the full suite green.
- Push, wait for CI (app unit tests, APK, emulator smoke), then check the new screenshots and the debug-latest APK.
- On the phone: open Plan, then "Try a scenario". The untouched scenario matches today's forecast. Move each lever,
  close the sheet, and confirm the plan is unchanged.

## After approval
Save this as `docs/superpowers/specs/2026-11-16-plan-scenario-design.md`, then write the implementation plan
(writing-plans) and build it on `claude/fervent-ptolemy-ho7r2a`.
