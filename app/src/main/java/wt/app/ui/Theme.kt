package wt.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import wt.app.R

/** "B orange": dark athletic theme with a Strava-orange accent. */
object Palette {
    val Background = Color(0xFF0E1113)
    val Card = Color(0xFF171C20)
    val CardHigh = Color(0xFF262D33)
    val Outline = Color(0xFF3A434A)
    val Text = Color(0xFFF2F4F5)
    val Muted = Color(0xFF9AA4AC)
    val Accent = Color(0xFFFC5200)
    val OnAccent = Color(0xFF0E1113)
    val Warn = Color(0xFFFFC857)
    val Planned = Color(0xFF7FA6C9)
    val Error = Color(0xFFFF8A80)
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

/** Condensed face for big numbers only. */
val BarlowCondensed = FontFamily(
    Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlowcondensed_bold, FontWeight.Bold),
)

/** Style for large figures (weights, distances, kcal). */
fun numberStyle(size: TextUnit, color: Color = Color.Unspecified) =
    TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = size, color = color, lineHeight = size * 1.05)

/** Fixed series colours for charts so "planned" and "realistic" always read the same way. */
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

private val series = SeriesColors(
    planned = Palette.Planned,
    realistic = Palette.Accent,
    band = Palette.Accent.copy(alpha = 0.16f),
    average = Palette.Muted.copy(alpha = 0.6f),
    raw = Palette.Muted,
    good = Palette.Accent,
    warn = Palette.Warn,
)

val LocalSeriesColors = staticCompositionLocalOf { series }

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.Accent,
    onPrimaryContainer = Palette.OnAccent,
    secondary = Palette.Planned,
    onSecondary = Palette.OnAccent,
    secondaryContainer = Palette.CardHigh,
    onSecondaryContainer = Palette.Text,
    tertiary = Palette.Warn,
    onTertiary = Palette.OnAccent,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Background,
    onSurface = Palette.Text,
    surfaceVariant = Palette.CardHigh,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Card,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.Card,
    surfaceContainerHighest = Palette.Card,
    outline = Palette.Outline,
    outlineVariant = Palette.CardHigh,
    error = Palette.Error,
    onError = Palette.OnAccent,
    errorContainer = Color(0xFF3B2A12),
    onErrorContainer = Palette.Text,
)

private val typography = Typography().run {
    fun TextStyle.barlow() = copy(fontFamily = Barlow)
    Typography(
        displayLarge = displayLarge.barlow(), displayMedium = displayMedium.barlow(), displaySmall = displaySmall.barlow(),
        headlineLarge = headlineLarge.barlow(), headlineMedium = headlineMedium.barlow(), headlineSmall = headlineSmall.barlow(),
        titleLarge = titleLarge.barlow().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.barlow().copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        titleSmall = titleSmall.barlow().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.barlow(), bodyMedium = bodyMedium.barlow(), bodySmall = bodySmall.barlow().copy(fontSize = 13.sp),
        labelLarge = labelLarge.barlow().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = labelMedium.barlow().copy(fontSize = 13.sp, letterSpacing = 0.sp),
        labelSmall = labelSmall.barlow().copy(fontSize = 12.sp, letterSpacing = 0.sp),
    )
}

/** The app is always dark. [dynamicColor] is ignored (kept so callers don't change). */
@Suppress("UNUSED_PARAMETER")
@Composable
fun WtTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSeriesColors provides series) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            // Every screen gets the dark background and light text, even outside the Scaffold.
            Surface(color = Palette.Background, contentColor = Palette.Text, content = content)
        }
    }
}
