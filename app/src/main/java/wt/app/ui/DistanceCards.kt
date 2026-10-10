package wt.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.core.summary.DayDistance
import wt.core.summary.DayKind
import wt.core.summary.DistanceWeek
import wt.core.summary.distanceSpan
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Distance per day over the last 2 or 4 weeks: last week against this week, a bar per day (km on top in the
 * 2-week view), then the longest day, km per run and run days so far.
 */
@Composable
fun DistanceCard(weeks: List<DistanceWeek>, today: LocalDate) {
    if (weeks.isEmpty()) return
    var span by rememberSaveable { mutableIntStateOf(2) }
    val s = distanceSpan(weeks, span)
    SectionCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Distance", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Background).padding(3.dp)) {
                listOf(2, 4).forEach { w ->
                    val on = span == w
                    TextButton(
                        onClick = { span = w },
                        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Palette.CardHigh else Palette.Background),
                    ) { Text("$w weeks", color = if (on) Palette.Text else Palette.Muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }
        }
        Row {
            Figure("Last week", s.lastWeekKm?.let { "%.1f km".format(it) } ?: "–", Palette.Text, Modifier.weight(1f))
            Figure("This week", "%.1f km".format(s.thisWeekKm), Palette.Accent, Modifier.weight(1f))
        }
        val thisWeek = weeks.last().monday
        val top = s.longestKm.coerceAtLeast(1.0)
        Row(
            Modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = "Kilometres per day" },
            horizontalArrangement = Arrangement.spacedBy(if (span == 2) 4.dp else 2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            s.days.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (day.kind) {
                        DayKind.Run -> {
                            if (span == 2) Text("%.1f".format(day.km), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                            Box(
                                Modifier.fillMaxWidth().height((96 * day.km / top).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    // Earlier weeks dimmer, this week bright.
                                    .background(if (day.date >= thisWeek) Palette.Accent else Palette.Accent.copy(alpha = 0.45f)),
                            )
                        }
                        DayKind.Rest -> RestMark(if (span == 2) 18.dp else 10.dp, Modifier.padding(bottom = 4.dp))
                        DayKind.Missed -> Box(Modifier.padding(bottom = 6.dp).size(width = 8.dp, height = 2.dp).background(Palette.Muted))
                        DayKind.Open, DayKind.Future -> Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.CardHigh))
                        DayKind.BeforeStart -> {}
                    }
                }
            }
        }
        HorizontalDivider(color = Palette.CardHigh)
        if (span == 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                s.days.forEach { day ->
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                        Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.date == today) Palette.Text else Palette.Muted,
                        fontWeight = if (day.date == today) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        } else {
            Row {
                weeks.takeLast(span).forEach { w ->
                    Text(dayMonth(w.monday), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Longest", "%.1f km".format(s.longestKm), Modifier.weight(1f))
            Tile("Per run", s.averageRunKm?.let { "%.1f km".format(it) } ?: "–", Modifier.weight(1f))
            Tile("Run days", "${s.runDays} of ${s.daysSoFar}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Text(value, style = numberStyle(26.sp, color))
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Background).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Text(value, style = numberStyle(20.sp))
    }
}

/**
 * Every day since the first entry, newest week on top: a run is an orange disc with its km, a rest day the moon,
 * a missed day a dash, today an orange ring until something is logged. Each row ends with the week's km.
 */
@Composable
fun EveryDayCard(weeks: List<DistanceWeek>) {
    if (weeks.isEmpty()) return
    val days = weeks.flatMap { it.days }
    val runs = days.sumOf { it.runs }
    val rest = days.count { it.kind == DayKind.Rest }
    CollapsibleSection(
        "Every day",
        "%.1f km · %d %s · %d %s".format(days.sumOf { it.km }, runs, if (runs == 1) "run" else "runs", rest, if (rest == 1) "rest day" else "rest days"),
        initiallyExpanded = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(48.dp))
            java.time.DayOfWeek.entries.forEach {
                Text(it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
            Text("Week", Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
        weeks.asReversed().forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayMonth(w.monday), Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                w.days.forEach { DayCell(it, Modifier.weight(1f)) }
                Text("%.1f".format(w.km), Modifier.width(40.dp), textAlign = TextAlign.End, style = numberStyle(16.sp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend("Run (km)") { Box(Modifier.size(14.dp).background(Palette.Accent, CircleShape)) }
            Legend("Rest") { RestMark(14.dp) }
            Legend("Missed") { Box(Modifier.size(width = 10.dp, height = 2.dp).background(Palette.Muted)) }
        }
    }
}

@Composable
private fun DayCell(day: DayDistance, modifier: Modifier) {
    Box(modifier.height(44.dp), contentAlignment = Alignment.Center) {
        val disc = Modifier.size(32.dp)
        when (day.kind) {
            DayKind.Run -> Box(disc.background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Text("%.1f".format(day.km), style = numberStyle(13.sp, Palette.OnAccent))
            }
            DayKind.Rest -> RestMark(32.dp)
            DayKind.Missed -> Box(Modifier.size(width = 10.dp, height = 2.dp).background(Palette.Muted))
            DayKind.Open -> Box(disc.border(2.dp, Palette.Accent, CircleShape))
            DayKind.Future -> Box(disc.background(Palette.Card.copy(alpha = 0.6f), CircleShape).border(1.dp, Palette.CardHigh, CircleShape))
            DayKind.BeforeStart -> {}
        }
    }
}

@Composable
private fun Legend(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}
