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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import wt.app.data.RunEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import wt.core.io.RunReading
import wt.core.model.formatMinSec
import wt.core.summary.WeekSummary
import java.time.temporal.ChronoUnit
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
    programStart: LocalDate? = null,
) {
    var editing by remember { mutableStateOf<RunEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    val dated = runs.filter { it.date != null }
    val last28 = dated.filter { it.date!! > today.minusDays(28) }
    // Fastest pace over runs of at least 1 km, so a short sprint doesn't win.
    val fastest = dated.filter { it.km >= 1.0 }.minByOrNull { it.durationSec / it.km }
    val start = programStart ?: dated.minOfOrNull { it.date!! } ?: today
    val currentWeek = weekIndex(start, today)
    val groups = dated.sortedByDescending { it.date }.groupBy { weekIndex(start, it.date!!) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard("Last 28 days", trailing = "plan: 4–5 runs a week") {
                    Row(Modifier.fillMaxWidth()) {
                        SummaryFigure("Runs", "${last28.size}", Modifier.weight(1f))
                        SummaryFigure("Distance", "%.1f km".format(last28.sumOf { it.km }), Modifier.weight(1f))
                        SummaryFigure("Fastest pace", fastest?.let { formatMinSec(it.durationSec / it.km) } ?: "–", Modifier.weight(1f), Palette.Accent)
                    }
                    if (weeks.isNotEmpty()) KmBars(weeks)
                }
            }
            val undated = runs.filter { it.date == null }
            if (undated.isNotEmpty()) {
                item { GroupHeader("Date not set · tap a run to set it") }
                items(undated, key = { it.id }) { run -> RunCard(run, fastest = false) { editing = run } }
            }
            groups.forEach { (week, list) ->
                item(key = "week-$week") {
                    val first = start.plusDays((week - 1) * 7L)
                    GroupHeader(
                        if (week == currentWeek) "This week"
                        else "Week $week · ${dayMonth(first)} – ${dayMonth(first.plusDays(6))} · %.1f km".format(list.sumOf { it.km }),
                    )
                }
                items(list, key = { it.id }) { run -> RunCard(run, fastest = run.id == fastest?.id) { editing = run } }
            }
            if (runs.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No runs yet.", color = Palette.Muted)
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

    if (adding) RunDialog(null, today, onDismiss = { adding = false }, onSave = { onSave(it); adding = false })
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

/** 1-based program week of [date], counted from [start] (e.g. Thursday to Wednesday). */
private fun weekIndex(start: LocalDate, date: LocalDate): Int =
    (ChronoUnit.DAYS.between(start, date).coerceAtLeast(0) / 7 + 1).toInt()

@Composable
private fun GroupHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
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
                if (w == newest) "This week" else "Wk ${w.index}",
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

/** Add or edit a run, or confirm one read from a screenshot ([prefill]). Pace is computed from distance and time. */
@Composable
fun RunDialog(
    initial: RunEntity?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (RunEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
    prefill: RunReading? = null,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prefill != null) "Run from screenshot" else if (initial == null) "Add run" else "Edit run") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prefill != null) {
                    val notes = listOf("Check the values, and set the date if the run wasn't today.") + prefill.notes
                    Text(notes.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                DateField("Date", date, { date = it }, Modifier.fillMaxWidth(), isError = date == null)
                NumberField("Distance", km, { km = it }, Modifier.fillMaxWidth(), suffix = "km", isError = km.isNotBlank() && kmValue == null)
                NumberField("Time (mm:ss)", time, { time = it }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Text, isError = time.isNotBlank() && seconds == null)
                NumberField("Calories (optional)", kcalText, { kcalText = it }, Modifier.fillMaxWidth(), suffix = "kcal", isError = !kcalValid)
                if (kmValue != null && seconds != null) Text("Pace ${pace(seconds / kmValue)}")
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        (initial ?: RunEntity(date = date, km = 0.0, durationSec = 0, source = if (prefill != null) "strava" else "manual"))
                            .copy(date = date, km = kmValue!!, durationSec = seconds!!, kcal = kcalValue),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
