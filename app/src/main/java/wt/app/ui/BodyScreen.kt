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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.app.data.BodyCompEntity
import wt.core.io.BodyReading
import wt.core.summary.BodyMetric
import wt.core.summary.MetricSeries
import wt.core.summary.WeighInStatus
import wt.core.summary.weighInStatus
import java.time.LocalDate

/**
 * Body: the next weekly measurement (same morning as the weigh-in) with a scale-screenshot import, a big chart of
 * the chosen measure, a stat card per measure (weight, BMI, body fat, visceral fat, muscle) that picks the chart, and
 * every measurement in a folded log at the end.
 */
@Composable
fun BodyScreen(
    body: List<BodyCompEntity>,
    today: LocalDate,
    onSave: (BodyCompEntity) -> Unit,
    onDelete: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onScreenshot: (() -> Unit)? = null,
    weighInDay: Int = 1,
    weightDates: List<LocalDate> = emptyList(),
    metrics: List<MetricSeries> = emptyList(),
    initialMetric: BodyMetric = BodyMetric.Weight,
    logInitiallyOpen: Boolean = false,
) {
    // The measure shown in the big chart; tapping a stat card below switches it.
    var chosen by rememberSaveable { mutableStateOf(initialMetric) }
    var editing by remember { mutableStateOf<BodyCompEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val sorted = body.sortedBy { it.date }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                // A weight typed on Today also counts for the week; the card then says the scale numbers are missing.
                val measured = weighInStatus(sorted.map { it.date }, today, weighInDay)
                val status = weighInStatus(sorted.map { it.date } + weightDates, today, weighInDay)
                NextMeasurementCard(status, weightOnly = measured.doneOn == null && status.doneOn != null, today, onScreenshot, onAdd = { adding = true })
            }
            // Until there are two measurements to compare, say what the scale numbers are for.
            if (sorted.size < 2) item {
                SectionCard("What the scale numbers add") {
                    Text(
                        "Body fat, visceral fat and muscle show whether the weight you lose is fat, not muscle. " +
                            "Once a week, import a screenshot from your scale app or add the numbers by hand. " +
                            "Charts start after the second measurement.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                    )
                }
            }
            if (metrics.isNotEmpty()) {
                item { BodyChartCard(metrics.firstOrNull { it.metric == chosen } ?: metrics.first()) }
                item {
                    Text("Tap a measure to chart it", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(start = 4.dp))
                }
                items(metrics, key = { it.metric.name }) { m -> MetricCard(m, selected = m.metric == chosen) { chosen = m.metric } }
            }
            // Every measurement, newest first, folded away by default.
            if (sorted.isNotEmpty()) item {
                CollapsibleSection(
                    "Measurements",
                    "${sorted.size} ${if (sorted.size == 1) "entry" else "entries"} · latest ${dayMonth(sorted.last().date)}",
                    initiallyExpanded = logInitiallyOpen,
                ) {
                    sorted.asReversed().forEach { entry -> MeasurementCard(entry) { editing = entry } }
                }
            }
            item {
                Text(
                    "Losing fat while keeping muscle is the goal. Estimates, not medical advice.",
                    style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (adding) {
        BodyDialog(
            null, today, sorted.lastOrNull(), { adding = false }, { it, _ -> onSave(it); adding = false },
            onFromScreenshot = onScreenshot?.let { pick -> { adding = false; pick() } },
        )
    }
    editing?.let { e ->
        BodyDialog(
            e, today, null, { editing = null },
            onSave = { it, _ ->
                if (it.date != e.date) onDelete(e.date) // date changed: move the entry
                onSave(it)
                editing = null
            },
            onDelete = { onDelete(e.date); editing = null },
        )
    }
}

/**
 * The weekly measurement (same morning as the weigh-in, from the same scale). When it's due: a full card with the
 * scale-screenshot import as the main action. Otherwise one line with the next date and a small "Add".
 */
@Composable
private fun NextMeasurementCard(status: WeighInStatus, weightOnly: Boolean, today: LocalDate, onScreenshot: (() -> Unit)?, onAdd: () -> Unit) {
    // A phone that follows someone gets the one-line status, never the "measure now" card or Add.
    val readOnly = LocalReadOnly.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (status.due) 24.dp else 16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
    ) {
        if (status.due && !readOnly) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weekly measurement", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Text(if (status.next == today) "Today" else "Due since ${dayMonth(status.next)}", style = numberStyle(30.sp))
                Text(
                    "Same morning as your weigh-in. A scale screenshot fills the weight too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
                if (onScreenshot != null) PrimaryButton("Import scale screenshot", onScreenshot, Modifier.fillMaxWidth())
                TextButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Add by hand", color = Palette.Muted) }
            }
        } else {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        status.doneOn.let { done ->
                            when {
                                done == null -> "Next measurement"
                                weightOnly -> "Weight logged this week (${dayMonth(done)}) · no scale numbers"
                                else -> "Measured this week (${dayMonth(done)})"
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (status.doneOn != null) Palette.Accent else Palette.Muted,
                    )
                    Text("Next: ${longDay(status.next)}", style = MaterialTheme.typography.titleSmall)
                }
                // The form, which also offers the screenshot import, so values can be typed too.
                if (!readOnly) TextButton(onClick = onAdd) { Text("Add", color = Palette.Accent) }
            }
        }
    }
}

@Composable
private fun MeasurementCard(entry: BodyCompEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = !LocalReadOnly.current,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // Sits inside the Measurements section, so it uses the page colour to stand apart.
        colors = CardDefaults.cardColors(containerColor = Palette.Background, contentColor = Palette.Text),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(longDay(entry.date), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                Figure("%.1f%%".format(entry.fatPct), "body fat", Modifier.weight(1f))
                Figure("%.1f".format(entry.visceral), "visceral", Modifier.weight(1f))
                Figure("%.1f kg".format(entry.muscleKg), "muscle", Modifier.weight(1f))
            }
            val extra = listOfNotNull(
                entry.skeletalPct?.let { "Skeletal %.1f%%".format(it) },
                entry.leanKg?.let { "lean %.1f kg".format(it) },
                entry.bmrKcal?.let { "BMR %,.0f".format(it) },
            )
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        }
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = numberStyle(24.sp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

/**
 * Add or edit a measurement, or confirm one read from a screenshot ([prefill]). Only the screenshot version has a
 * weight field; [onSave] gets that weight, or null. When adding, [onFromScreenshot] shows a button that picks a
 * body-scale screenshot instead of typing.
 */
@Composable
fun BodyDialog(
    initial: BodyCompEntity?,
    today: LocalDate,
    previous: BodyCompEntity?,
    onDismiss: () -> Unit,
    onSave: (BodyCompEntity, Double?) -> Unit,
    onDelete: (() -> Unit)? = null,
    prefill: BodyReading? = null,
    onFromScreenshot: (() -> Unit)? = null,
) {
    // New entries start from the previous measurement so only changed values need typing.
    val base = initial ?: previous.takeIf { prefill == null }
    var date by remember { mutableStateOf(initial?.date ?: prefill?.date ?: today) }
    var fat by remember { mutableStateOf(fieldText(base?.fatPct ?: prefill?.fatPct)) }
    var visceral by remember { mutableStateOf(fieldText(base?.visceral ?: prefill?.visceral)) }
    var muscle by remember { mutableStateOf(fieldText(base?.muscleKg ?: prefill?.muscleKg)) }
    var skeletal by remember { mutableStateOf(fieldText(base?.skeletalPct ?: prefill?.skeletalPct)) }
    var lean by remember { mutableStateOf(fieldText(base?.leanKg ?: prefill?.leanKg)) }
    var bmr by remember { mutableStateOf(fieldText(base?.bmrKcal ?: prefill?.bmrKcal)) }
    var weight by remember { mutableStateOf(fieldText(prefill?.weightKg)) }
    val weightV = parseDecimal(weight)?.takeIf { it in 30.0..250.0 }
    val weightValid = weight.isBlank() || weightV != null

    val fatV = parseDecimal(fat)?.takeIf { it in 2.0..70.0 }
    val visceralV = parseDecimal(visceral)?.takeIf { it in 0.0..60.0 }
    val muscleV = parseDecimal(muscle)?.takeIf { it in 10.0..150.0 }
    fun optional(s: String) = if (s.isBlank()) true else parseDecimal(s) != null
    val valid = fatV != null && visceralV != null && muscleV != null && optional(skeletal) && optional(lean) && optional(bmr) && weightValid

    FormDialog(
        title = if (prefill != null) "Measurement from screenshot" else if (initial == null) "Add measurement" else "Edit measurement",
        onDismiss = onDismiss,
        confirmLabel = "Save measurement",
        confirmEnabled = valid,
        onConfirm = {
            onSave(BodyCompEntity(date, fatV!!, visceralV!!, muscleV!!, parseDecimal(skeletal), parseDecimal(lean), parseDecimal(bmr)), weightV)
        },
        onDelete = onDelete,
    ) {
        if (prefill != null) Text("Check the values before saving.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        if (initial == null && prefill == null && onFromScreenshot != null) ScreenshotButton("Import from a scale screenshot", onFromScreenshot)
        DateField("Date", date, { date = it }, Modifier.fillMaxWidth())
        if (prefill != null) {
            NumberField("Weight (optional)", weight, { weight = it }, Modifier.fillMaxWidth(), suffix = "kg", isError = !weightValid)
            if (prefill.weightEstimated) {
                Text(
                    "Weight isn't on the screenshot, so it's worked out from lean mass and body fat. " +
                        "Check it against the scale, or clear it to save only the measurement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Body fat", fat, { fat = it }, Modifier.weight(1f), suffix = "%", isError = fatV == null)
            NumberField("Visceral", visceral, { visceral = it }, Modifier.weight(1f), isError = visceralV == null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Muscle", muscle, { muscle = it }, Modifier.weight(1f), suffix = "kg", isError = muscleV == null)
            NumberField("Skeletal", skeletal, { skeletal = it }, Modifier.weight(1f), suffix = "%", isError = !optional(skeletal))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Lean mass", lean, { lean = it }, Modifier.weight(1f), suffix = "kg", isError = !optional(lean))
            NumberField("BMR", bmr, { bmr = it }, Modifier.weight(1f), suffix = "kcal", isError = !optional(bmr))
        }
    }
}
