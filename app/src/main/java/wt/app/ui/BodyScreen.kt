package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.app.chart.MiniLineChart
import wt.app.data.BodyCompEntity
import wt.core.io.BodyReading
import java.time.LocalDate

/** Body-scale measurements (weekly or monthly): change tiles, fat and visceral trends, and the entries. */
@Composable
fun BodyScreen(
    body: List<BodyCompEntity>,
    today: LocalDate,
    onSave: (BodyCompEntity) -> Unit,
    onDelete: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<BodyCompEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val sorted = body.sortedBy { it.date }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sorted.isNotEmpty()) item {
                val first = sorted.first()
                val last = sorted.last()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChangeTile("Body fat", "%.1f%%".format(last.fatPct), last.fatPct - first.fatPct, "%+.1f", Modifier.weight(1f))
                        ChangeTile("Visceral", "%.1f".format(last.visceral), last.visceral - first.visceral, "%+.1f", Modifier.weight(1f))
                        // Muscle going down is the one change to watch, so it shows amber.
                        ChangeTile("Muscle", "%.1f".format(last.muscleKg), last.muscleKg - first.muscleKg, "%+.1f kg", Modifier.weight(1f), lowerIsBetter = false)
                    }
                    if (sorted.size >= 2) {
                        Text(
                            "Change since ${dayMonth(first.date)}. Losing fat while keeping muscle is the goal.",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
            if (sorted.size >= 2) {
                item {
                    SectionCard("Body fat", trailing = "%.1f → %.1f%%".format(sorted.first().fatPct, sorted.last().fatPct)) {
                        MiniLineChart(sorted.map { it.date to it.fatPct }, Palette.Accent, Modifier.fillMaxWidth().height(110.dp), fill = true)
                    }
                }
                item {
                    SectionCard("Visceral fat", trailing = "%.1f → %.1f".format(sorted.first().visceral, sorted.last().visceral)) {
                        MiniLineChart(sorted.map { it.date to it.visceral }, Palette.Planned, Modifier.fillMaxWidth().height(110.dp))
                    }
                }
            }
            if (sorted.isNotEmpty()) item {
                Text("Measurements", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            items(sorted.asReversed(), key = { it.date.toString() }) { entry -> MeasurementCard(entry) { editing = entry } }
            if (sorted.isEmpty()) item { Text("No measurements yet. Add one from your body scale.", color = Palette.Muted) }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add measurement", style = MaterialTheme.typography.labelLarge) },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) BodyDialog(null, today, sorted.lastOrNull(), { adding = false }, { it, _ -> onSave(it); adding = false })
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

/** Latest value with its change since the first measurement; good changes in orange, bad in amber. */
@Composable
private fun ChangeTile(label: String, value: String, change: Double, changeFormat: String, modifier: Modifier, lowerIsBetter: Boolean = true) {
    val good = if (lowerIsBetter) change <= 0 else change >= 0
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(value, style = numberStyle(28.sp))
            Text(changeFormat.format(change), style = MaterialTheme.typography.labelMedium, color = if (good) Palette.Accent else Palette.Warn)
        }
    }
}

@Composable
private fun MeasurementCard(entry: BodyCompEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
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
 * weight field; [onSave] gets that weight, or null.
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prefill != null) "Measurement from screenshot" else if (initial == null) "Add measurement" else "Edit measurement") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prefill != null) Text("Check the values before saving.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
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
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(BodyCompEntity(date, fatV!!, visceralV!!, muscleV!!, parseDecimal(skeletal), parseDecimal(lean), parseDecimal(bmr)), weightV)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
