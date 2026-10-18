package wt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.core.summary.RunWeekStreak
import wt.core.summary.STREAK_RUNS_PER_WEEK

/** Solid: the streak is on and this week counts. Waiting: on, but this week needs more runs. Off: no streak. */
enum class FlameLook { Solid, Waiting, Off }

/** A flame in a 40 × 48 box with a wide round base for the number; the inner curve stays above the number. */
private const val FLAME = "M20 2 C23 11 35 17 35 31 A15 15 0 0 1 5 31 C5 23 10 18 13 14 C13.5 17 15 19 17.5 19.5 C16.5 13 18 8 20 2 Z"

/** The run-streak flame, [width] wide, with [weeks] inside. Decorative: the text beside it says the same. */
@Composable
fun StreakFlame(weeks: Int, look: FlameLook, width: Dp) {
    val path = remember { PathParser().parsePathString(FLAME).toPath() }
    val (fill, edge, number) = when (look) {
        FlameLook.Solid -> Triple(Palette.Accent, Palette.Accent, Palette.OnAccent)
        FlameLook.Waiting -> Triple(Palette.Background, Palette.Accent, Palette.Accent)
        FlameLook.Off -> Triple(Palette.Background, Palette.Outline, Palette.Muted)
    }
    val fontSize = with(LocalDensity.current) { (width * if (weeks >= 10) 0.42f else 0.52f).toSp() }
    Box(Modifier.size(width, width * 1.2f).clearAndSetSemantics {}) {
        Canvas(Modifier.matchParentSize()) {
            scale(size.width / 40f, pivot = Offset.Zero) {
                drawPath(path, fill)
                drawPath(path, edge, style = Stroke(2.5f))
            }
        }
        // The number sits in the round base, below the inner curve.
        Text("$weeks", Modifier.align(BiasAlignment(0f, 0.55f)), style = numberStyle(fontSize, number))
    }
}

/**
 * Today's streak, next to the week's dots: the flame and two short lines. Solid once this week has its runs, outlined
 * while it still needs some, grey with no streak (and the last one's length, if there was one).
 */
@Composable
fun TodayStreak(s: RunWeekStreak, modifier: Modifier = Modifier) {
    fun runs(n: Int) = if (n == 1) "1 more run" else "$n more runs"
    val (look, title, sub) = when {
        s.thisWeekCounts -> Triple(FlameLook.Solid, "Week streak", "This week counts")
        s.weeks > 0 -> Triple(FlameLook.Waiting, runs(s.runsToGo), "keeps it going")
        else -> Triple(
            FlameLook.Off,
            s.endedWeeks?.let { if (it == 1) "Last streak: 1 week" else "Last streak: $it weeks" } ?: "No streak yet",
            if (s.runsThisWeek == 0) "$STREAK_RUNS_PER_WEEK runs start one" else "${runs(s.runsToGo)} start one",
        )
    }
    val titleColor = when (look) {
        FlameLook.Solid -> Palette.Accent
        FlameLook.Waiting -> Palette.Warn
        FlameLook.Off -> Palette.Text
    }
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Run streak ${s.weeks} weeks. $title, $sub" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(sub, fontSize = 11.sp, color = Palette.Muted)
        }
        StreakFlame(s.weeks, look, 30.dp)
    }
}
