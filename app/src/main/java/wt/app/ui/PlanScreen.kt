package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RebaselineCard(state, onApplyRebaseline)
        CheckpointEditor(state.checkpoints, onSaveCheckpoints)
        EnergySettings(state.profile, onSaveProfile)
        SectionCard("Plan history") {
            state.plans.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.label, style = MaterialTheme.typography.titleSmall)
                        Text("Created ${dayMonthYear(p.createdAt)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    }
                    if (p.active) Pill("Active", filled = true)
                }
            }
        }
        Disclaimer()
    }
}

/** "Realistic", "Ambitious" (above 0.7 kg/week) or "Not allowed" (above the 1 kg/week cap). */
@Composable
private fun RateStatus(r: RebaselineResult) = when {
    r.exceedsSafeMax -> Pill("Not allowed", textColor = Palette.Error)
    r.unrealistic -> Pill("Ambitious", textColor = Palette.Warn)
    else -> Pill("Realistic", filled = true)
}

private fun rateAdvice(r: RebaselineResult): String = when {
    r.exceedsSafeMax -> "Above the ${Safety.MAX_LOSS_KG_PER_WEEK} kg/week safety limit. Choose a later goal date."
    r.unrealistic -> "Above ${Safety.UNREALISTIC_KG_PER_WEEK} kg/week is hard to keep up. Consider a later goal date."
    else -> "A pace you can keep up."
}

@Composable
private fun RebaselineCard(state: UiState, onApply: (List<Checkpoint>) -> Unit) {
    val d = state.dashboard
    val preview = d.rebaselinePreview()
    var open by remember { mutableStateOf(false) }
    SectionCard("Re-baseline", trailing = "goal ${kg(d.plan.goal.kg)} · ${dayMonth(d.plan.goal.date)}") {
        val rate = preview?.requiredKgPerWeek
        when {
            preview == null -> Text("Log a weight first.", color = Palette.Muted)
            rate == null -> Text("The goal date has passed. Re-baseline to a later date.", color = Palette.Muted)
            else -> {
                Text("Needed from today", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text("%.2f".format(rate), style = numberStyle(44.sp))
                        Text(" kg/week", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    RateStatus(preview)
                }
                Text(rateAdvice(preview), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
        Text(
            "Starts a new planned line from your current weight to the same goal. The old plan stays in the history.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
        if (preview != null) PrimaryButton("Re-baseline from today", { open = true }, Modifier.fillMaxWidth())
    }
    if (open && preview != null) {
        // A gentler alternative: same goal weight at 0.5 kg/week.
        val alternativeDate = d.asOf.plusDays(ceil((preview.startKg - d.plan.goal.kg) / 0.5 * 7).toLong().coerceAtLeast(7))
        val alternative = rebaseline(d.weights, d.asOf, alternativeDate, d.plan.goal.kg)
        val keepAllowed = preview.requiredKgPerWeek != null && !preview.exceedsSafeMax
        val offerAlternative = alternativeDate != d.plan.goal.date
        FormDialog(
            title = "New plan from ${dayMonth(d.asOf)}",
            onDismiss = { open = false },
            confirmLabel = if (keepAllowed) "Keep goal date (${dayMonth(d.plan.goal.date)})" else "Use ${dayMonth(alternativeDate)}",
            onConfirm = { onApply(if (keepAllowed) preview.checkpoints else alternative.checkpoints); open = false },
            confirmEnabled = keepAllowed || offerAlternative,
        ) {
            Text(
                "Start: ${kg(preview.startKg)} (" + (if (preview.fromTrend) "trend weight" else "latest weigh-in; not enough data for a trend") + ")",
                color = Palette.Muted,
            )
            preview.requiredKgPerWeek?.let { rate ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("%.2f kg/week".format(rate), style = numberStyle(26.sp))
                    RateStatus(preview)
                }
            }
            if (offerAlternative && alternative.requiredKgPerWeek != null) {
                Text(
                    "A steady 0.5 kg/week instead reaches ${kg(d.plan.goal.kg)} on ${dayMonthYear(alternativeDate)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
                if (keepAllowed) {
                    SecondaryButton("Use ${dayMonth(alternativeDate)} instead", { onApply(alternative.checkpoints); open = false }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CheckpointEditor(checkpoints: List<Checkpoint>, onSave: (List<Checkpoint>) -> Unit) {
    var points by remember(checkpoints) { mutableStateOf(checkpoints.map { EditablePoint(it.date, fieldText(it.kg)) }) }
    val parsed = points.map { p -> p.date?.let { d -> parseDecimal(p.kg)?.takeIf { it in 30.0..250.0 }?.let { Checkpoint(d, it) } } }
    val complete = parsed.filterNotNull()
    val valid = complete.size == points.size && complete.size >= 2 && complete.map { it.date }.toSet().size == complete.size
    val sorted = complete.sortedBy { it.date }

    SectionCard("Planned checkpoints", trailing = "the last one is the goal") {
        points.forEachIndexed { i, p ->
            val goal = i == points.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(if (goal) "Goal date" else "Date", p.date, { d -> points = points.toMutableList().also { it[i] = p.copy(date = d) } }, Modifier.weight(1.5f), withWeekday = false)
                NumberField(
                    if (goal) "Goal" else "Weight", p.kg, { v -> points = points.toMutableList().also { it[i] = p.copy(kg = v) } }, Modifier.weight(1f),
                    suffix = "kg", isError = parsed[i] == null,
                )
                IconButton(onClick = { points = points.toMutableList().also { it.removeAt(i) } }, enabled = points.size > 2, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Close, "Remove checkpoint", tint = if (points.size > 2) Palette.Muted else Palette.CardHigh)
                }
            }
            // Weekly rate to the next checkpoint, so steep stretches stand out.
            val a = parsed[i]
            val b = parsed.getOrNull(i + 1)
            if (a != null && b != null && b.date > a.date) {
                val rate = (a.kg - b.kg) / (ChronoUnit.DAYS.between(a.date, b.date) / 7.0)
                Text(
                    "↓ %.2f kg/week".format(rate),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rate > Safety.UNREALISTIC_KG_PER_WEEK) Palette.Warn else Palette.Muted,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
        if (!valid && complete.size == points.size) {
            Text("Dates must be different.", style = MaterialTheme.typography.bodySmall, color = Palette.Error)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Add checkpoint", {
                val last = sorted.lastOrNull()
                points = points + EditablePoint(last?.date?.plusDays(14), fieldText(last?.kg))
            })
            TextButton(onClick = { points = checkpoints.map { EditablePoint(it.date, fieldText(it.kg)) } }, modifier = Modifier.height(48.dp)) {
                Text("Reset", color = Palette.Muted)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("Save", { onSave(sorted) }, enabled = valid && sorted != checkpoints)
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
    val changed = bmrV != profile.bmrKcal || factorV != profile.activityFactor || deficitV != profile.plannedFoodDeficitKcal

    SectionCard("Energy estimate") {
        Text(
            "Maintenance ≈ BMR × activity factor (daily life, not running). Running is added from your run log.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("BMR", bmr, { bmr = it }, Modifier.weight(1f), suffix = "kcal", isError = bmrV == null)
            NumberField("Activity factor", factor, { factor = it }, Modifier.weight(1f), suffix = "×", isError = factorV == null)
        }
        NumberField("Planned food deficit", deficit, { deficit = it }, Modifier.fillMaxWidth(), suffix = "kcal/day", isError = deficitV == null)
        Row {
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                "Save",
                { onSave(profile.copy(bmrKcal = bmrV!!, activityFactor = factorV!!, plannedFoodDeficitKcal = deficitV!!)) },
                enabled = bmrV != null && factorV != null && deficitV != null && changed,
            )
        }
    }
}
