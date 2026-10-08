package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wt.app.data.ProfileEntity
import wt.core.Safety
import wt.core.model.Checkpoint
import wt.core.plan.RebaselineResult
import wt.core.plan.rebaseline
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

private data class EditablePoint(val date: LocalDate?, val kg: String)

@Composable
fun PlanScreen(
    state: UiState,
    onSaveCheckpoints: (List<Checkpoint>) -> Unit,
    onApplyRebaseline: (List<Checkpoint>) -> Unit,
    onSaveProfile: (ProfileEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RebaselineCard(state, onApplyRebaseline)
        CheckpointEditor(state.checkpoints, onSaveCheckpoints)
        EnergySettings(state.profile, onSaveProfile)
        SectionCard("Plan history") {
            state.plans.forEach { p ->
                Text(
                    "${p.label} · created ${p.createdAt}" + if (p.active) " · active" else "",
                    fontWeight = if (p.active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Disclaimer()
    }
}

@Composable
private fun RebaselineCard(state: UiState, onApply: (List<Checkpoint>) -> Unit) {
    val d = state.dashboard
    val preview = d.rebaselinePreview()
    var open by remember { mutableStateOf(false) }
    SectionCard("Re-baseline") {
        Text(
            "Starts a new planned line from your current weight, keeping the goal " +
                "(${kg(d.plan.goal.kg)} on ${d.plan.goal.date}). The old plan stays in the history.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (preview == null) {
            Text("Log a weight first.")
        } else {
            preview.requiredKgPerWeek?.let { RateLine(preview, it) }
            Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Re-baseline from today") }
        }
    }
    if (open && preview != null) {
        // A gentler alternative: same goal weight at 0.5 kg/week.
        val alternativeDate = d.asOf.plusDays(ceil((preview.startKg - d.plan.goal.kg) / 0.5 * 7).toLong().coerceAtLeast(7))
        val alternative = rebaseline(d.weights, d.asOf, alternativeDate, d.plan.goal.kg)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("New plan from ${d.asOf}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Start: ${kg(preview.startKg)} (" + (if (preview.fromTrend) "trend weight" else "latest weigh-in; not enough data for a trend") + ")",
                    )
                    val rate = preview.requiredKgPerWeek
                    if (rate == null) {
                        Text("The goal date has passed. Use the slower plan below or edit the last checkpoint.")
                    } else {
                        RateLine(preview, rate)
                    }
                    if (alternative.requiredKgPerWeek != null && alternativeDate != d.plan.goal.date) {
                        Text(
                            "A steady 0.5 kg/week instead reaches ${kg(d.plan.goal.kg)} on $alternativeDate.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (preview.requiredKgPerWeek != null && !preview.exceedsSafeMax) {
                        TextButton(onClick = { onApply(preview.checkpoints); open = false }) { Text("Keep goal date") }
                    }
                    if (alternativeDate != d.plan.goal.date) {
                        TextButton(onClick = { onApply(alternative.checkpoints); open = false }) { Text("Use $alternativeDate") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RateLine(r: RebaselineResult, rate: Double) {
    val c = LocalSeriesColors.current
    Text(
        "Needs %.2f kg/week".format(rate) + when {
            r.exceedsSafeMax -> ": above the ${Safety.MAX_LOSS_KG_PER_WEEK} kg/week safety limit. Choose a later goal date."
            r.unrealistic -> ": unrealistic (above ${Safety.UNREALISTIC_KG_PER_WEEK} kg/week). Consider a later goal date."
            else -> ": realistic."
        },
        color = if (r.unrealistic) c.warn else c.good,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun CheckpointEditor(checkpoints: List<Checkpoint>, onSave: (List<Checkpoint>) -> Unit) {
    var points by remember(checkpoints) { mutableStateOf(checkpoints.map { EditablePoint(it.date, fieldText(it.kg)) }) }
    val parsed = points.map { p -> p.date?.let { d -> parseDecimal(p.kg)?.takeIf { it in 30.0..250.0 }?.let { Checkpoint(d, it) } } }
    val complete = parsed.filterNotNull()
    val valid = complete.size == points.size && complete.size >= 2 && complete.map { it.date }.toSet().size == complete.size
    val sorted = complete.sortedBy { it.date }
    val c = LocalSeriesColors.current

    SectionCard("Planned checkpoints") {
        Text("The last checkpoint is the goal. Dates must be different.", style = MaterialTheme.typography.bodySmall)
        points.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DateField("Date", p.date, { d -> points = points.toMutableList().also { it[i] = p.copy(date = d) } }, Modifier.weight(1.4f))
                NumberField(
                    "kg", p.kg, { v -> points = points.toMutableList().also { it[i] = p.copy(kg = v) } }, Modifier.weight(1f),
                    isError = parsed[i] == null,
                )
                IconButton(onClick = { points = points.toMutableList().also { it.removeAt(i) } }, enabled = points.size > 2) {
                    Icon(Icons.Default.Close, "Remove checkpoint")
                }
            }
        }
        // Weekly rate of each segment, so steep stretches stand out.
        if (valid) {
            sorted.zipWithNext().forEach { (a, b) ->
                val weeks = ChronoUnit.DAYS.between(a.date, b.date) / 7.0
                val rate = (a.kg - b.kg) / weeks
                Text(
                    "${short(a.date)} → ${short(b.date)}: %.2f kg/week".format(rate),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rate > Safety.UNREALISTIC_KG_PER_WEEK) c.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val last = sorted.lastOrNull()
                points = points + EditablePoint(last?.date?.plusDays(14), fieldText(last?.kg))
            }) { Text("Add") }
            OutlinedButton(onClick = { points = checkpoints.map { EditablePoint(it.date, fieldText(it.kg)) } }) { Text("Reset") }
            Button(onClick = { onSave(sorted) }, enabled = valid && sorted != checkpoints) { Text("Save") }
        }
    }
}

@Composable
private fun EnergySettings(profile: ProfileEntity, onSave: (ProfileEntity) -> Unit) {
    var bmr by remember(profile) { mutableStateOf(fieldText(profile.bmrKcal)) }
    var factor by remember(profile) { mutableStateOf(fieldText(profile.activityFactor)) }
    var deficit by remember(profile) { mutableStateOf(fieldText(profile.plannedFoodDeficitKcal)) }
    val bmrV = parseDecimal(bmr)?.takeIf { it in 800.0..4000.0 }
    val factorV = parseDecimal(factor)?.takeIf { it in 1.0..2.5 }
    val deficitV = parseDecimal(deficit)?.takeIf { it in 0.0..1000.0 }

    SectionCard("Energy estimate settings") {
        Text(
            "Maintenance ≈ BMR × activity factor (daily life, not running). Running is added from your run log.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("BMR", bmr, { bmr = it }, Modifier.weight(1f), suffix = "kcal", isError = bmrV == null)
            NumberField("Activity", factor, { factor = it }, Modifier.weight(1f), suffix = "×", isError = factorV == null)
        }
        NumberField("Planned food deficit", deficit, { deficit = it }, Modifier.fillMaxWidth(), suffix = "kcal/day", isError = deficitV == null)
        Button(
            onClick = { onSave(profile.copy(bmrKcal = bmrV!!, activityFactor = factorV!!, plannedFoodDeficitKcal = deficitV!!)) },
            enabled = bmrV != null && factorV != null && deficitV != null,
        ) { Text("Save") }
    }
}
