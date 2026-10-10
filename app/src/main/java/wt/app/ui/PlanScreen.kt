package wt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.app.data.PlanEntity
import wt.app.data.ProfileEntity
import wt.core.Safety
import wt.core.model.Checkpoint
import wt.core.plan.RebaselineResult
import wt.core.plan.rebaseline
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

private data class EditablePoint(val date: LocalDate?, val kg: String)

/** The pop-ups the Plan tab opens. */
private enum class PlanSheet { Checkpoints, Rebaseline, Energy, History }

/**
 * The plan as one quiet page: a sentence with the goal and the weeks left, the checkpoints as a timeline with
 * today in it (orange line up to today), and a short list for re-baseline, the energy estimate and the history.
 * Editing happens in pop-ups.
 */
@Composable
fun PlanScreen(
    state: UiState,
    onSaveCheckpoints: (List<Checkpoint>) -> Unit,
    onApplyRebaseline: (List<Checkpoint>) -> Unit,
    onSaveProfile: (ProfileEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<PlanSheet?>(null) }
    val d = state.dashboard
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        GoalSentence(d.plan.goal, state.today)
        Timeline(state, onEdit = { sheet = PlanSheet.Checkpoints })
        Column {
            HorizontalDivider(color = Palette.CardHigh)
            val preview = d.rebaselinePreview()
            val rate = preview?.requiredKgPerWeek
            ListRow(
                "Re-baseline",
                when {
                    d.fit == null -> "after 3 weigh-ins"
                    rate == null -> "goal date passed"
                    else -> "%.2f kg/week needed".format(rate)
                },
                enabled = d.fit != null && preview != null,
            ) { sheet = PlanSheet.Rebaseline }
            ListRow(
                "Energy estimate",
                "%,.0f × %s − %,.0f".format(state.profile.bmrKcal, fieldText(state.profile.activityFactor), state.profile.plannedFoodDeficitKcal),
            ) { sheet = PlanSheet.Energy }
            ListRow("History", state.plans.firstOrNull { it.active }?.label ?: "${state.plans.size} plans") { sheet = PlanSheet.History }
        }
        Disclaimer()
    }

    val close = { sheet = null }
    when (sheet) {
        PlanSheet.Checkpoints -> CheckpointDialog(state.checkpoints, onSave = { onSaveCheckpoints(it); close() }, onDismiss = close)
        PlanSheet.Rebaseline -> RebaselineDialog(state, onApply = { onApplyRebaseline(it); close() }, onDismiss = close)
        PlanSheet.Energy -> EnergyDialog(state.profile, onSave = { onSaveProfile(it); close() }, onDismiss = close)
        PlanSheet.History -> HistoryDialog(state.plans, onDismiss = close)
        null -> {}
    }
}

/** "82 kg by 7 January." with "9 weeks to go." in grey underneath. */
@Composable
private fun GoalSentence(goal: Checkpoint, today: LocalDate) {
    val days = ChronoUnit.DAYS.between(today, goal.date)
    Text(
        buildAnnotatedString {
            append("${plainKg(goal.kg)} kg by ${dayMonthLong(goal.date)}.\n")
            withStyle(SpanStyle(color = Palette.Muted)) {
                append(
                    when {
                        days < 0 -> "The goal date has passed."
                        days < 7 -> "This week."
                        else -> "${ceil(days / 7.0).toInt()} weeks to go."
                    },
                )
            }
        },
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Medium,
    )
}

private enum class Stop { Past, Today, Future, Goal }

private data class TimelineRow(val stop: Stop, val date: String, val label: String, val value: String)

/**
 * Checkpoints and today in date order on a thin line with a dot each: grey before today, orange for today, dim
 * after it, white for the goal. The line is orange from the start down to today. Tapping a checkpoint (or "Add
 * checkpoint") opens the checkpoint editor.
 */
@Composable
private fun Timeline(state: UiState, onEdit: () -> Unit) {
    val today = state.today
    val points = state.checkpoints.sortedBy { it.date }
    val position = state.dashboard.todayVsPlan()
    val todayRow = TimelineRow(
        Stop.Today,
        "Today",
        position?.let { p ->
            when {
                p.gapKg > 0.05 -> "%.1f kg behind".format(p.gapKg)
                p.gapKg < -0.05 -> "%.1f kg ahead".format(-p.gapKg)
                else -> "On plan"
            }
        } ?: "No weigh-in yet",
        position?.let { "%.1f".format(it.kg) } ?: "–",
    )
    val rows = buildList {
        var todayAdded = false
        points.forEachIndexed { i, c ->
            if (!todayAdded && c.date > today) { add(todayRow); todayAdded = true }
            val goal = i == points.lastIndex
            add(
                TimelineRow(
                    if (goal) Stop.Goal else if (c.date <= today) Stop.Past else Stop.Future,
                    dayMonth(c.date),
                    if (goal) "Goal" else if (i == 0) "Start" else "",
                    "%.1f".format(c.kg),
                ),
            )
        }
        if (!todayAdded) add(todayRow)
    }
    val todayIndex = rows.indexOfFirst { it.stop == Stop.Today }

    Column {
        rows.forEachIndexed { j, row ->
            val clickable = row.stop != Stop.Today
            Row(
                Modifier.fillMaxWidth().height(56.dp)
                    .then(if (clickable) Modifier.clickable(onClickLabel = "Edit checkpoints", onClick = onEdit) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Rail(
                    dot = when (row.stop) {
                        Stop.Past -> Palette.Muted
                        Stop.Today -> Palette.Accent
                        Stop.Future -> Palette.Outline
                        Stop.Goal -> Palette.Text
                    },
                    above = if (j == 0) null else if (j <= todayIndex) Palette.Accent else Palette.CardHigh,
                    below = if (j == rows.lastIndex) null else if (j < todayIndex) Palette.Accent else Palette.CardHigh,
                )
                Spacer(Modifier.width(16.dp))
                val isToday = row.stop == Stop.Today
                Text(
                    row.date,
                    Modifier.width(72.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isToday) Palette.Accent else Palette.Muted,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    row.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (row.stop) {
                        Stop.Goal -> Palette.Text
                        else -> Palette.Muted
                    },
                )
                Text(
                    row.value,
                    style = MaterialTheme.typography.titleMedium,
                    color = when (row.stop) {
                        Stop.Today -> Palette.Accent
                        Stop.Past -> Palette.Muted
                        else -> Palette.Text
                    },
                    fontWeight = if (row.stop == Stop.Today || row.stop == Stop.Goal) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        TextButton(onClick = onEdit, modifier = Modifier.padding(start = 28.dp).height(44.dp)) {
            Text("Add checkpoint", color = Palette.Muted)
        }
    }
}

/** One stop on the timeline line: the segment from the row above ([above]), the dot, the segment to the row below. */
@Composable
private fun Rail(dot: Color, above: Color?, below: Color?) {
    Canvas(Modifier.width(24.dp).fillMaxHeight()) {
        val x = size.width / 2
        val mid = size.height / 2
        val stroke = 2.dp.toPx()
        above?.let { drawLine(it, Offset(x, 0f), Offset(x, mid), stroke) }
        below?.let { drawLine(it, Offset(x, mid), Offset(x, size.height), stroke) }
        drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, mid))
    }
}

/** A quiet list row: title, grey value and a chevron, with a hairline below. */
@Composable
private fun ListRow(title: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (enabled) Palette.Text else Palette.Muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = Palette.CardHigh)
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

/** Every plan so far, the active one marked. */
@Composable
internal fun HistoryDialog(plans: List<PlanEntity>, onDismiss: () -> Unit) {
    FormDialog("Plan history", onDismiss = onDismiss, confirmLabel = "Close", onConfirm = onDismiss, dismissLabel = null) {
        plans.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.titleSmall)
                    Text("Created ${dayMonthYear(p.createdAt)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
                if (p.active) Pill("Active", filled = true)
            }
        }
    }
}

/** Re-baseline from today to the same goal, or to a later date at a steady 0.5 kg/week. */
@Composable
internal fun RebaselineDialog(state: UiState, onApply: (List<Checkpoint>) -> Unit, onDismiss: () -> Unit) {
    val d = state.dashboard
    val preview = d.rebaselinePreview() ?: return
    // A gentler alternative: same goal weight at 0.5 kg/week.
    val alternativeDate = d.asOf.plusDays(ceil((preview.startKg - d.plan.goal.kg) / 0.5 * 7).toLong().coerceAtLeast(7))
    val alternative = rebaseline(d.weights, d.asOf, alternativeDate, d.plan.goal.kg)
    val keepAllowed = preview.requiredKgPerWeek != null && !preview.exceedsSafeMax
    val offerAlternative = alternativeDate != d.plan.goal.date
    FormDialog(
        title = "New plan from ${dayMonth(d.asOf)}",
        onDismiss = onDismiss,
        confirmLabel = if (keepAllowed) "Keep goal date (${dayMonth(d.plan.goal.date)})" else "Use ${dayMonth(alternativeDate)}",
        onConfirm = { onApply(if (keepAllowed) preview.checkpoints else alternative.checkpoints) },
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
            Text(rateAdvice(preview), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Text(
            "Starts a new planned line from your current weight to the same goal. The old plan stays in the history.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
        if (offerAlternative && alternative.requiredKgPerWeek != null) {
            Text(
                "A steady 0.5 kg/week instead reaches ${kg(d.plan.goal.kg)} on ${dayMonthYear(alternativeDate)}.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            if (keepAllowed) {
                SecondaryButton("Use ${dayMonth(alternativeDate)} instead", { onApply(alternative.checkpoints) }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Edit all checkpoints at once; the last one is the goal. */
@Composable
internal fun CheckpointDialog(checkpoints: List<Checkpoint>, onSave: (List<Checkpoint>) -> Unit, onDismiss: () -> Unit) {
    var points by remember(checkpoints) { mutableStateOf(checkpoints.sortedBy { it.date }.map { EditablePoint(it.date, fieldText(it.kg)) }) }
    val parsed = points.map { p -> p.date?.let { d -> parseDecimal(p.kg)?.takeIf { it in 30.0..250.0 }?.let { Checkpoint(d, it) } } }
    val complete = parsed.filterNotNull()
    val valid = complete.size == points.size && complete.size >= 2 && complete.map { it.date }.toSet().size == complete.size
    val sorted = complete.sortedBy { it.date }

    FormDialog(
        title = "Planned checkpoints",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = { onSave(sorted) },
        confirmEnabled = valid && sorted != checkpoints.sortedBy { it.date },
    ) {
        Text("The last checkpoint is the goal.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
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
        SecondaryButton("Add checkpoint", {
            val last = sorted.lastOrNull()
            points = points + EditablePoint(last?.date?.plusDays(14), fieldText(last?.kg))
        })
    }
}

/** BMR, activity factor and planned food deficit behind the energy estimate. */
@Composable
internal fun EnergyDialog(profile: ProfileEntity, onSave: (ProfileEntity) -> Unit, onDismiss: () -> Unit) {
    var bmr by remember(profile) { mutableStateOf(fieldText(profile.bmrKcal)) }
    var factor by remember(profile) { mutableStateOf(fieldText(profile.activityFactor)) }
    var deficit by remember(profile) { mutableStateOf(fieldText(profile.plannedFoodDeficitKcal)) }
    val bmrV = parseDecimal(bmr)?.takeIf { it in 800.0..4000.0 }
    val factorV = parseDecimal(factor)?.takeIf { it in 1.0..2.5 }
    val deficitV = parseDecimal(deficit)?.takeIf { it in 0.0..1000.0 }
    val changed = bmrV != profile.bmrKcal || factorV != profile.activityFactor || deficitV != profile.plannedFoodDeficitKcal

    FormDialog(
        title = "Energy estimate",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = { onSave(profile.copy(bmrKcal = bmrV!!, activityFactor = factorV!!, plannedFoodDeficitKcal = deficitV!!)) },
        confirmEnabled = bmrV != null && factorV != null && deficitV != null && changed,
    ) {
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
    }
}
