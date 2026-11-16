# Plan scenario simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A look-only "Try a scenario" sheet on the Plan page. You change food, runs, km per run or the goal and see where
you'd land, without touching the real plan.

**Architecture:** All the maths lives in a new pure-Kotlin file, `core/.../plan/Scenario.kt`, with JVM unit tests. It
uses a straight energy balance (approach A). The app adds `ScenarioSheet.kt`, a full-screen dialog with steppers and a
chart, plus one row on the Plan page. `WeightChart` gets an optional dotted scenario series. Nothing is persisted and
there is no Room change.

**Tech Stack:** Kotlin, JUnit (`kotlin.test`), Jetpack Compose, Robolectric screenshot tests, GitHub Actions CI.

**Spec:** `docs/superpowers/specs/2026-11-16-plan-scenario-design.md`

## Global Constraints
- Never suggest intake below `Safety.MIN_INTAKE_KCAL` (1800) or a loss above `Safety.MAX_LOSS_KG_PER_WEEK` (1.0 kg/week).
- Suggested later date uses `Safety.UNREALISTIC_KG_PER_WEEK` (0.7 kg/week).
- 7700 kcal per kg (`Safety.KCAL_PER_KG`). Run burn = 0.9 × kg × km.
- Show `Safety.DISCLAIMER` ("Estimates, not medical advice.") on the sheet.
- Sentence case everywhere, no ALL-CAPS. Touch targets ≥ 44 dp.
- The scenario line must differ from the planned (dashed blue-grey) and realistic (solid orange) lines by lightness and
  dash: a dotted, lighter orange line.
- Nothing is saved: the sheet holds its state with `remember`, and closing it discards everything.
- Lever ranges:
  - food in 50 kcal steps, minimum 1800;
  - runs per week 0–7;
  - km per run in 0.5 steps, 1–21.
- Maths stays in `core/` with tests; the UI only formats values.

## Review Focus
1. **Goal already reached** (start kg ≤ goal kg): it should show "Goal reached", with no division by zero and no
   negative food. *Task 1 test `alreadyAtGoal`, Task 2 test `goalAlreadyReached`.*
2. **Goal date today or passed** in goal mode: it should show "Pick a later date" and no food figure. *Task 2 test
   `goalDatePassed`.*
3. **No weigh-ins at all** (fresh install): the sheet should open from the plan start weight without crashing. *Task 3
   test `freshInstallStartsFromPlan`.*
4. **Gaining weight under the levers** (deficit ≤ 0): there should be no arrival date and the copy should say so.
   *Task 1 test `noLossMeansNoArrival`.*
5. **Very slow loss** (arrival more than 3 years away): the arrival should be null, matching the forecast's
   `MAX_ETA_DAYS` cap. *Task 1 test `farArrivalIsCapped`.*

---

### Task 1: Core `simulate`

**Files:**
- Create: `core/src/main/kotlin/wt/core/plan/Scenario.kt`
- Test: `core/src/test/kotlin/wt/core/ScenarioTest.kt`

**Interfaces:**
- Produces:
  - `data class ScenarioInput(val startDate: LocalDate, val startKg: Double, val maintenanceKcal: Double, val foodKcal: Double, val runsPerWeek: Int, val kmPerRun: Double, val goalKg: Double, val goalDate: LocalDate)`
  - `data class ScenarioResult(val deficitKcal: Double, val kgPerWeek: Double, val tooFast: Boolean, val arrival: LocalDate?, val kgAtGoalDate: Double, val daysLate: Long?, val points: List<DatedValue>)`
    - `kgPerWeek` is positive when losing.
    - `daysLate` is negative when early, and null when there's no arrival.
    - `points` is two points: start, and `max(goalDate, startDate)`.
  - `fun runBurnPerDay(runsPerWeek: Int, kmPerRun: Double, kg: Double): Double`, which is `runsPerWeek × kmPerRun × 0.9 × kg / 7`.
  - `fun simulate(input: ScenarioInput): ScenarioResult`

- [ ] **Step 1: Write the failing tests**

```kotlin
class ScenarioTest {
    private val start = LocalDate.of(2026, 11, 16)
    private val goalDate = LocalDate.of(2027, 1, 7)               // 52 days later
    private fun input(food: Double, runs: Int = 0, km: Double = 3.0, kg: Double = 87.0, goalKg: Double = 82.0) =
        ScenarioInput(start, kg, maintenanceKcal = 2600.0, foodKcal = food, runsPerWeek = runs, kmPerRun = km, goalKg = goalKg, goalDate = goalDate)

    @Test fun runBurnUsesPointNineKcalPerKgKm() = assertEquals(4 * 5.0 * 0.9 * 87.0 / 7, runBurnPerDay(4, 5.0, 87.0), 1e-9)

    @Test fun deficitGivesRateAndArrival() {
        val r = simulate(input(food = 2050.0))                         // 550 kcal/day → 0.5 kg/week
        assertEquals(550.0, r.deficitKcal, 1e-9)
        assertEquals(0.5, r.kgPerWeek, 1e-9)
        assertEquals(start.plusDays(70), r.arrival)                  // 5 kg / (0.5/7) = 70 days
        assertEquals(18L, r.daysLate)
        assertEquals(87.0 - 0.5 / 7 * 52, r.kgAtGoalDate, 1e-9)
        assertFalse(r.tooFast)
    }

    @Test fun runsAddToTheDeficit() {
        val r = simulate(input(food = 2050.0, runs = 4, km = 5.0))
        assertEquals(550.0 + runBurnPerDay(4, 5.0, 87.0), r.deficitKcal, 1e-9)
    }

    @Test fun earlyArrivalIsNegativeDaysLate() = assertTrue(simulate(input(food = 1900.0, runs = 4, km = 5.0)).daysLate!! < 0)  // ≈ 0.84 kg/week

    @Test fun overOneKgAWeekIsTooFast() = assertTrue(simulate(input(food = 1800.0, runs = 7, km = 15.0)).tooFast)

    @Test fun noLossMeansNoArrival() {
        val r = simulate(input(food = 2700.0))
        assertNull(r.arrival); assertNull(r.daysLate); assertTrue(r.kgPerWeek < 0)
    }

    @Test fun farArrivalIsCapped() = assertNull(simulate(input(food = 2595.0)).arrival)  // 5 kcal/day

    @Test fun alreadyAtGoal() {
        val r = simulate(input(food = 2050.0, kg = 81.5))
        assertEquals(start, r.arrival)
    }

    @Test fun pointsRunFromStartToGoalDate() {
        val r = simulate(input(food = 2050.0))
        assertEquals(listOf(start, goalDate), r.points.map { it.date })
        assertEquals(r.kgAtGoalDate, r.points.last().value, 1e-9)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:test --tests wt.core.ScenarioTest`
Expected: compilation FAIL (unresolved `ScenarioInput`, `simulate`, `runBurnPerDay`).

- [ ] **Step 3: Implement `Scenario.kt`**

The deficit is maintenance + `runBurnPerDay(runs, km, startKg)` − food, and `kgPerWeek` is deficit × 7 / `KCAL_PER_KG`.
`tooFast` means `kgPerWeek > MAX_LOSS_KG_PER_WEEK`. For the arrival:
- if `startKg <= goalKg`, the arrival is `startDate`;
- else if `kgPerWeek <= 0`, there's no arrival (null);
- else the arrival is `startDate` + ceil((startKg − goalKg) / (kgPerWeek / 7)) days, or null if that's more than
  `MAX_ETA_DAYS` (import from `wt.core.trend`).

Add KDoc noting that this is a straight energy balance from today's trend weight.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test --tests wt.core.ScenarioTest`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit** with message `Scenario: simulate a what-if weight line in core`

### Task 2: Core `solveForGoal`

**Files:**
- Modify: `core/src/main/kotlin/wt/core/plan/Scenario.kt`
- Test: `core/src/test/kotlin/wt/core/ScenarioTest.kt`

**Interfaces:**
- Consumes: `ScenarioInput` and `runBurnPerDay` from Task 1. `foodKcal` is ignored here.
- Produces:
  - `data class GoalSolution(val kgPerWeek: Double?, val foodKcal: Double?, val floored: Boolean, val extraKmPerWeek: Double, val tooFast: Boolean, val suggestedDate: LocalDate?, val reached: Boolean)`
  - `fun solveForGoal(input: ScenarioInput): GoalSolution`

- [ ] **Step 1: Write the failing tests** (add them to `ScenarioTest`)

```kotlin
@Test fun foodNeededForTheGoal() {
    val s = solveForGoal(input(food = 0.0, kg = 87.0, goalKg = 83.0))         // 4 kg in 52 days
    val rate = 4.0 / 52 * 7
    assertEquals(rate, s.kgPerWeek!!, 1e-9)
    assertEquals(2600.0 - rate / 7 * 7700, s.foodKcal!!, 1e-6)
    assertFalse(s.floored); assertFalse(s.tooFast); assertNull(s.suggestedDate)
}

@Test fun foodIsFlooredAndExtraKmCoversTheRest() {
    val i = input(food = 0.0, kg = 87.0, goalKg = 82.0).copy(maintenanceKcal = 2300.0) // raw need ≈ 1559 kcal
    val s = solveForGoal(i)
    assertEquals(1800.0, s.foodKcal!!, 1e-9); assertTrue(s.floored)
    val raw = 2300.0 - (5.0 / 52 * 7) / 7 * 7700
    assertEquals((1800.0 - raw) * 7 / (0.9 * 87.0), s.extraKmPerWeek, 1e-6)
}

@Test fun tooFastSuggestsADateAtPointSevenKgAWeek() {
    val s = solveForGoal(input(food = 0.0, kg = 90.0, goalKg = 82.0))          // 8 kg in 52 days ≈ 1.08 kg/week
    assertTrue(s.tooFast)
    assertEquals(start.plusDays(ceil(8.0 / (0.7 / 7)).toLong()), s.suggestedDate)
}

@Test fun goalAlreadyReached() {
    val s = solveForGoal(input(food = 0.0, kg = 81.0))
    assertTrue(s.reached); assertNull(s.foodKcal)
}

@Test fun goalDatePassed() {
    val s = solveForGoal(input(food = 0.0).copy(goalDate = start))
    assertNull(s.kgPerWeek); assertNull(s.foodKcal); assertFalse(s.reached)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:test --tests wt.core.ScenarioTest`
Expected: compilation FAIL (unresolved `solveForGoal`).

- [ ] **Step 3: Implement `solveForGoal`**
- `reached` when `startKg <= goalKg`; then all other fields are null, false or 0.
- Days left = days from start to goal date. If ≤ 0, `kgPerWeek` and `foodKcal` are null.
- Otherwise:
  - rate = (startKg − goalKg) / daysLeft × 7, and `tooFast` = rate > `MAX_LOSS_KG_PER_WEEK`;
  - raw food = maintenance + run burn − rate / 7 × 7700, then `Safety.floorIntake`;
  - `extraKmPerWeek` = (1800 − raw) × 7 / (0.9 × startKg) when floored, else 0;
  - `suggestedDate` (only when too fast) = start + ceil((startKg − goalKg) / (`UNREALISTIC_KG_PER_WEEK` / 7)) days.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test --tests wt.core.ScenarioTest`
Expected: PASS (14 tests).

- [ ] **Step 5: Commit** with message `Scenario: solve for the food a goal needs`

### Task 3: Core `scenarioStart` (the levers begin at the real numbers)

**Files:**
- Modify: `core/src/main/kotlin/wt/core/plan/Scenario.kt`
- Test: `core/src/test/kotlin/wt/core/ScenarioTest.kt`

**Interfaces:**
- Consumes: `Dashboard` (`asOf`, `fit`, `latestWeight`, `plan`), `Run`, and `Profile` (`bmrKcal`, `activityFactor`,
  `plannedFoodDeficitKcal`).
- Produces: `fun scenarioStart(d: Dashboard, runs: List<Run>, profile: Profile): ScenarioInput`

**The rules:**
- `startDate = d.asOf`.
- `startKg` = the trend value at `asOf`, else the latest weigh-in, else `plan.start.kg`.
- Look at runs dated in `asOf − 27 … asOf`:
  - `runsPerWeek` = round(count / 4).coerceIn(0, 7);
  - `kmPerRun` = the mean km rounded to 0.5 and coerced to 1–21, or 3.0 when there are no runs.
- `maintenance` = BMR × activity factor.
- `food`:
  - with a fit: maintenance + `runBurnPerDay`(levers, startKg) + `fit.slopePerDay` × 7700. This calibrates the start
    so the untouched scenario reproduces the trend slope exactly.
  - without a fit: maintenance − planned food deficit.
- The goal is `plan.goal`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun untouchedScenarioMatchesTheTrend() {
    val weights = Synthetic.series(days = 21, kgPerWeek = 0.5, noiseSd = 0.0)
    val asOf = weights.last().date
    val runs = (0 until 12).map { Run(asOf.minusDays(it * 2L), 5.0, 30 * 60) }
    val d = buildDashboard(weights, runs, emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, asOf)
    val s = scenarioStart(d, runs, Defaults.profile)
    assertEquals(3, s.runsPerWeek); assertEquals(5.0, s.kmPerRun, 1e-9)
    assertEquals(-d.fit!!.kgPerWeek, simulate(s).kgPerWeek, 1e-9)
    assertEquals(d.plan.goal.kg, s.goalKg, 1e-9); assertEquals(d.plan.goal.date, s.goalDate)
}

@Test fun freshInstallStartsFromPlan() {
    val d = buildDashboard(emptyList(), emptyList(), emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, Defaults.START)
    val s = scenarioStart(d, emptyList(), Defaults.profile)
    assertEquals(d.plan.start.kg, s.startKg, 1e-9)
    assertEquals(0, s.runsPerWeek)
    val p = Defaults.profile
    assertEquals(p.bmrKcal * p.activityFactor - p.plannedFoodDeficitKcal, s.foodKcal, 1e-9)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:test --tests wt.core.ScenarioTest`
Expected: compilation FAIL (unresolved `scenarioStart`).

- [ ] **Step 3: Implement `scenarioStart`** using the rules above.

- [ ] **Step 4: Run the full core suite**

Run: `./gradlew :core:test`
Expected: PASS, 171 existing tests + 16 new.

- [ ] **Step 5: Commit** with message `Scenario: start the levers from the real numbers`

### Task 4: Scenario sheet, chart series and Plan row

**Files:**
- Modify:
  - `app/src/main/java/wt/app/chart/WeightChart.kt`: add the parameter `scenario: List<DatedValue> = emptyList()`.
    When it's non-empty, draw it after the realistic line as a polyline with `colors.realistic.copy(alpha = 0.55f)`,
    width 3 dp, round cap, and dots via `PathEffect.dashPathEffect(floatArrayOf(2f, 10f))`. Also include its values in
    the y-range (`core` list), clamped like the projection (plan ± 3 kg).
- Create: `app/src/main/java/wt/app/ui/ScenarioSheet.kt`
- Modify: `app/src/main/java/wt/app/ui/PlanScreen.kt`: add `PlanSheet.Scenario`, and a `ListRow("Try a scenario", "what if")`
  after "Energy estimate" that opens `ScenarioSheet(state, onDismiss = close)`.
- Modify: `app/src/main/java/wt/app/ui/Format.kt`: add the copy helpers below.
- Test:
  - `app/src/test/java/wt/app/FormatTest.kt`;
  - `app/src/test/java/wt/app/ScreenshotTest.kt`: add `4b-plan-scenario` and `4c-plan-scenario-goal`.

**Interfaces:**
- Consumes: `scenarioStart`, `simulate`, `solveForGoal`, `ScenarioResult`, `GoalSolution` from Tasks 1–3, and
  `state.runs.map { it.toModel() }`, `state.profile.toModel()`.
- Produces:
  - `@Composable fun ScenarioSheet(state: UiState, onDismiss: () -> Unit, initialGoalMode: Boolean = false)`
  - `internal fun scenarioHeadline(r: ScenarioResult, goalKg: Double): String`
  - `internal fun goalHeadline(s: GoalSolution, goalKg: Double, goalDate: LocalDate): String`

**The sheet:**
- It's a full-screen `Dialog(usePlatformDefaultWidth = false)` on `Palette.Background`.
- **Top bar:** "Try a scenario", then a "Reset" `TextButton` and a close icon.
- **Body, top to bottom:**
  - `WeightChart(state.dashboard, scenario = result.points)`, 220 dp tall;
  - the headline text;
  - a muted line "About %.2f kg a week." (habits mode);
  - a two-option segmented row, "Change my habits" | "Change my goal";
  - stepper rows (label left, then `−` value `+`, each button ≥ 44 dp);
  - `Disclaimer()`.
- **Habits mode:** steppers for food, runs and km.
- **Goal mode:** steppers for goal kg (0.5 steps, 40–200) and goal date (± 7 days, never before tomorrow). Runs and km
  keep the habits-mode values.
- **State:** `var input by remember { mutableStateOf(scenarioStart(...)) }`. "Reset" sets it back to `scenarioStart(...)`.

**Copy** (exact; the `Format.kt` helpers return these):
- `scenarioHeadline`:
  - if `tooFast`: "Faster than 1 kg a week. Not suggested.", shown in `Palette.Amber`;
  - else if the arrival is null: "At this rate you won't reach %s kg.";
  - else if `daysLate == 0`: "%s kg by %s, right on your goal date.";
  - else if early: "%s kg by %s, %d days before your goal.";
  - else: "%s kg by %s, %d days after your goal." For both early and late, use "1 day" when it's 1.
  - Format the kg with `plainKg` and the date with `dayMonthLong`.
- `goalHeadline`:
  - if `reached`: "Goal reached.";
  - else if `kgPerWeek == null`: "Pick a date after today.";
  - else if `tooFast`: "That's faster than 1 kg a week. %s is more realistic.", with the suggested date;
  - else if `floored`: "Eat 1,800 kcal a day and run %.1f km more a week.";
  - else: "Eat about %,d kcal a day to reach %s kg by %s." (food rounded to the nearest 10).

- [ ] **Step 1: Write the failing FormatTest cases**

```kotlin
@Test fun scenarioHeadlines() {
    val base = ScenarioResult(550.0, 0.5, false, LocalDate.of(2027, 1, 10), 82.3, 3, emptyList())
    assertEquals("82 kg by 10 January, 3 days after your goal.", scenarioHeadline(base, 82.0))
    assertEquals("82 kg by 6 January, 1 day before your goal.", scenarioHeadline(base.copy(arrival = LocalDate.of(2027, 1, 6), daysLate = -1), 82.0))
    assertEquals("At this rate you won't reach 82 kg.", scenarioHeadline(base.copy(arrival = null, daysLate = null), 82.0))
    assertEquals("Faster than 1 kg a week. Not suggested.", scenarioHeadline(base.copy(tooFast = true), 82.0))
}

@Test fun goalHeadlines() {
    val date = LocalDate.of(2027, 1, 7)
    val ok = GoalSolution(0.54, 2014.0, false, 0.0, false, null, false)
    assertEquals("Eat about 2,010 kcal a day to reach 82 kg by 7 January.", goalHeadline(ok, 82.0, date))
    assertEquals("Eat 1,800 kcal a day and run 4.2 km more a week.", goalHeadline(ok.copy(foodKcal = 1800.0, floored = true, extraKmPerWeek = 4.2), 82.0, date))
    assertEquals("Goal reached.", goalHeadline(ok.copy(reached = true), 82.0, date))
    assertEquals("Pick a date after today.", goalHeadline(ok.copy(kgPerWeek = null, foodKcal = null), 82.0, date))
}
```

(Check `plainKg(82.0)` and `dayMonthLong` output in the existing `FormatTest` first, and adjust the expected strings
to match what they really produce, for example "82" vs "82.0".)

- [ ] **Step 2: Implement the copy helpers, `ScenarioSheet`, the chart series and the Plan row**

- [ ] **Step 3: Add screenshot tests** in `ScreenshotTest.kt`, next to `plan()`:

```kotlin
@Test fun planScenario() = shoot("4b-plan-scenario") { ScenarioSheet(sampleState(), onDismiss = {}) }
@Test fun planScenarioGoal() = shoot("4c-plan-scenario-goal") { ScenarioSheet(sampleState(), onDismiss = {}, initialGoalMode = true) }
```

(If `shoot` can't capture a `Dialog`, extract the sheet body into `ScenarioContent(state, ...)` and shoot that,
following the pattern other screenshot tests use for dialogs.)

- [ ] **Step 4: Run core tests, commit, push, and read CI**

Run: `./gradlew :core:test`, then push and wait for the push run (`scratchpad/waitci.sh <sha>`).
Expected:
- build, unit tests, emulator and publish all succeed;
- the screenshot artefacts include `4b-plan-scenario.png` and `4c-plan-scenario-goal.png`, and they look right
  (chart with a dotted line, headline, steppers, disclaimer).

Commit message: `Plan: Try a scenario sheet with a dotted what-if line`.

### Task 5: Release housekeeping

**Files:**
- Modify: `app/build.gradle.kts`: versionCode 17 → 18, versionName "0.8.2" → "0.9.0".
- Create: `fastlane/metadata/android/en-US/changelogs/18.txt`: "New: Try a scenario on the Plan page. Change food, runs
  or your goal and see where you'd land, without changing your plan."
- Modify:
  - `HANDOVER.md`: what changed, the new decision (scenario sheet is look-only, approach A), and that it's unmerged;
  - `.github/emulator/smoke.py`: only if it walks the Plan list by index, so a new row doesn't break it. Check it
    first, and change nothing otherwise.
- Modify: `docs/screenshots/`: add the new Plan scenario screenshot if the README lists Plan screenshots.

- [ ] **Step 1: Make the edits above**
- [ ] **Step 2: Run `./gradlew :core:test` and the full CI, both green**
- [ ] **Step 3: Commit and push** with message `Downhill 0.9.0: scenario simulator`
