package wt.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wt.app.data.BodyCompEntity
import wt.app.data.PlanEntity
import wt.app.data.ProfileEntity
import wt.app.data.RunEntity
import wt.app.data.WeightEntity
import wt.app.ui.BodyDialog
import wt.app.ui.CheckpointDialog
import wt.app.ui.EnergyDialog
import wt.app.ui.HistoryDialog
import wt.app.ui.RebaselineDialog
import wt.app.ui.BodyScreen
import wt.app.ui.LocalInlineDialogs
import wt.app.ui.RunDialog
import wt.app.ui.DashboardScreen
import wt.app.ui.DistanceCard
import wt.app.ui.DistanceRange
import wt.app.ui.Palette
import wt.app.ui.LogScreen
import wt.app.ui.PlanScreen
import wt.app.ui.RunLoggedBanner
import wt.app.ui.OnboardingScreen
import wt.app.ui.RunsScreen
import wt.app.ui.SetupDraft
import wt.app.ui.SetupStep
import wt.app.ui.SettingsScreen
import wt.app.ui.UiState
import wt.app.ui.WtTheme
import wt.core.dashboard.buildDashboard
import wt.core.model.Defaults
import java.io.File
import java.time.LocalDate
import java.util.Random

/**
 * Renders screens with sample data and saves PNGs to app/build/screenshots (uploaded by CI).
 * Sample: the start weight, then weekly Monday weigh-ins losing ~0.35 kg/week, and a run most days.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1500dp-xhdpi") // owner's Nothing Phone (3a) is about 411 dp wide
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 5)

    private fun sampleState(): UiState {
        val rnd = Random(7)
        val weights = listOf(0, 4, 11, 18, 25).map { d -> // 8 Oct, then Mondays 12 Oct to 2 Nov
            WeightEntity(Defaults.START.plusDays(d.toLong()), Math.round((88.3 - 0.05 * d + rnd.nextGaussian() * 0.35) * 10) / 10.0)
        }
        val runs = (0..28).filter { it % 7 !in setOf(2, 5) }.mapIndexed { i, d ->
            RunEntity(i + 1L, Defaults.START.plusDays(d.toLong()), 3.0 + (d / 25) * 0.5, 1260 + rnd.nextInt(120))
        } + RunEntity(100, null, 2.81, 1216) + RunEntity(101, LocalDate.of(2026, 10, 4), 3.0, 1290) // a run before the plan
        val body = listOf(
            BodyCompEntity(Defaults.START, 29.2, 16.0, 60.1, 37.0, 62.5, 1818.0),
            BodyCompEntity(Defaults.START.plusDays(14), 28.8, 15.5, 60.0, 37.1, 62.3, 1810.0),
            BodyCompEntity(Defaults.START.plusDays(28), 28.3, 15.0, 59.9, 37.2, 62.2, 1805.0),
        )
        val profile = ProfileEntity.from(Defaults.profile)
        return UiState(
            today = today,
            dashboard = buildDashboard(
                weights.map { it.toModel() }, runs.map { it.toModel() }, setOf(Defaults.START.plusDays(2)),
                body.map { it.toModel() }, Defaults.planCheckpoints, profile.toModel(), today,
            ),
            weights = weights,
            runs = runs.sortedByDescending { it.date },
            restDays = setOf(Defaults.START.plusDays(2)),
            body = body,
            checkpoints = Defaults.planCheckpoints,
            plans = listOf(PlanEntity(1, Defaults.START, true, "Original plan")),
            profile = profile,
        )
    }

    /** A fresh install on the plan's first day: one weigh-in, no runs, no measurements. */
    private fun freshState(): UiState {
        val day = Defaults.START
        val weights = listOf(WeightEntity(day, 88.3))
        val profile = ProfileEntity.from(Defaults.profile)
        return UiState(
            today = day,
            dashboard = buildDashboard(weights.map { it.toModel() }, emptyList(), emptySet(), emptyList(), Defaults.planCheckpoints, profile.toModel(), day),
            weights = weights,
            runs = emptyList(),
            restDays = emptySet(),
            body = emptyList(),
            checkpoints = Defaults.planCheckpoints,
            plans = listOf(PlanEntity(1, day, true, "Original plan")),
            profile = profile,
        )
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { WtTheme(dynamicColor = false) { content() } }
        compose.waitForIdle()
        // Draw the view hierarchy directly; PixelCopy-based captureToImage() can hang under Robolectric.
        val bitmap = compose.activity.window.decorView.drawToBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun dashboard() = shoot("1-dashboard") { DashboardScreen(sampleState().dashboard) }

    // The Trend distance card's other two ranges (2 weeks shows in 1-dashboard).
    @Test fun distanceMonth() = shoot("1c-distance-month") {
        Box(Modifier.background(Palette.Background).padding(16.dp)) { DistanceCard(sampleState().dashboard, DistanceRange.Month) }
    }

    @Test fun distanceAllTime() = shoot("1d-distance-all-time") {
        Box(Modifier.background(Palette.Background).padding(16.dp)) { DistanceCard(sampleState().dashboard, DistanceRange.AllTime) }
    }

    // Three daily weigh-ins losing 0.5 kg a day project far below the plan: the axis must stay readable.
    @Test fun dashboardSteep() = shoot("1b-dashboard-steep") {
        val day = Defaults.START.plusDays(2)
        val weights = listOf(88.3, 87.8, 87.3).mapIndexed { i, kg -> wt.core.model.WeightEntry(Defaults.START.plusDays(i.toLong()), kg) }
        DashboardScreen(buildDashboard(weights, emptyList(), emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, day))
    }

    // 5 Nov is a Thursday; the Monday 2 Nov weigh-in is done, so the card shows next week's.
    @Test fun log() = shoot("2-today") { LogScreen(sampleState(), {}, {}, { _, _ -> }, {}, onScreenshot = {}) }

    // Weigh-in day Thursday and this week's weight and measurement left out, so the weigh-in is due.
    @Test fun weighIn() = shoot("2b-weigh-in") {
        val s = sampleState()
        LogScreen(
            s.copy(
                profile = s.profile.copy(weighInDay = 4),
                weights = s.weights.filter { it.date < LocalDate.of(2026, 11, 2) },
                body = s.body.filter { it.date < LocalDate.of(2026, 11, 2) },
            ),
            {}, {}, { _, _ -> }, {}, onScreenshot = {},
        )
    }

    // The owner's case: a weight already logged today in a week that has one, so only the small card shows.
    @Test fun loggedToday() = shoot("2c-logged-today") {
        val s = sampleState()
        LogScreen(s.copy(weights = s.weights + WeightEntity(today, 87.2)), {}, {}, { _, _ -> }, {}, onScreenshot = {})
    }

    // Right after saving this week's weigh-in (86.9 kg today): the goal ring has filled to the new weight.
    @Test fun weighInSaved() = shoot("2g-weigh-in-saved") {
        val s = sampleState()
        LogScreen(s.copy(weights = s.weights + WeightEntity(today, 86.9)), {}, {}, { _, _ -> }, {}, onScreenshot = {}, savedWeighIn = today)
    }

    // Today before the run is logged, and a rest day instead of a run.
    @Test fun noRunYet() = shoot("2d-no-run-yet") {
        val s = sampleState()
        LogScreen(s.copy(runs = s.runs.filter { it.date != today }), {}, {}, { _, _ -> }, {}, onScreenshot = {})
    }

    @Test fun restDay() = shoot("2e-rest-day") {
        val s = sampleState()
        LogScreen(s.copy(runs = s.runs.filter { it.date != today }, restDays = s.restDays + today), {}, {}, { _, _ -> }, {}, onScreenshot = {})
    }

    // The banner lives in the app scaffold; drawn here above Today to show how it looks.
    @Test fun runLogged() = shoot("2f-run-logged") {
        Column {
            RunLoggedBanner(3, 10.5, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LogScreen(sampleState(), {}, {}, { _, _ -> }, {}, onScreenshot = {})
        }
    }

    @Test fun runs() = shoot("3-runs") {
        val s = sampleState()
        RunsScreen(s.runs, today, {}, {}, onImportStrava = {}, week1 = s.dashboard.week1, allTime = s.dashboard.allTime())
    }

    // Fresh install: what each tab looks like with one weigh-in and nothing else.
    @Test fun freshToday() = shoot("9a-fresh-today") { LogScreen(freshState(), {}, {}, { _, _ -> }, {}, onScreenshot = {}) }

    @Test fun freshTrend() = shoot("9b-fresh-trend") { DashboardScreen(freshState().dashboard) }

    @Test fun freshRuns() = shoot("9c-fresh-runs") {
        val s = freshState()
        RunsScreen(s.runs, s.today, {}, {}, onImportStrava = {}, week1 = s.dashboard.week1, allTime = s.dashboard.allTime())
    }

    @Test fun freshBody() = shoot("9d-fresh-body") {
        BodyScreen(emptyList(), Defaults.START, {}, {}, onScreenshot = {}, weighInDay = 1, weightDates = listOf(Defaults.START))
    }

    @Test fun freshPlan() = shoot("9e-fresh-plan") { PlanScreen(freshState(), {}, {}, {}) }

    @Test fun body() = shoot("5-body") { BodyScreen(sampleState().body, today, {}, {}, onScreenshot = {}) }

    @Test fun plan() = shoot("4-plan") { PlanScreen(sampleState(), {}, {}, {}) }

    @Test fun settings() = shoot("8-settings") {
        SettingsScreen(sampleState().profile, notificationsAllowed = true, onReminder = { _, _, _ -> }, onWeighInDay = {}, onExport = {}, onRestore = {})
    }

    // Dialogs normally open in their own window; LocalInlineDialogs draws them in place for the capture.
    @Test fun addRun() = shoot("6-add-run") {
        CompositionLocalProvider(LocalInlineDialogs provides true) {
            RunDialog(null, today, onDismiss = {}, onSave = {}, onFromScreenshot = {}, onImportCsv = {})
        }
    }

    // The Plan tab's pop-ups, drawn in place.
    @Test fun planCheckpoints() = shoot("4b-plan-checkpoints") {
        CompositionLocalProvider(LocalInlineDialogs provides true) { CheckpointDialog(Defaults.planCheckpoints, {}, {}) }
    }

    @Test fun planRebaseline() = shoot("4c-plan-rebaseline") {
        CompositionLocalProvider(LocalInlineDialogs provides true) { RebaselineDialog(sampleState(), {}, {}) }
    }

    @Test fun planEnergy() = shoot("4d-plan-energy") {
        CompositionLocalProvider(LocalInlineDialogs provides true) { EnergyDialog(sampleState().profile, {}, {}) }
    }

    @Test fun planHistory() = shoot("4e-plan-history") {
        CompositionLocalProvider(LocalInlineDialogs provides true) { HistoryDialog(sampleState().plans, {}) }
    }

    @Test fun addMeasurement() = shoot("7-add-measurement") {
        CompositionLocalProvider(LocalInlineDialogs provides true) {
            BodyDialog(null, today, sampleState().body.last(), {}, { _, _ -> }, onFromScreenshot = {})
        }
    }

    // First-run setup on Saturday 10 October: 88.3 kg today, 82 kg by 7 January. Phone-height frames.
    private val setupDay = LocalDate.of(2026, 10, 10)
    private val setupDraft = SetupDraft(kg = "88.3", goalKg = "82", goalDate = LocalDate.of(2027, 1, 7))

    private fun setup(name: String, step: SetupStep, draft: SetupDraft = setupDraft) = shoot(name) {
        OnboardingScreen(setupDay, { _, _, _ -> }, {}, {}, initialStep = step, initialDraft = draft)
    }

    @Config(qualifiers = "w411dp-h900dp-xhdpi") @Test fun setupWelcome() = setup("10a-setup-welcome", SetupStep.Welcome)
    @Config(qualifiers = "w411dp-h900dp-xhdpi") @Test fun setupWeight() = setup("10b-setup-weight", SetupStep.Weight)
    @Config(qualifiers = "w411dp-h900dp-xhdpi") @Test fun setupGoal() = setup("10c-setup-goal", SetupStep.Goal)
    @Config(qualifiers = "w411dp-h900dp-xhdpi") @Test fun setupTooFast() =
        setup("10d-setup-too-fast", SetupStep.Goal, setupDraft.copy(goalDate = LocalDate.of(2026, 11, 7)))
    @Config(qualifiers = "w411dp-h900dp-xhdpi") @Test fun setupWeighIn() = setup("10e-setup-weigh-in", SetupStep.WeighIn)
    @Config(qualifiers = "w411dp-h900dp-xhdpi") @Test fun setupPlan() = setup("10f-setup-plan", SetupStep.Plan)
}
