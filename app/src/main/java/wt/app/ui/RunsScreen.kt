package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import wt.app.data.RunEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import wt.core.io.RunReading
import wt.core.model.formatMinSec
import wt.core.summary.WeekSummary
import wt.core.summary.programWeekIndex
import wt.core.summary.weekStart
import java.time.LocalDate

@Composable
fun RunsScreen(
    runs: List<RunEntity>,
    today: LocalDate,
    onSave: (RunEntity) -> Unit,
    onDelete: (RunEntity) -> Unit,
    onImportStrava: () -> Unit,
    modifier: Modifier = Modifier,
    weeks: List<WeekSummary> = emptyList(),
    week1: LocalDate? = null,
    onScreenshot: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf<RunEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Weeks before this one start collapsed to one line; tapping toggles them.
    val toggled = remember { mutableStateMapOf<LocalDate, Boolean>() }

    val dated = runs.filter { it.date != null }
    val last28 = dated.filter { it.date!! > today.minusDays(28) }
    // Fastest pace over runs of at least 1 km, so a short sprint doesn't win.
    val fastest = dated.filter { it.km >= 1.0 }.minByOrNull { it.durationSec / it.km }
    val anchor = week1 ?: dated.minOfOrNull { it.date!! } ?: today
    val thisWeek = weekStart(today)
    val groups = dated.sortedByDescending { it.date }.groupBy { weekStart(it.date!!) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard("Last 28 days") {
                    Row(Modifier.fillMaxWidth()) {
                        SummaryFigure("Runs", "${last28.size}", Modifier.weight(1f))
                        SummaryFigure("Distance", "%.1f km".format(last28.sumOf { it.km }), Modifier.weight(1f))
                        SummaryFigure("Fastest pace", fastest?.let { formatMinSec(it.durationSec / it.km) } ?: "–", Modifier.weight(1f), Palette.Accent)
                    }
                    if (weeks.isNotEmpty() && dated.isNotEmpty()) KmBars(weeks) // no empty chart before the first run
                }
            }
            val undated = runs.filter { it.date == null }
            if (undated.isNotEmpty()) {
                item { GroupHeader("Date not set · tap a run to set it") }
                items(undated, key = { it.id }) { run -> RunCard(run, fastest = false) { editing = run } }
            }
            groups.forEach { (monday, list) ->
                val index = programWeekIndex(anchor, monday)
                val title = when {
                    monday == thisWeek -> "This week"
                    index < 1 -> "Before the plan · ${weekRange(monday)}"
                    else -> "Week $index · ${weekRange(monday)}"
                }
                val km = "%.1f km".format(list.sumOf { it.km })
                val recent = monday == thisWeek // only this week starts open
                val expanded = toggled[monday] ?: recent
                item(key = "week-$monday") {
                    when {
                        recent -> GroupHeader("$title · $km")
                        expanded -> GroupHeader("$title · $km", expanded = true) { toggled[monday] = false }
                        else -> CollapsedWeek(title, list, km) { toggled[monday] = true }
                    }
                }
                if (expanded) items(list, key = { it.id }) { run -> RunCard(run, fastest = run.id == fastest?.id) { editing = run } }
            }
            if (runs.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No runs yet. Tap Add run, or import a Strava file.", color = Palette.Muted)
                        TextButton(onClick = onImportStrava) { Text("Import from Strava") }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add run", style = MaterialTheme.typography.labelLarge) },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        RunDialog(
            null, today, onDismiss = { adding = false }, onSave = { onSave(it); adding = false },
            onFromScreenshot = onScreenshot?.let { pick -> { adding = false; pick() } },
            onImportCsv = { adding = false; onImportStrava() },
        )
    }
    editing?.let { run ->
        RunDialog(
            initial = run,
            defaultDate = today,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = { onDelete(run); editing = null },
        )
    }
}

/** Muted label above a group of runs; with [onClick] it shows a chevron and collapses the group. */
@Composable
private fun GroupHeader(text: String, expanded: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.heightIn(min = 44.dp).clickable(onClick = onClick) else Modifier)
            .padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.weight(1f))
        if (onClick != null) {
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", tint = Palette.Muted)
        }
    }
}

/** An older week as one line: dates, run count, distance and best pace. Tap to show its runs. */
@Composable
private fun CollapsedWeek(title: String, runs: List<RunEntity>, km: String, onClick: () -> Unit) {
    val best = runs.filter { it.km >= 1.0 }.minOfOrNull { it.durationSec / it.km }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull("${runs.size} run" + if (runs.size == 1) "" else "s", km, best?.let { "best ${formatMinSec(it)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Muted,
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, "Expand", tint = Palette.Muted)
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Text(value, style = numberStyle(30.sp), color = color)
    }
}

/** Last five program weeks as km bars; the current week in orange. */
@Composable
private fun KmBars(weeks: List<WeekSummary>) {
    val shown = weeks.take(5).reversed() // weekly is newest first
    val maxKm = shown.maxOf { it.km }.coerceAtLeast(1.0)
    val newest = shown.last()
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        shown.forEach { w ->
            val current = w == newest
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("%.1f".format(w.km), style = MaterialTheme.typography.labelSmall, color = if (current) Palette.Accent else Palette.Muted)
                Box(
                    Modifier.padding(top = 6.dp).fillMaxWidth()
                        .height((70 * (w.km / maxKm)).dp.coerceAtLeast(4.dp))
                        .background(if (current) Palette.Accent else Palette.CardHigh, RoundedCornerShape(6.dp)),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { w ->
            Text(
                if (w == newest) "This week" else weekLabel(w),
                style = MaterialTheme.typography.labelSmall,
                color = if (w == newest) Palette.Text else Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RunCard(run: RunEntity, fastest: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    run.date?.let(::longDay) ?: "Date not set",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (run.date == null) Palette.Warn else Palette.Text,
                    modifier = Modifier.weight(1f),
                )
                if (fastest) Pill("Fastest yet", filled = true)
                else if (run.source != "manual") Pill(run.source.replaceFirstChar { it.uppercase() })
            }
            Row(Modifier.fillMaxWidth()) {
                RunFigure("%.2f".format(run.km), "km", "distance", Modifier.weight(1f))
                RunFigure(duration(run.durationSec), null, "time", Modifier.weight(1f))
                RunFigure(formatMinSec(run.durationSec / run.km), null, "per km", Modifier.weight(1f), if (fastest) Palette.Accent else Palette.Text)
            }
            run.kcal?.let { Text("%,.0f kcal".format(it), style = MaterialTheme.typography.labelMedium, color = Palette.Muted) }
        }
    }
}

@Composable
private fun RunFigure(value: String, unit: String?, label: String, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = numberStyle(26.sp), color = color)
            if (unit != null) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

/**
 * Add or edit a run, or confirm one read from a screenshot ([prefill]). Pace is computed from distance and time.
 * When adding, [onFromScreenshot] and [onImportCsv] show buttons that read a Strava screenshot or Strava's
 * activities.csv instead of typing.
 */
@Composable
fun RunDialog(
    initial: RunEntity?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (RunEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
    prefill: RunReading? = null,
    onFromScreenshot: (() -> Unit)? = null,
    onImportCsv: (() -> Unit)? = null,
) {
    var date by remember { mutableStateOf(if (initial == null) defaultDate else initial.date) }
    var km by remember { mutableStateOf(fieldText(initial?.km ?: prefill?.km)) }
    var time by remember { mutableStateOf((initial?.durationSec ?: prefill?.durationSec)?.let(::duration) ?: "") }
    var kcalText by remember { mutableStateOf(fieldText(initial?.kcal ?: prefill?.kcal)) }

    val kmValue = parseDecimal(km)?.takeIf { it in 0.1..100.0 }
    val seconds = parseDuration(time)
    val kcalValue = parseDecimal(kcalText)
    val kcalValid = kcalText.isBlank() || (kcalValue != null && kcalValue in 0.0..5000.0)
    val valid = date != null && kmValue != null && seconds != null && kcalValid

    FormDialog(
        title = if (prefill != null) "Run from screenshot" else if (initial == null) "Add run" else "Edit run",
        onDismiss = onDismiss,
        confirmLabel = "Save run",
        confirmEnabled = valid,
        onConfirm = {
            onSave(
                (initial ?: RunEntity(date = date, km = 0.0, durationSec = 0, source = if (prefill != null) "strava" else "manual"))
                    .copy(date = date, km = kmValue!!, durationSec = seconds!!, kcal = kcalValue),
            )
        },
        onDelete = onDelete,
    ) {
        if (prefill != null) {
            val notes = listOf("Check the values, and set the date if the run wasn't today.") + prefill.notes
            Text(notes.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        if (initial == null && prefill == null) {
            onFromScreenshot?.let { ScreenshotButton("Import from a Strava screenshot", it) }
            onImportCsv?.let { ScreenshotButton("Import Strava activities.csv", it) }
        }
        DateField("Date", date, { date = it }, Modifier.fillMaxWidth(), isError = date == null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Distance", km, { km = it }, Modifier.weight(1f), suffix = "km", isError = km.isNotBlank() && kmValue == null)
            NumberField("Time (mm:ss)", time, { time = it }, Modifier.weight(1f), keyboardType = KeyboardType.Text, isError = time.isNotBlank() && seconds == null)
        }
        NumberField("Calories (optional)", kcalText, { kcalText = it }, Modifier.fillMaxWidth(), suffix = "kcal", isError = !kcalValid)
        if (kmValue != null && seconds != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Pace ", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Text(pace(seconds / kmValue), style = numberStyle(22.sp), color = Palette.Accent)
            }
        }
    }
}
