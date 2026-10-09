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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.round

private val hungerLabels = listOf("None", "Low", "Okay", "High", "Very")

/**
 * The day's page: run, rest day and this week's numbers. On the weekly weigh-in day (or when the day already has
 * a weight, or after "Weigh in now") it also shows the weigh-in card with ±0.1 steppers and the weekly check-in.
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
) {
    var date by rememberSaveable { mutableStateOf(state.today) }
    var showRunDialog by remember { mutableStateOf(false) }
    val existing = state.weights.firstOrNull { it.date == date }
    val weighInDay = DayOfWeek.of(state.profile.weighInDay.coerceIn(1, 7))
    var weighInOpen by remember(date) { mutableStateOf(false) }
    val showWeighIn = date.dayOfWeek == weighInDay || existing != null || weighInOpen
    val lastKg = state.weights.lastOrNull { it.date <= date }?.kg ?: state.weights.lastOrNull()?.kg ?: 80.0

    // Field state resets when the date (or its stored entry) changes.
    var weight by remember(date, existing) { mutableStateOf(fieldText(existing?.kg ?: lastKg)) }
    var sleep by remember(date, existing) { mutableStateOf(fieldText(existing?.sleepHours)) }
    var hunger by remember(date, existing) { mutableStateOf(existing?.hunger) }
    var snacks by remember(date, existing) { mutableStateOf(existing?.snacks ?: "") }
    var note by remember(date, existing) { mutableStateOf(existing?.note ?: "") }

    val kgValue = parseDecimal(weight)
    val weightValid = kgValue != null && kgValue in 30.0..250.0
    val sleepValue = parseDecimal(sleep)
    val sleepValid = sleep.isBlank() || (sleepValue != null && sleepValue in 0.0..24.0)
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
                    Text(if (date.dayOfWeek == weighInDay) "Weekly weigh-in" else "Extra weigh-in", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
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
                        TextButton(onClick = onScreenshot) { Text("Fill from a scale screenshot", color = Palette.Accent) }
                    }
                }
            }

            DarkCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hunger", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        hungerLabels.forEachIndexed { i, label ->
                            val level = i + 1
                            val selected = hunger == level
                            Button(
                                onClick = { hunger = if (selected) null else level },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Palette.Accent else Palette.CardHigh,
                                    contentColor = if (selected) Palette.OnAccent else Palette.Text,
                                ),
                            ) { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkField(sleep, { sleep = it }, "Sleep (h)", Modifier.width(104.dp), KeyboardType.Decimal, isError = !sleepValid)
                        DarkField(snacks, { snacks = it }, "Snacks", Modifier.weight(1f))
                    }
                    DarkField(note, { note = it }, "Note", Modifier.fillMaxWidth())
                }
            }

            Button(
                onClick = {
                    onSaveWeight(
                        WeightEntity(
                            date = date,
                            kg = round(kgValue!! * 100) / 100,
                            sleepHours = sleepValue,
                            hunger = hunger,
                            snacks = snacks.trim().ifBlank { null },
                            note = note.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = weightValid && sleepValid,
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
                next = date.with(TemporalAdjusters.next(weighInDay)),
                last = state.weights.lastOrNull { it.date <= date },
                onWeighInNow = { weighInOpen = true },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DarkCard(Modifier.weight(1f), onClick = { showRunDialog = true }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (date == state.today) "Run today" else "Run", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Text(
                        if (ranToday.isEmpty()) "No run" else "%.2f km".format(ranToday.sumOf { it.km }),
                        style = numberStyle(28.sp),
                    )
                    Text("Tap to add a run", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
            }
            DarkCard(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rest day", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Switch(checked = date in state.restDays, onCheckedChange = { onSetRest(date, it) })
                }
            }
        }

        WeekStrip(state.dashboard.weekly.firstOrNull { it.start == weekStart(date) }, if (date == state.today) state.dashboard.runStreak else null)

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

/** Off-day card: when the next weigh-in is and the last weight, with a way to weigh in anyway. */
@Composable
private fun NextWeighInCard(next: LocalDate, last: WeightEntity?, onWeighInNow: () -> Unit) {
    DarkCard(radius = 24) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Next weigh-in", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(longDay(next), style = numberStyle(30.sp))
            if (last != null) {
                Text("Last %.1f kg on %s".format(last.kg, dayMonth(last.date)), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
            TextButton(onClick = onWeighInNow, contentPadding = PaddingValues(0.dp)) { Text("Weigh in now", color = Palette.Accent) }
        }
    }
}

/** Runs, distance and rest days of the week holding the shown day; [streak] only for today. */
@Composable
private fun WeekStrip(week: WeekSummary?, streak: Int?) {
    if (week == null) return
    DarkCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Week of ${weekRange(week.start)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Row(Modifier.fillMaxWidth()) {
                WeekFigure("${week.runs}", "runs", Modifier.weight(1f))
                WeekFigure("%.1f".format(week.km), "km", Modifier.weight(1f))
                WeekFigure("${week.restDays}", "rest days", Modifier.weight(1f))
                if (streak != null) WeekFigure("$streak", "day streak", Modifier.weight(1f))
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
