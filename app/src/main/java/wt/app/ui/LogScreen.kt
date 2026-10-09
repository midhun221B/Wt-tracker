package wt.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.app.data.RunEntity
import wt.app.data.WeightEntity
import wt.core.summary.weekStart
import wt.core.summary.weighInStatus
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.round

/**
 * The day's page: run, rest day and this week's numbers. On the weekly weigh-in day (or when the day already has
 * a weight, or after "Weigh in now") it also shows the weigh-in card with ±0.1 steppers.
 */
@Composable
fun LogScreen(
    state: UiState,
    onSaveWeight: (WeightEntity) -> Unit,
    onDeleteWeight: (LocalDate) -> Unit,
    onSetRest: (LocalDate, Boolean) -> Unit,
    onSaveRun: (RunEntity) -> Unit,
    modifier: Modifier = Modifier,
    onScreenshot: (() -> Unit)? = null,
    onWeighInDay: (Int) -> Unit = {},
) {
    var date by rememberSaveable { mutableStateOf(state.today) }
    var showRunDialog by remember { mutableStateOf(false) }
    val existing = state.weights.firstOrNull { it.date == date }
    val weighInDay = DayOfWeek.of(state.profile.weighInDay.coerceIn(1, 7))
    // A scale measurement counts as the week's weigh-in too (it may have been saved without a weight).
    val status = weighInStatus(state.weights.map { it.date } + state.body.map { it.date }, date, weighInDay.value)
    var weighInOpen by remember(date) { mutableStateOf(false) }
    // Weekly: the big card shows only while this week's weigh-in is due, or when asked for ("Weigh in now" / "Edit").
    // A day that already has a weight shows it in the small card instead.
    val showWeighIn = (status.due && existing == null) || weighInOpen
    val lastKg = state.weights.lastOrNull { it.date <= date }?.kg ?: state.weights.lastOrNull()?.kg ?: 80.0

    // Field state resets when the date (or its stored entry) changes.
    var weight by remember(date, existing) { mutableStateOf(fieldText(existing?.kg ?: lastKg)) }

    val kgValue = parseDecimal(weight)
    val weightValid = kgValue != null && kgValue in 30.0..250.0
    val planned = state.dashboard.plan.at(date)
    val ranToday = state.runs.filter { it.date == date }
    val monday = weekStart(date)
    val weekRuns = state.runs.filter { it.date != null && it.date in monday..monday.plusDays(6) }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareButton(onClick = { date = date.minusDays(1) }, label = "Previous day") {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (date == state.today) "Today" else "Past day", style = MaterialTheme.typography.titleMedium)
                Text(longDay(date), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
            SquareButton(onClick = { date = date.plusDays(1) }, label = "Next day", enabled = date < state.today) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }

        if (showWeighIn) {
            DarkCard(radius = 24) {
                Column(Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when {
                            // A weight earlier this week already counts as the weekly one.
                            state.weights.any { it.date >= weekStart(date) && it.date < date } -> "Extra weigh-in"
                            existing == null && status.due && date.dayOfWeek != weighInDay ->
                                "Weekly weigh-in · due since " + weighInDay.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                            else -> "Weekly weigh-in"
                        },
                        style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SquareButton(onClick = { kgValue?.let { weight = fieldText(round((it - 0.1) * 10) / 10) } }, label = "Decrease by 0.1 kg", size = 56) {
                            Text("−", fontSize = 26.sp)
                        }
                        BasicTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            singleLine = true,
                            textStyle = numberStyle(80.sp, if (weightValid) Palette.Text else Palette.Error).copy(textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(Palette.Accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(150.dp).semantics { contentDescription = "Weight in kg" },
                        )
                        SquareButton(onClick = { kgValue?.let { weight = fieldText(round((it + 0.1) * 10) / 10) } }, label = "Increase by 0.1 kg", size = 56) {
                            Text("+", fontSize = 26.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Plan %.1f kg".format(planned))
                        if (kgValue != null) {
                            val diff = kgValue - planned
                            if (diff <= 0.05) {
                                Pill(if (diff < -0.05) "%.1f kg under plan".format(-diff) else "On plan", filled = true)
                            } else {
                                Pill("%.1f kg over plan".format(diff), textColor = Palette.Warn)
                            }
                        }
                    }
                    if (onScreenshot != null) {
                        ScreenshotButton("Import scale screenshot", onScreenshot)
                    }
                }
            }

            Button(
                onClick = {
                    // Only the weight is asked now; sleep, hunger, snacks and notes from older entries are kept.
                    val kg = round(kgValue!! * 100) / 100
                    onSaveWeight(existing?.copy(kg = kg) ?: WeightEntity(date = date, kg = kg))
                    weighInOpen = false
                },
                enabled = weightValid,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
            ) {
                Text(
                    (if (existing == null) "Save " else "Update ") + (kgValue?.let { "%.1f kg".format(it) } ?: ""),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (existing != null) {
                TextButton(onClick = { onDeleteWeight(date); weighInOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Delete this entry", color = Palette.Muted) }
            }
        } else {
            NextWeighInCard(
                next = status.next,
                done = status.doneOn?.let { d ->
                    val kg = state.weights.firstOrNull { it.date == d }?.kg
                    val day = if (d == date) "today" else "on ${dayMonth(d)}"
                    if (kg != null) "This week done: %.1f kg %s".format(kg, day) else "This week done: scale measurement $day"
                },
                last = state.weights.lastOrNull { it.date <= date },
                weighInDay = weighInDay.value,
                actionLabel = if (existing != null) "Edit" else "Weigh in",
                onWeighInNow = { weighInOpen = true },
                onWeighInDay = onWeighInDay,
            )
        }

        RunningCard(
            date = date,
            today = state.today,
            runs = ranToday,
            rest = date in state.restDays,
            weekRuns = weekRuns,
            restDays = state.restDays,
            onAddRun = { showRunDialog = true },
            onRest = { onSetRest(date, it) },
        )

        Disclaimer()
    }

    if (showRunDialog) {
        RunDialog(
            initial = null,
            defaultDate = date,
            onDismiss = { showRunDialog = false },
            onSave = { onSaveRun(it); showRunDialog = false },
            onFromScreenshot = onScreenshot?.let { pick -> { showRunDialog = false; pick() } },
        )
    }
}

/**
 * One-line card when no weigh-in is due: the next weigh-in and this week's if done, with "Weigh in" (or "Edit")
 * on the right. Tapping the text opens the weekday chips to change the day.
 */
@Composable
private fun NextWeighInCard(
    next: LocalDate,
    done: String?,
    last: WeightEntity?,
    weighInDay: Int,
    actionLabel: String,
    onWeighInNow: () -> Unit,
    onWeighInDay: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    DarkCard {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tapping the text opens the weekday chips to change the weigh-in day.
                Column(
                    Modifier.weight(1f).heightIn(min = 44.dp).clickable(onClickLabel = "Change weigh-in day") { picking = !picking },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Next weigh-in · ${shortDay(next)}", style = MaterialTheme.typography.titleSmall)
                    when {
                        done != null -> Text(done, style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                        last != null -> Text("Last %.1f kg on %s".format(last.kg, dayMonth(last.date)), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    }
                }
                TextButton(onClick = onWeighInNow) { Text(actionLabel, color = Palette.Accent) }
            }
            if (picking) {
                Text("Weigh-in day", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                WeekdayPicker(weighInDay, { onWeighInDay(it); picking = false }, Modifier.padding(end = 8.dp, bottom = 8.dp))
            }
        }
    }
}

/**
 * Today's running and the week in one card. With a run, the top turns into a done state (orange check, the run's
 * numbers, a quiet "Add another run") and the rest-day switch goes away. Without one: "No run yet", an orange
 * "Add run" and the switch; switching rest on completes the day too. Below, the Mon–Sun dots and the week's total.
 */
@Composable
private fun RunningCard(
    date: LocalDate,
    today: LocalDate,
    runs: List<RunEntity>,
    rest: Boolean,
    weekRuns: List<RunEntity>,
    restDays: Set<LocalDate>,
    onAddRun: () -> Unit,
    onRest: (Boolean) -> Unit,
) {
    val done = runs.isNotEmpty() || rest
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = if (done) Modifier.border(1.dp, Palette.Accent.copy(alpha = 0.35f), shape) else Modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
    ) {
        Column(
            Modifier
                .then(if (done) Modifier.background(Brush.verticalGradient(listOf(Palette.Accent.copy(alpha = 0.14f), Color.Transparent))) else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                runs.isNotEmpty() -> {
                    val km = runs.sumOf { it.km }
                    val sec = runs.sumOf { it.durationSec }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DoneMark(filled = true)
                        Column {
                            Text(if (runs.size > 1) "${runs.size} runs done" else "Run done", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                            Text("%.2f km".format(km), style = numberStyle(28.sp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${pace(sec / km)} · ${duration(sec)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                        TextButton(onClick = onAddRun) { Text("+ Add another", color = Palette.Muted) }
                    }
                }
                rest -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DoneMark(filled = false)
                        Column(Modifier.weight(1f)) {
                            Text("Rest day", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                            Text("Recovery counts", style = numberStyle(28.sp))
                        }
                        Switch(checked = true, onCheckedChange = onRest)
                    }
                    TextButton(onClick = onAddRun, contentPadding = PaddingValues(0.dp)) { Text("+ Log a run anyway", color = Palette.Muted) }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (date == today) "Run today" else "Run", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                            Text("No run yet", style = numberStyle(28.sp), color = Palette.Muted)
                        }
                        PrimaryButton("Add run", onAddRun)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rest day", Modifier.weight(1f))
                        Switch(checked = false, onCheckedChange = onRest)
                    }
                }
            }
            HorizontalDivider(color = Palette.CardHigh)
            val monday = weekStart(date)
            Text("This week · ${weekRange(monday)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            WeekDots(monday, date, today, weekRuns.mapNotNull { it.date }.toSet(), restDays)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${weekRuns.size} ${if (weekRuns.size == 1) "run" else "runs"}", style = numberStyle(22.sp))
                Text("%.1f km this week".format(weekRuns.sumOf { it.km }), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** Orange disc with a check (run done), or an orange ring with a check (rest day). */
@Composable
private fun DoneMark(filled: Boolean) {
    Box(
        Modifier.size(32.dp)
            .then(if (filled) Modifier.background(Palette.Accent, CircleShape) else Modifier.border(2.dp, Palette.Accent, CircleShape)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Check, null, tint = if (filled) Palette.OnAccent else Palette.Accent, modifier = Modifier.size(18.dp)) }
}

/**
 * Mon–Sun dots for the week: orange with a check on run days, a grey ring on rest days, an empty orange ring for
 * the shown day before anything is logged, a dash for past days that were missed, raised grey for days to come.
 */
@Composable
private fun WeekDots(monday: LocalDate, shown: LocalDate, today: LocalDate, runDays: Set<LocalDate>, restDays: Set<LocalDate>) {
    Row(Modifier.fillMaxWidth()) {
        (0L..6L).map { monday.plusDays(it) }.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val ran = d in runDays
                val rested = d in restDays
                val dot = Modifier.size(30.dp)
                // The shown day gets an orange outer ring once it has a run or a rest day.
                Box(
                    Modifier.size(38.dp).then(if (d == shown && (ran || rested)) Modifier.border(2.dp, Palette.Accent, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        when {
                            ran -> dot.background(Palette.Accent, CircleShape)
                            rested -> dot.border(2.dp, Palette.Muted, CircleShape)
                            d == shown -> dot.border(2.dp, Palette.Accent, CircleShape)
                            d < today -> dot // missed: no run, no rest; just a dash
                            else -> dot.background(Palette.CardHigh, CircleShape)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            ran -> Icon(Icons.Default.Check, null, tint = Palette.OnAccent, modifier = Modifier.size(16.dp))
                            !rested && d != shown && d < today -> Box(Modifier.size(width = 12.dp, height = 2.dp).background(Palette.Muted))
                        }
                    }
                }
                Text(
                    d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == shown) Palette.Text else Palette.Muted,
                    fontWeight = if (d == shown) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun DarkCard(
    modifier: Modifier = Modifier,
    radius: Int = 20,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(radius.dp), colors = colors) { content() }
    } else {
        Card(modifier = modifier, shape = RoundedCornerShape(radius.dp), colors = colors) { content() }
    }
}

/** Rounded-square icon button (44 dp by default). */
@Composable
private fun SquareButton(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    size: Int = 44,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(if (size > 44) 16.dp else 12.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (size > 44) Palette.CardHigh else Palette.Card,
            contentColor = Palette.Text,
            disabledContainerColor = Palette.Card.copy(alpha = 0.4f),
            disabledContentColor = Palette.Muted.copy(alpha = 0.4f),
        ),
        modifier = Modifier.size(size.dp).semantics { contentDescription = label },
    ) { content() }
}
