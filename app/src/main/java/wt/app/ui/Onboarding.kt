package wt.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.app.data.ProfileEntity
import wt.core.model.Checkpoint
import wt.core.model.Defaults
import wt.core.plan.RebaselineResult
import wt.core.plan.firstPlan
import wt.core.plan.steadyGoalDate
import wt.core.summary.weekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The first-run setup screens (design A): welcome, today's weight, goal, weigh-in day, then the plan. */
internal enum class SetupStep { Welcome, Weight, Goal, WeighIn, Plan }

/** What setup has collected so far. The profile starts from the defaults; energy can be changed on the last step. */
internal data class SetupDraft(
    val kg: String = "80.0",
    val goalKg: String = "",
    val goalDate: LocalDate? = null,
    val profile: ProfileEntity = ProfileEntity.from(Defaults.profile),
)

/**
 * First-run setup, shown while there is no profile. "Start" saves the profile, today's weight and the first plan
 * through [onFinish]; "Restore a backup" ([onRestore]) skips setup. [onReminderOn] asks for notification permission.
 */
@Composable
internal fun OnboardingScreen(
    today: LocalDate,
    onFinish: (profile: ProfileEntity, todayKg: Double, checkpoints: List<Checkpoint>) -> Unit,
    onRestore: () -> Unit,
    onReminderOn: () -> Unit,
    initialStep: SetupStep = SetupStep.Welcome,
    initialDraft: SetupDraft = SetupDraft(),
) {
    var step by remember { mutableStateOf(initialStep) }
    var draft by remember { mutableStateOf(initialDraft) }
    BackHandler(enabled = step != SetupStep.Welcome) { step = SetupStep.entries[step.ordinal - 1] }

    val kg = parseDecimal(draft.kg)?.takeIf { it in 30.0..250.0 }
    val goalKg = parseDecimal(draft.goalKg)?.takeIf { it in 30.0..250.0 }
    val goalDate = draft.goalDate
    // Only a loss, at least a week away.
    val plan = if (kg != null && goalKg != null && goalKg < kg - 0.05 && goalDate != null && goalDate >= today.plusDays(7)) {
        firstPlan(today, kg, goalDate, goalKg)
    } else {
        null
    }

    Surface(Modifier.fillMaxSize(), color = Palette.Background, contentColor = Palette.Text) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (step != SetupStep.Welcome) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(
                        onClick = { step = SetupStep.entries[step.ordinal - 1] },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Palette.Card, contentColor = Palette.Text),
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back") }
                    StepBar(step.ordinal, SetupStep.entries.size - 1, Modifier.weight(1f))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
                when (step) {
                    SetupStep.Welcome -> Welcome()
                    SetupStep.Weight -> WeightStep(draft.kg, kg) { draft = draft.copy(kg = it) }
                    SetupStep.Goal -> GoalStep(today, draft, kg, plan) { draft = it }
                    SetupStep.WeighIn -> WeighInStep(today, kg, draft.profile) { draft = draft.copy(profile = it) }
                    SetupStep.Plan -> plan?.let { PlanStep(today, it.checkpoints, draft.profile, { step = SetupStep.WeighIn }) { p -> draft = draft.copy(profile = p) } }
                }
            }
            Spacer(Modifier.height(16.dp))
            when (step) {
                SetupStep.Welcome -> {
                    BigButton("Set up my plan") { step = SetupStep.Weight }
                    Spacer(Modifier.height(12.dp))
                    BigButton("Restore a backup", primary = false, onClick = onRestore)
                    Text(
                        "No account, no cloud.",
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, textAlign = TextAlign.Center,
                    )
                }
                SetupStep.Weight -> BigButton("Next", enabled = kg != null) {
                    // Suggest a goal 5 kg lower at a gentle 0.5 kg/week until the user picks their own.
                    if (draft.goalKg.isBlank() && kg != null) {
                        val suggested = (kg - 5.0).roundToInt().toDouble()
                        draft = draft.copy(goalKg = plainKg(suggested), goalDate = steadyGoalDate(today, kg, suggested))
                    }
                    step = SetupStep.Goal
                }
                SetupStep.Goal -> BigButton("Next", enabled = plan != null && !plan.exceedsSafeMax) { step = SetupStep.WeighIn }
                SetupStep.WeighIn -> BigButton("Next") { step = SetupStep.Plan }
                SetupStep.Plan -> BigButton("Start", enabled = plan != null && kg != null && goalKg != null && goalDate != null) {
                    val p = draft.profile.copy(goalKg = goalKg!!, goalDate = goalDate!!)
                    if (p.reminderEnabled) onReminderOn()
                    onFinish(p, kg!!, plan!!.checkpoints)
                }
            }
        }
    }
}

/** Thin progress segments, the first [done] in orange. */
@Composable
private fun StepBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "Step $done of $total" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < done) Palette.Accent else Palette.CardHigh))
        }
    }
}

@Composable
private fun BigButton(text: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Palette.Accent else Palette.CardHigh,
            contentColor = if (primary) Palette.OnAccent else Palette.Text,
            disabledContainerColor = Palette.CardHigh, disabledContentColor = Palette.Muted,
        ),
    ) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium) }
}

@Composable
private fun Heading(text: String, sub: String? = null) {
    Text(text, fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold)
    if (sub != null) {
        Text(sub, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
    }
}

@Composable
private fun Welcome() {
    Text("Wt-tracker", style = numberStyle(22.sp, Palette.Accent))
    Spacer(Modifier.height(32.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Card).padding(20.dp)) {
        PlanVsRealitySketch(Modifier.fillMaxWidth().height(140.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(Palette.Planned, "Planned", dashed = true)
            LegendItem(Palette.Accent, "Realistic", thick = true)
        }
    }
    Spacer(Modifier.height(36.dp))
    Heading(
        "Your plan, and where you’ll really land.",
        "Set a goal, weigh in once a week, and the forecast updates from your own trend. Everything stays on this phone.",
    )
}

/** A decorative planned (dashed) vs realistic (orange, with its band) chart for the welcome screen. */
@Composable
private fun PlanVsRealitySketch(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val band = Path().apply {
            moveTo(0f, h * 0.12f); lineTo(w * 0.35f, h * 0.40f); lineTo(w * 0.65f, h * 0.58f); lineTo(w, h * 0.68f)
            lineTo(w, h * 0.94f); lineTo(w * 0.65f, h * 0.80f); lineTo(w * 0.35f, h * 0.58f); lineTo(0f, h * 0.20f); close()
        }
        drawPath(band, Palette.Accent.copy(alpha = 0.18f))
        drawLine(
            Palette.Planned, Offset(0f, h * 0.15f), Offset(w, h * 0.90f),
            strokeWidth = 2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
        )
        val realistic = Path().apply {
            moveTo(0f, h * 0.16f); lineTo(w * 0.18f, h * 0.27f); lineTo(w * 0.35f, h * 0.49f); lineTo(w * 0.65f, h * 0.69f); lineTo(w, h * 0.81f)
        }
        drawPath(realistic, Palette.Accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Palette.Accent, 5.dp.toPx(), Offset(0f, h * 0.16f))
    }
}

@Composable
private fun WeightStep(text: String, kg: Double?, onChange: (String) -> Unit) {
    Heading("What do you weigh today?", "In the morning, before breakfast, is best. This is the start of your plan.")
    Spacer(Modifier.height(40.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = numberStyle(96.sp, if (kg != null) Palette.Text else Palette.Error).copy(textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Palette.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(170.dp).semantics { contentDescription = "Weight today in kg" },
        )
        Text("kg", Modifier.padding(start = 6.dp, bottom = 18.dp), style = MaterialTheme.typography.titleLarge, color = Palette.Muted)
    }
    Text(
        "Drag the ruler, or tap the number to type",
        Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    WeightRuler(kg = kg ?: 80.0, onKg = { onChange(String.format(Locale.ROOT, "%.1f", it)) }, planKg = null, lastKg = null)
}

@Composable
private fun GoalStep(today: LocalDate, draft: SetupDraft, kg: Double?, plan: RebaselineResult?, onChange: (SetupDraft) -> Unit) {
    Heading("Where do you want to be, and by when?")
    Spacer(Modifier.height(28.dp))
    val goalKg = parseDecimal(draft.goalKg)
    val goalError = goalKg == null || goalKg !in 30.0..250.0 || (kg != null && goalKg >= kg - 0.05)
    val dateError = draft.goalDate == null || draft.goalDate < today.plusDays(7)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField("Goal weight", draft.goalKg, { onChange(draft.copy(goalKg = it)) }, Modifier.weight(1f), suffix = "kg", isError = goalError)
        DateField("Goal date", draft.goalDate, { onChange(draft.copy(goalDate = it)) }, Modifier.weight(1.3f), isError = dateError, withWeekday = false)
    }
    when {
        goalError && kg != null -> Hint("The goal must be below today’s ${plainKg(kg)} kg.")
        dateError -> Hint("Pick a date at least a week from today.")
    }
    if (plan != null && kg != null && goalKg != null) {
        val weeks = ceil(ChronoUnit.DAYS.between(today, plan.checkpoints.last().date) / 7.0).toInt()
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${plainKg(kg - goalKg)} kg in $weeks ${if (weeks == 1) "week" else "weeks"}", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%.2f kg/week".format(plan.requiredKgPerWeek ?: 0.0), style = numberStyle(40.sp, if (plan.exceedsSafeMax) Palette.Warn else Palette.Text))
                RateStatus(plan)
            }
            Text(rateAdvice(plan), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            if (plan.unrealistic) {
                val steady = steadyGoalDate(today, kg, goalKg)
                Button(
                    onClick = { onChange(draft.copy(goalDate = steady)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
                ) { Text("Use ${dayMonthLong(steady)} (0.5 kg/week)", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    Disclaimer(Modifier.padding(top = 12.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Error)
}

@Composable
private fun WeighInStep(today: LocalDate, kg: Double?, profile: ProfileEntity, onChange: (ProfileEntity) -> Unit) {
    var pickingTime by remember { mutableStateOf(false) }
    Heading("Which day will you weigh in?", "Once a week, same morning. Daily swings are mostly water, so a weekly number is enough for the trend.")
    Spacer(Modifier.height(28.dp))
    WeekdayPicker(profile.weighInDay, { onChange(profile.copy(weighInDay = it)) })
    // Today's weight is this Mon–Sun week's weigh-in, so the next one is next week.
    val next = weekStart(today).plusWeeks(1).plusDays(profile.weighInDay - 1L)
    Text(
        (kg?.let { "Today’s ${"%.1f".format(it)} kg counts as this week’s weigh-in. " } ?: "") + "Next one: ${longDay(next)}.",
        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
    )
    Spacer(Modifier.height(24.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Card).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reminder", style = MaterialTheme.typography.bodyLarge)
                Text("Only if you haven’t logged by then", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            Switch(checked = profile.reminderEnabled, onCheckedChange = { onChange(profile.copy(reminderEnabled = it)) })
        }
        Row(Modifier.heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Time", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            SecondaryButton("%02d:%02d".format(profile.reminderHour, profile.reminderMinute), { pickingTime = true })
        }
    }
    if (pickingTime) ReminderTimeDialog(profile.reminderHour, profile.reminderMinute, { pickingTime = false }) { h, m ->
        pickingTime = false
        onChange(profile.copy(reminderEnabled = true, reminderHour = h, reminderMinute = m))
    }
}

@Composable
private fun PlanStep(
    today: LocalDate,
    checkpoints: List<Checkpoint>,
    profile: ProfileEntity,
    onEditWeighIn: () -> Unit,
    onProfile: (ProfileEntity) -> Unit,
) {
    var editingEnergy by remember { mutableStateOf(false) }
    Text("Your plan", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
    Spacer(Modifier.height(6.dp))
    GoalSentence(checkpoints.last(), today)
    Spacer(Modifier.height(24.dp))
    checkpoints.forEachIndexed { i, c ->
        val first = i == 0
        val goal = i == checkpoints.lastIndex
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Rail(
                dot = if (first) Palette.Accent else if (goal) Palette.Text else Palette.Outline,
                above = if (first) null else Palette.CardHigh,
                below = if (goal) null else Palette.CardHigh,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                if (first) "Today" else dayMonth(c.date), Modifier.width(72.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (first) Palette.Accent else Palette.Muted,
                fontWeight = if (first) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                if (first) "Start" else if (goal) "Goal" else "", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge, color = if (goal) Palette.Text else Palette.Muted,
            )
            Text(
                "%.1f".format(c.kg),
                style = MaterialTheme.typography.titleMedium,
                color = if (first) Palette.Accent else Palette.Text,
                fontWeight = if (first || goal) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
    Text(
        "Monthly checkpoints at a steady pace. You can edit them later in Plan.",
        Modifier.padding(top = 4.dp, bottom = 20.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
    )
    val day = DayOfWeek.of(profile.weighInDay).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    ListRow(
        "Weigh-in",
        "${day}s" + if (profile.reminderEnabled) " · %02d:%02d".format(profile.reminderHour, profile.reminderMinute) else " · no reminder",
        onClick = onEditWeighIn,
    )
    ListRow(
        "Energy estimate",
        "%,.0f × %s − %,.0f".format(profile.bmrKcal, fieldText(profile.activityFactor), profile.plannedFoodDeficitKcal),
    ) { editingEnergy = true }
    Disclaimer(Modifier.padding(top = 12.dp))
    if (editingEnergy) EnergyDialog(profile, { onProfile(it); editingEnergy = false }) { editingEnergy = false }
}
