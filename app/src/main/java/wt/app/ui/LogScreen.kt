package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import wt.core.summary.WeekSummary
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
    val status = weighInStatus(state.weights.map { it.date }, date, weighInDay.value)
    var weighInOpen by remember(date) { mutableStateOf(false) }
    // Weekly: show the card when this week's weigh-in is due, the day already has a weight, or on request.
    val showWeighIn = status.due || existing != null || weighInOpen
    val lastKg = state.weights.lastOrNull { it.date <= date }?.kg ?: state.weights.lastOrNull()?.kg ?: 80.0

    // Field state resets when the date (or its stored entry) changes.
    var weight by remember(date, existing) { mutableStateOf(fieldText(existing?.kg ?: lastKg)) }

    val kgValue = parseDecimal(weight)
    val weightValid = kgValue != null && kgValue in 30.0..250.0
    val planned = state.dashboard.plan.at(date)
    val ranToday = state.runs.filter { it.date == date }

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
                TextButton(onClick = { onDeleteWeight(date) }, modifier = Modifier.fillMaxWidth()) { Text("Delete this entry", color = Palette.Muted) }
            }
        } else {
            NextWeighInCard(
                next = status.next,
                doneThisWeek = status.doneOn?.let { d -> state.weights.firstOrNull { it.date == d } },
                last = state.weights.lastOrNull { it.date <= date },
                weighInDay = weighInDay.value,
                onWeighInNow = { weighInOpen = true },
                onWeighInDay = onWeighInDay,
            )
        }

        RunningCard(
            today = date == state.today,
            runs = ranToday,
            rest = date in state.restDays,
            week = state.dashboard.weekly.firstOrNull { it.start == weekStart(date) },
            streak = if (date == state.today) state.dashboard.runStreak else null,
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

/** Card when no weigh-in is due: the next weigh-in, this week's if already done, and ways to weigh in anyway or change the day. */
@Composable
private fun NextWeighInCard(
    next: LocalDate,
    doneThisWeek: WeightEntity?,
    last: WeightEntity?,
    weighInDay: Int,
    onWeighInNow: () -> Unit,
    onWeighInDay: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    DarkCard(radius = 24) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Next weigh-in", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(longDay(next), style = numberStyle(30.sp))
            when {
                doneThisWeek != null -> Text(
                    "This week done: %.1f kg on %s".format(doneThisWeek.kg, dayMonth(doneThisWeek.date)),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Accent,
                )
                last != null -> Text("Last %.1f kg on %s".format(last.kg, dayMonth(last.date)), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onWeighInNow, contentPadding = PaddingValues(0.dp)) { Text("Weigh in now", color = Palette.Accent) }
                TextButton(onClick = { picking = !picking }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (picking) "Done" else "Change day", color = Palette.Muted)
                }
            }
            if (picking) WeekdayPicker(weighInDay, { onWeighInDay(it); picking = false })
        }
    }
}

/**
 * Today's running and the week in one card: the day's run with an add button, the rest-day switch,
 * then the week's runs, km, rest days and (for today) the streak.
 */
@Composable
private fun RunningCard(
    today: Boolean,
    runs: List<RunEntity>,
    rest: Boolean,
    week: WeekSummary?,
    streak: Int?,
    onAddRun: () -> Unit,
    onRest: (Boolean) -> Unit,
) {
    DarkCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (today) "Run today" else "Run", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    if (runs.isEmpty()) {
                        Text("No run yet", style = numberStyle(28.sp), color = Palette.Muted)
                    } else {
                        val km = runs.sumOf { it.km }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("%.2f km".format(km), style = numberStyle(28.sp))
                            Text("  " + pace(runs.sumOf { it.durationSec } / km), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                }
                SecondaryButton("Add run", onAddRun)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rest day", Modifier.weight(1f))
                Switch(checked = rest, onCheckedChange = onRest)
            }
            if (week != null) {
                HorizontalDivider(color = Palette.CardHigh)
                Text("Week of ${weekRange(week.start)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Row(Modifier.fillMaxWidth()) {
                    WeekFigure("${week.runs}", if (week.runs == 1) "run" else "runs", Modifier.weight(1f))
                    WeekFigure("%.1f".format(week.km), "km", Modifier.weight(1f))
                    WeekFigure("${week.restDays}", if (week.restDays == 1) "rest day" else "rest days", Modifier.weight(1f))
                    if (streak != null) WeekFigure("$streak", "day streak", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WeekFigure(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = numberStyle(26.sp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
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
