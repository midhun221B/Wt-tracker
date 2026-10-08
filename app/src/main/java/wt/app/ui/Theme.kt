package wt.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Fixed series colours so "planned" and "realistic" look the same whatever the wallpaper theme. */
@Immutable
data class SeriesColors(
    val planned: Color,
    val realistic: Color,
    val band: Color,
    val average: Color,
    val raw: Color,
    val good: Color,
    val warn: Color,
)

private val lightSeries = SeriesColors(
    planned = Color(0xFF2F6FD0),
    realistic = Color(0xFFD9640F),
    band = Color(0xFFD9640F).copy(alpha = 0.16f),
    average = Color(0xFF8A5A2B),
    raw = Color(0xFF7A7A80),
    good = Color(0xFF2E7D32),
    warn = Color(0xFFC62828),
)

private val darkSeries = SeriesColors(
    planned = Color(0xFF7FB0FF),
    realistic = Color(0xFFFFA25C),
    band = Color(0xFFFFA25C).copy(alpha = 0.20f),
    average = Color(0xFFE0B48A),
    raw = Color(0xFFA0A0A8),
    good = Color(0xFF81C784),
    warn = Color(0xFFEF9A9A),
)

val LocalSeriesColors = staticCompositionLocalOf { lightSeries }

@Composable
fun WtTheme(dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSeriesColors provides if (dark) darkSeries else lightSeries) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
