package wt.app

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
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
import wt.app.ui.DashboardScreen
import wt.app.ui.LogScreen
import wt.app.ui.PlanScreen
import wt.app.ui.RunsScreen
import wt.app.ui.UiState
import wt.app.ui.WtTheme
import wt.core.dashboard.buildDashboard
import wt.core.model.Defaults
import java.io.File
import java.time.LocalDate
import java.util.Random

/**
 * Renders screens with sample data and saves PNGs to app/build/screenshots (uploaded by CI).
 * Sample: 4 weeks of noisy weigh-ins losing ~0.35 kg/week, a run most days.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1500dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 5)

    private fun sampleState(): UiState {
        val rnd = Random(7)
        val weights = (0..28).filter { it % 9 != 4 }.map { d ->
            WeightEntity(Defaults.START.plusDays(d.toLong()), Math.round((88.3 - 0.05 * d + rnd.nextGaussian() * 0.35) * 10) / 10.0)
        }
        val runs = (0..28).filter { it % 7 !in setOf(2, 5) }.mapIndexed { i, d ->
            RunEntity(i + 1L, Defaults.START.plusDays(d.toLong()), 3.0 + (d / 25) * 0.5, 1260 + rnd.nextInt(120))
        } + RunEntity(100, null, 2.81, 1216)
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

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { WtTheme(dynamicColor = false) { content() } }
        compose.waitForIdle()
        // Draw the view hierarchy directly; PixelCopy-based captureToImage() can hang under Robolectric.
        val bitmap = compose.activity.window.decorView.drawToBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Small JPEG preview for quick review from CI logs.
        val small = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
        val previews = File(dir, "preview").apply { mkdirs() }
        File(previews, "$name.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    @Test fun dashboard() = shoot("1-dashboard") { DashboardScreen(sampleState().dashboard) }

    @Test fun log() = shoot("2-today") { LogScreen(sampleState(), {}, {}, { _, _ -> }, {}) }

    @Test fun runs() = shoot("3-runs") { RunsScreen(sampleState().runs, today, {}, {}, onImportStrava = {}) }

    @Test fun plan() = shoot("4-plan") { PlanScreen(sampleState(), {}, {}, {}) }
}
