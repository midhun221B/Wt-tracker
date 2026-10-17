# Partner Share Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From the "Weigh-in saved" card, send the partner one picture card with a caption: the goal ring, the downhill plan trail and last week's run strip. It goes through Android's share menu (WhatsApp).

**Architecture:**
- **core:** a pure function `weekShare(dashboard, date)` computes every number, point and the caption, and has unit tests.
- **app:** `ShareCard` draws them at a fixed 360×450 dp. A preview dialog captures the drawn card with a Compose graphics layer, writes a PNG to the cache, and sends it through a `FileProvider` with `ACTION_SEND`.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01: `rememberGraphicsLayer`, `toImageBitmap`), `androidx.core` FileProvider, JUnit/kotlin.test, Robolectric screenshot tests.

**Spec:** `docs/superpowers/specs/2026-10-15-partner-share-card-design.md`. The approved look is on the canvas https://claude.ai/artifact/BcrEGMr357vuq6Q4yWhWuS, boards "Mix · week 5" and "Mix · week 2".

## Global Constraints
- Sentence case in every label and button, no all-caps. Touch targets ≥ 44 dp.
- Colours:
  - background `#0E1113`, cards `#171C20` (`Palette.Card`), raised `Palette.CardHigh`;
  - text `#F2F4F5`, muted `#9AA4AC`;
  - accent `#FC5200`, rest/planned `#7FA6C9` dashed.
  - Planned vs mine differ by dash and lightness.
- Fonts: Barlow Condensed for big numbers (`numberStyle`), Barlow otherwise.
- Footer copy exactly: "Estimates, not medical advice" (left) and "Wt-tracker" (right).
- Numbers have one decimal ("87.5 kg", "15.0 km"). The change sign is "−" (U+2212) for a loss and "+" for a gain.
- All maths lives in `core/` with tests; the UI only formats `WeekShare` values.
- No new permissions, no internet, no Room schema change.
- Release: `versionCode` 10, `versionName` "0.5.0".
- App code can only be built in CI (`.github/workflows/android.yml`). Locally run `./gradlew :core:test`.

## Review Focus
1. **Sharing an older weigh-in** (editing 26 Oct, then sharing on 2 Nov): the card must show that day's kg, change, week and the strip of the week before it, not today's. → Task 1 `olderWeighInUsesThatDay`.
2. **A weight above the plan start** (e.g. 89.4 after holidays): the trail's kg range must include it, so the "You" dot stays inside the picture. → Task 1 `rangeIncludesWeightsAboveStart`.
3. **A weigh-in after the goal date:** "weeks to go" must be 0, never negative, and the label reads "Goal week". → Task 1 `afterGoalDateHasNoWeeksToGo`, Task 2 label.
4. **Phone locale with a decimal comma:** the caption must still read "87.5", because WhatsApp text isn't localised by the app. → Task 1 `captionIgnoresDecimalCommaLocale`.
5. **Two weigh-ins in one week** (an extra weigh-in): the change and "since" date come from the immediately previous weigh-in. → Task 1 `extraWeighInComparesWithThePreviousOne`.

---

### Task 1: `weekShare` in core

**Files:**
- Modify: `core/src/main/kotlin/wt/core/summary/WeighInProgress.kt` (add `previousDate`)
- Create: `core/src/main/kotlin/wt/core/summary/WeekShare.kt`
- Test: `core/src/test/kotlin/wt/core/WeekShareTest.kt`

**Interfaces:**
- Consumes:
  - `Dashboard` (`plan: PlannedLine`, `weights`, `week1`, `distance`, `programWeek()`);
  - `weighInProgress(...)`, `programWeekIndex(week1, date)`, `weekStart(date)`, `trendWait(entries, asOf)`, `DayDistance`, `DayKind`.
- Produces:
  ```kotlin
  // WeighInProgress gains: val previousDate: LocalDate?  (date of the weigh-in changeKg is measured from)
  data class WeekShare(
      val date: LocalDate, val week: Int, val totalWeeks: Int, val weeksToGo: Int,
      val kg: Double, val changeKg: Double?, val previousDate: LocalDate?, val fraction: Double,
      val trail: List<Checkpoint>,        // active plan checkpoints, start..goal
      val posts: List<Checkpoint>,        // Monday of each program week 1..totalWeeks, with plan.at(monday)
      val currentPost: Checkpoint,        // the post of date's week
      val weighIns: List<WeightEntry>,    // weights from trail.first().date to date, sorted
      val planMarkerKg: Double?,          // plan.at(date) once the trend wait is over, else null
      val kgRange: ClosedFloatingPointRange<Double>, // min(goal kg, weighIns) .. max(start kg, weighIns)
      val strip: List<DayDistance>,       // 7 days, Monday first
      val stripIsLastWeek: Boolean,
      val runs: Int, val km: Double,      // over the strip's days
      val caption: String,
  )
  fun weekShare(dashboard: Dashboard, date: LocalDate): WeekShare?   // null when date has no weight
  ```

Test data (all in the test file):
- Weights: 8 Oct 88.6, 12 Oct 88.4, 19 Oct 87.6, 26 Oct 87.9, 2 Nov 87.5.
- Runs, 3.0 km each: 26, 27, 29, 30 and 31 Oct. Rest day 28 Oct.
- `Defaults.planCheckpoints`, `Defaults.profile`, dashboard `asOf` = 2 Nov 2026.

- [ ] **Step 1: Write the failing tests** in `WeekShareTest`:
  - `weekFiveCard`:
    - `week == 5`, `totalWeeks == 14`, `weeksToGo == 9`, `kg == 87.5`;
    - `changeKg` −0.4 (±1e-9), `previousDate == 26 Oct`;
    - `planMarkerKg == plan.at(2 Nov)`;
    - `stripIsLastWeek`, `strip.first().date == 26 Oct`;
    - strip kinds `[Run, Run, Rest, Run, Run, Run, Missed]`, `runs == 5`, `km == 15.0`;
    - `caption == "Week 5 weigh-in: 87.5 kg (−0.4). Last week 5 runs, 15.0 km."`;
    - `currentPost.date == 2 Nov`, `posts.size == 14`, `trail == plan.checkpoints`.
  - `firstWeighIn`: only 8 Oct 88.6, `asOf` 8 Oct.
    - `changeKg == null`, `previousDate == null`, `planMarkerKg == null`;
    - `!stripIsLastWeek`, strip starts 5 Oct with kinds `[BeforeStart×3, Open, Future×3]`;
    - `caption == "Week 1 weigh-in: 88.6 kg. This week no runs."`.
  - `beforeTheTrendWait`: weights 8 and 12 Oct, `asOf` 12 Oct → `planMarkerKg == null`, `changeKg` −0.2, `stripIsLastWeek`.
  - `oneRunCaption`: a single 4.2 km run last week → caption ends "Last week 1 run, 4.2 km.".
  - `olderWeighInUsesThatDay`: `weekShare(d, 26 Oct)` → `kg == 87.9`, `changeKg` +0.3, `previousDate == 19 Oct`, `week == 4`, `strip.first().date == 19 Oct`, caption contains "(+0.3)".
  - `rangeIncludesWeightsAboveStart`: add 19 Oct 89.4 → `kgRange.endInclusive >= 89.4`, `kgRange.start <= 82.0`.
  - `afterGoalDateHasNoWeeksToGo`: weigh-in 11 Jan 2027 (asOf the same) → `weeksToGo == 0`.
  - `captionIgnoresDecimalCommaLocale`: `Locale.setDefault(Locale.GERMANY)` (restored in `finally`) → caption contains "87.5" and "15.0".
  - `extraWeighInComparesWithThePreviousOne`: add 30 Oct 87.7 → `changeKg` −0.2, `previousDate == 30 Oct`.
  - `noWeightThatDay`: `weekShare(d, 1 Nov) == null`.
  - `rebaselinedPlanTrail`: checkpoints `[(8 Oct, 88.3), (26 Oct, 87.0), (7 Jan, 82.0)]` → `trail` equals them, `week == 5` (numbering still from `week1`).
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew :core:test --tests wt.core.WeekShareTest`. Expected: compilation failure (`weekShare` unresolved).
- [ ] **Step 3: Implement.**
  - In `weighInProgress`, set `previousDate = before.lastOrNull()?.date`.
  - In `WeekShare.kt`:
    - **Week:** `week = programWeekIndex(week1, date).coerceAtLeast(1)`, `totalWeeks = dashboard.programWeek().second`, `weeksToGo = (totalWeeks - week).coerceAtLeast(0)`.
    - **Ring:** `fraction` comes from `weighInProgress(...).goal.fraction`.
    - **Plan marker:** shown when `trendWait(weights, date)` has `weighInsNeeded == 0` and `readyOn <= date`.
    - **Strip:** the `DistanceWeek` with `monday == weekStart(date).minusWeeks(1)` when it has any day that isn't `BeforeStart`, else the week of `date`.
    - **Caption:** format with `String.format(Locale.ROOT, …)`.
    - **Change text:** `changeKg` rounded to one decimal → "−x.x" for a loss, "+x.x" for a gain, "0.0" otherwise.
    - **Runs text:** "no runs" / "1 run, x.x km" / "n runs, x.x km".
    - **Label:** "Last week" or "This week".
  - KDoc on `WeekShare` explains the strip and plan-marker rules.
- [ ] **Step 4: Run them to verify they pass.** Run `./gradlew :core:test`. Expected: BUILD SUCCESSFUL, all core tests green, including the existing `WeighInTest`.
- [ ] **Step 5: Commit** with the message "core: weekShare for the partner share card".

### Task 2: `ShareCard` composable and screenshots

**Files:**
- Create: `app/src/main/java/wt/app/ui/ShareCard.kt`
- Modify: `app/src/test/java/wt/app/ScreenshotTest.kt`

**Interfaces:**
- Consumes: `WeekShare` (Task 1); `ProgressRing`, `RestMark`, `numberStyle`, `Palette`, `dayMonth`, `shortDay` from `wt.app.ui`.
- Produces: `@Composable fun ShareCard(s: WeekShare, goalKg: Double, modifier: Modifier = Modifier)`, drawn at exactly `360.dp × 450.dp`, background `Palette.Background`, rounded 8 dp.

- [ ] **Step 1: Add screenshot tests** (they fail to compile until Step 2):
  - `@Test fun shareCard() = shoot("2i-share-card") { ShareCard(weekShare(sampleState().dashboard, LocalDate.of(2026, 11, 2))!!, 82.0) }`
  - `@Test fun shareCardEarly() = shoot("2j-share-card-early") { … }`, built from a two-weigh-in state:
    - 8 Oct 88.3 and 12 Oct 88.1, dashboard `asOf` 12 Oct, built the same way as `freshState()`;
    - shared for 12 Oct.
- [ ] **Step 2: Implement `ShareCard`.** It follows the canvas board "Mix · week 5" (padding 16/18, gaps 10):
  1. **Ring row:**
     - `ProgressRing(s.fraction, Modifier.size(150.dp))`;
     - inside the ring: kg (`numberStyle(38.sp)`), "kg on the scale" (10 sp, muted), and the pill "−0.4 kg since 26 Oct" (10 sp, accent text on accent at 16 % alpha, shown only when `changeKg != null`);
     - right of the ring: "Week 5 of 14" (`numberStyle(28.sp)`) and "Weigh-in · Mon, 2 Nov" (13 sp, muted).
  2. **Trail:**
     - label row: "Downhill to 82 kg" on the left; on the right "9 weeks to go", or "Goal week" when `weeksToGo == 0`;
     - a `Canvas` at 324×112 dp, with x by date from `trail.first().date` to `trail.last().date` and y by kg over `s.kgRange`:
       - hillside fill under the trail polyline in `Palette.Card`;
       - the trail dashed `#7FA6C9`, 2.5 dp;
       - post ticks 6 dp below the trail, the current one in accent;
       - the weigh-ins as a solid accent polyline, ending in a 7 dp accent dot with a 2.5 dp text-colour ring;
       - when `planMarkerKg != null`, a dashed 5 dp ring on the trail at `date` with "plan 86.6";
       - a white flag at the goal, labelled "82 kg · 7 Jan";
       - start label "88.3" and "You · 87.5" above the dot. Draw the text with `drawText` and a `TextMeasurer`.
  3. **Strip card** (`Palette.Card`, radius 12):
     - "Last week" / "This week" with "5 runs · 15.0 km";
     - seven 28 dp circles: run = accent + check; rest = `RestMark(28.dp)`; missed = `CardHigh` + dash; `BeforeStart`/`Open`/`Future` = `CardHigh` empty;
     - letters M–S underneath.
  4. **Footer:** "Estimates, not medical advice" / "Wt-tracker" (10 sp, muted).
- [ ] **Step 3: Verify in CI.** Run `./gradlew :core:test` locally, then push. Wait with `scratchpad/waitci.sh <sha>` for "completed success". Download `2i-share-card.png` and `2j-share-card-early.png` from the `debug-latest` release. Expected: they match the canvas boards; labels don't overlap; the dot sits inside the picture.
- [ ] **Step 4: Commit** with the message "Share card composable and screenshots".

### Task 3: Capture and share plumbing

**Files:**
- Create: `app/src/main/java/wt/app/ui/Share.kt`
- Create: `app/src/main/res/xml/share_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/wt/app/ShareIntentTest.kt` (Robolectric, `@Config(sdk = [34])`)

**Interfaces:**
- Produces:
  ```kotlin
  fun shareIntent(image: Uri, caption: String): Intent        // ACTION_SEND chooser target, not the chooser itself
  suspend fun saveSharePng(context: Context, bitmap: ImageBitmap, name: String): Uri   // cacheDir/share/<name>.png via FileProvider
  fun Context.startShare(image: Uri, caption: String)        // Intent.createChooser(shareIntent(..), "Share progress")
  ```

- [ ] **Step 1: Write the failing test** `shareIntentCarriesImageAndCaption`:
  - `action == Intent.ACTION_SEND`, `type == "image/png"`;
  - `getParcelableExtra<Uri>(EXTRA_STREAM) == uri`, `getStringExtra(EXTRA_TEXT) == caption`;
  - `flags and FLAG_GRANT_READ_URI_PERMISSION != 0`.
- [ ] **Step 2: Implement.**
  - `share_paths.xml`: `<cache-path name="share" path="share/" />`.
  - Manifest: `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.share" android:exported="false" android:grantUriPermissions="true">` with `meta-data android.support.FILE_PROVIDER_PATHS` pointing to `@xml/share_paths`.
  - `saveSharePng`: writes on `Dispatchers.IO` with `asAndroidBitmap().compress(PNG, 100, …)`, overwriting the file each time, and gets the URI from `FileProvider.getUriForFile(context, "${context.packageName}.share", file)`.
- [ ] **Step 3: Verify in CI.** Push and wait for green. Expected: `ShareIntentTest` passes in the app unit-test step, and the APK builds with the provider.
- [ ] **Step 4: Commit** with the message "Share plumbing: FileProvider, PNG save, ACTION_SEND".

### Task 4: Button, preview dialog and wiring

**Files:**
- Modify: `app/src/main/java/wt/app/ui/ShareCard.kt` (add `ShareDialog`)
- Modify: `app/src/main/java/wt/app/ui/LogScreen.kt` (`WeighInSavedCard`, state)
- Modify: `app/src/test/java/wt/app/ScreenshotTest.kt`

**Interfaces:**
- Consumes: `ShareCard` (Task 2), `saveSharePng`, `startShare` (Task 3), `weekShare` (Task 1).
- Produces:
  - `@Composable fun ShareDialog(s: WeekShare, goalKg: Double, onDismiss: () -> Unit)`;
  - `WeighInSavedCard(p, goalKg, onEdit, onShare: (() -> Unit)? = null)`.

- [ ] **Step 1: Add the screenshot test** `@Test fun sharePreview() = shoot("2k-share-preview") { ShareDialog(<week-5 WeekShare>, 82.0) {} }`. `2g-weigh-in-saved` now shows the button.
- [ ] **Step 2: Implement.**
  - **`WeighInSavedCard`:** a full-width "Share with partner" button between the plan line and Edit. It is an accent outlined button with a share icon, ≥ 44 dp tall.
  - **`ShareDialog`:**
    - `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`, with the card recorded through `rememberGraphicsLayer()` and `Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }`;
    - buttons "Cancel" (text) and "Send" (accent);
    - Send runs in `rememberCoroutineScope()`: `layer.toImageBitmap()`, then `saveSharePng(context, it, "wt-week-${s.week}")`, then `context.startShare(uri, s.caption)`, then `onDismiss()`;
    - on failure it shows "Couldn't share the card" inside the dialog.
  - **`LogScreen`:** a `var sharing by remember { mutableStateOf(false) }`. `onShare` sets it, and only when `weekShare(state.dashboard, date) != null`.
- [ ] **Step 3: Verify in CI.** Push, wait for green including the emulator job, and download `2g-weigh-in-saved.png` and `2k-share-preview.png`. Expected: the button is visible under the plan line, and the dialog shows the card with Cancel/Send.
- [ ] **Step 4: Commit** with the message "Share with partner: button and preview".

### Task 5: Release prep and review

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 10`, `versionName = "0.5.0"`)
- Modify: `HANDOVER.md`
- Modify: the scratchpad review page `ui-review/build.py` (not in the repo)

- [ ] **Step 1: Bump the version.**
- [ ] **Step 2: Update HANDOVER.**
  - Features line under Today: "Share with partner on Weigh-in saved: picture card (ring, downhill trail, last week's runs) through the share menu".
  - Open items: "View-only partner app (Android), planned".
  - "Not merged": this branch.
- [ ] **Step 3: Run checks and push.** Run `./gradlew :core:test` locally, push, and wait for CI green including the emulator job.
- [ ] **Step 4: Republish the review page.** Add the 2i/2j/2k crops and republish https://claude.ai/artifact/CMjwnosuL7NWwpDtkGeSfz with a "New: share card" note.
- [ ] **Step 5: Commit** with the message "Version 0.5.0; HANDOVER: partner share card". Leave it unmerged until the owner says "create a pull request and merge it".

## Verification (end to end)
- `./gradlew :core:test` is green locally, and CI `build`, `publish` and `emulator` are green on the final push.
- The screenshots 2i/2j/2k/2g match the approved canvas.
- **On the phone:**
  1. Log a weigh-in and tap "Share with partner" → Send.
  2. In WhatsApp, check the image and caption arrive.
  3. Send a second time and check the partner's chat appears directly in the share menu.
