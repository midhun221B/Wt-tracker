package wt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.core.dashboard.Dashboard
import wt.core.summary.AllTimeDistance
import wt.core.summary.DayDistance
import wt.core.summary.DayKind
import wt.core.summary.DistanceWeek
import wt.core.summary.distanceMonth
import wt.core.summary.distanceMonths
import wt.core.summary.distanceSpan
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** The ranges of the Trend distance card. */
enum class DistanceRange(val label: String) { TwoWeeks("2 weeks"), Month("Month"), AllTime("All time") }

/**
 * Distance on Trend, one card with three ranges: the last 2 weeks as a bar per day (tap a day for its runs),
 * a calendar month of circles, and every week from the first entry to the goal week as columns.
 */
@Composable
fun DistanceCard(d: Dashboard, initialRange: DistanceRange = DistanceRange.TwoWeeks) {
    // Nothing to show before the first run (like the km chart on Runs).
    if (d.distance.none { w -> w.days.any { it.kind == DayKind.Run } }) return
    var range by rememberSaveable { mutableStateOf(initialRange) }
    SectionCard(null) {
        Text("Distance", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Background).padding(3.dp)) {
            DistanceRange.entries.forEach { r ->
                val on = r == range
                TextButton(
                    onClick = { range = r },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Palette.CardHigh else Palette.Background),
                ) { Text(r.label, color = if (on) Palette.Text else Palette.Muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        when (range) {
            DistanceRange.TwoWeeks -> TwoWeeks(d.distance, d.asOf)
            DistanceRange.Month -> MonthView(d.distance, d.asOf)
            DistanceRange.AllTime -> AllTimeColumns(d.allTime())
        }
    }
}

@Composable
private fun Headline(value: String, note: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(value, style = numberStyle(34.sp))
        Box(Modifier.padding(bottom = 4.dp)) { note() }
    }
}

/** A bar per day for this week and last; tapping a day shows its distance, time and pace below. */
@Composable
private fun TwoWeeks(weeks: List<DistanceWeek>, today: LocalDate) {
    val s = distanceSpan(weeks, 2)
    var selected by remember(today) { mutableStateOf(today) }
    val thisWeek = weeks.last().monday
    val top = s.longestKm.coerceAtLeast(1.0)
    Headline("%.1f km".format(s.days.sumOf { it.km })) {
        Text(
            (s.lastWeekKm?.let { "last week %.1f · ".format(it) } ?: "") + "this week %.1f".format(s.thisWeekKm),
            style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
        )
    }
    Row(
        Modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = "Kilometres per day, last two weeks" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        s.days.forEach { day ->
            Column(
                Modifier.weight(1f).height(132.dp).clickable(onClickLabel = "Show ${shortDay(day.date)}") { selected = day.date },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            ) {
                val on = day.date == selected
                when (day.kind) {
                    DayKind.Run -> {
                        Text("%.1f".format(day.km), style = MaterialTheme.typography.labelSmall, color = if (on) Palette.Text else Palette.Muted)
                        Box(
                            Modifier.fillMaxWidth().height((96 * day.km / top).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                // Last week dimmer, this week bright.
                                .background(if (day.date >= thisWeek) Palette.Accent else Palette.Accent.copy(alpha = 0.45f)),
                        )
                    }
                    DayKind.Rest -> RestMark(18.dp, Modifier.padding(bottom = 4.dp))
                    DayKind.Missed -> Box(Modifier.padding(bottom = 6.dp).size(width = 8.dp, height = 2.dp).background(Palette.Muted))
                    DayKind.Open, DayKind.Future -> Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.CardHigh))
                    DayKind.BeforeStart -> {}
                }
            }
        }
    }
    HorizontalDivider(color = Palette.CardHigh)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        s.days.forEach { day ->
            val on = day.date == selected
            Text(
                day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = if (on) Palette.Text else Palette.Muted,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
    s.days.firstOrNull { it.date == selected }?.let { DayDetail(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Longest", "%.1f km".format(s.longestKm), Modifier.weight(1f))
        Tile("Per run", s.averageRunKm?.let { "%.1f km".format(it) } ?: "–", Modifier.weight(1f))
        Tile("Run days", "${s.runDays}", Modifier.weight(1f))
    }
}

/** "Thu, 5 Nov · 3.5 km · 22:19 · 6:23 /km" for the tapped day. */
@Composable
private fun DayDetail(day: DayDistance) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Background).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(shortDay(day.date), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            when (day.kind) {
                DayKind.Run -> "%.1f km".format(day.km) +
                    if (day.durationSec > 0) " · ${duration(day.durationSec)} · ${pace(day.durationSec / day.km)}" else ""
                DayKind.Rest -> "Rest day"
                DayKind.Missed -> "No run"
                DayKind.Open -> "Nothing logged yet"
                DayKind.Future -> "To come"
                DayKind.BeforeStart -> "Before the start"
            },
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
        )
    }
}

/** A calendar month of circles: km on run days, the moon on rest days, a dash on missed days; ‹ › pages months. */
@Composable
private fun MonthView(weeks: List<DistanceWeek>, today: LocalDate) {
    val months = distanceMonths(weeks, today)
    var index by rememberSaveable { mutableIntStateOf(months.lastIndex) }
    val m = distanceMonth(weeks, months[index.coerceIn(months.indices)])
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { index-- }, enabled = index > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(m.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), style = MaterialTheme.typography.titleSmall)
            Text(
                "%.1f km · %d %s · %d %s".format(m.km, m.runs, if (m.runs == 1) "run" else "runs", m.restDays, if (m.restDays == 1) "rest day" else "rest days"),
                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            )
        }
        IconButton(onClick = { index++ }, enabled = index < months.lastIndex) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
    }
    Row {
        java.time.DayOfWeek.entries.forEach {
            Text(it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
    m.rows.forEach { row ->
        Row { row.forEach { MonthCell(it, today, Modifier.weight(1f)) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Legend("Run (km)") { Box(Modifier.size(14.dp).background(Palette.Accent, CircleShape)) }
        Legend("Rest") { RestMark(14.dp) }
        Legend("Missed") { Box(Modifier.size(width = 10.dp, height = 2.dp).background(Palette.Muted)) }
    }
}

@Composable
private fun MonthCell(day: DayDistance?, today: LocalDate, modifier: Modifier) {
    Column(modifier.height(54.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            val disc = Modifier.size(34.dp)
            if (day != null) when (day.kind) {
                DayKind.Run -> Box(
                    disc.background(Palette.Accent, CircleShape).then(if (day.date == today) Modifier.border(2.dp, Palette.Text, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Text("%.1f".format(day.km), style = numberStyle(13.sp, Palette.OnAccent)) }
                DayKind.Rest -> RestMark(34.dp)
                DayKind.Missed -> Box(Modifier.size(width = 10.dp, height = 2.dp).background(Palette.Muted))
                DayKind.Open -> Box(disc.border(2.dp, Palette.Accent, CircleShape))
                DayKind.Future -> Box(disc.border(1.dp, Palette.CardHigh, CircleShape))
                DayKind.BeforeStart -> {}
            }
        }
        if (day != null) Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

/** One column per week from the first entry to the goal week, with a dashed line at the average finished week. */
@Composable
private fun AllTimeColumns(a: AllTimeDistance) {
    Headline("%.1f km".format(a.totalKm)) {
        Text(
            a.since?.let { "${a.runs} ${if (a.runs == 1) "run" else "runs"} since ${dayMonth(it)}" } ?: "No runs yet",
            style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
        )
    }
    val top = maxOf(a.bestWeekKm, a.averageWeekKm ?: 0.0, 1.0)
    val barMax = 140.dp
    Box(Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = "Kilometres per week to the goal week" }) {
        // Drawn first so the bars and their labels sit on top of the average line.
        a.averageWeekKm?.let { avg ->
            Canvas(Modifier.matchParentSize()) {
                val y = size.height - barMax.toPx() * (avg / top).toFloat()
                drawLine(Palette.Muted, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            a.weeks.forEach { w ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val km = w.km
                    if (km == null) {
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.CardHigh))
                    } else {
                        Text(
                            "%.0f".format(km), Modifier.background(Palette.Card).padding(horizontal = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = if (w.current) Palette.Text else Palette.Muted, maxLines = 1,
                        )
                        Box(
                            Modifier.fillMaxWidth().height(barMax * (km / top).toFloat())
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (w.current) Palette.Accent else Palette.Accent.copy(alpha = 0.55f)),
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = Palette.CardHigh)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        a.weeks.forEach { w ->
            // The month's short name under the week holding its 1st.
            val first = (0L..6L).map { w.monday.plusDays(it) }.firstOrNull { it.dayOfMonth == 1 }
            Text(
                first?.month?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) ?: "",
                Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Best week", "%.1f km".format(a.bestWeekKm), Modifier.weight(1f))
        Tile("Average week", a.averageWeekKm?.let { "%.1f km".format(it) } ?: "–", Modifier.weight(1f))
        Tile("Weeks to go", "${a.weeksToGo}", Modifier.weight(1f))
    }
}

/**
 * Runs tab: every week as a spoke around a ring (longer = more km), clockwise from the first entry to the goal week.
 * The inner arc is how far through the plan today is; the white tick is the goal date.
 */
@Composable
fun WeekRingCard(a: AllTimeDistance) {
    SectionCard("Every week") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "Kilometres per week around a ring to the goal date" }) {
                val c = center
                // A wide track keeps the ring filling the square even when the spokes to come are short.
                val track = size.minDimension * 0.32f
                val inner = track + 12.dp.toPx()
                val reach = size.minDimension / 2 - inner - 6.dp.toPx()
                val top = maxOf(a.bestWeekKm, 1.0)
                fun at(f: Double, r: Float): Offset {
                    val angle = Math.toRadians(-90.0 + f * 360.0)
                    return Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat())
                }
                drawCircle(Palette.CardHigh, track, c, style = Stroke(4.dp.toPx()))
                drawArc(
                    Palette.Accent, -90f, (360 * a.progress).toFloat(), useCenter = false,
                    topLeft = Offset(c.x - track, c.y - track), size = Size(track * 2, track * 2),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(Palette.Text, 5.dp.toPx(), at(a.progress, track))
                val n = a.weeks.size
                a.weeks.forEachIndexed { i, w ->
                    val f = (i + 0.5) / n
                    val km = w.km
                    val (length, color) = when {
                        km == null -> 4.dp.toPx() to Palette.CardHigh
                        w.current -> maxOf(4.dp.toPx(), reach * (km / top).toFloat()) to Palette.Accent
                        else -> maxOf(4.dp.toPx(), reach * (km / top).toFloat()) to Palette.Accent.copy(alpha = 0.55f)
                    }
                    drawLine(color, at(f, inner), at(f, inner + length), 8.dp.toPx(), StrokeCap.Round)
                }
                drawLine(Palette.Text, at(a.goalAt, track - 8.dp.toPx()), at(a.goalAt, track + 8.dp.toPx()), 2.dp.toPx())
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.1f".format(a.totalKm), style = numberStyle(36.sp))
                Text(a.since?.let { "km since ${dayMonth(it)}" } ?: "km", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend("Week's km") { Box(Modifier.size(width = 16.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Accent)) }
            Legend("Through the plan") { Box(Modifier.size(width = 16.dp, height = 3.dp).background(Palette.Accent)) }
            Legend("Goal") { Box(Modifier.size(width = 2.dp, height = 12.dp).background(Palette.Text)) }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Background).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Text(value, style = numberStyle(20.sp))
    }
}

@Composable
private fun Legend(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}
