package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.app.data.RunEntity
import wt.app.data.WeightEntity
import java.time.LocalDate
import kotlin.math.round

/** Phone-first daily log: weight (with ±0.1 steppers), optional notes, rest day, quick run. */
@Composable
fun LogScreen(
    state: UiState,
    onSaveWeight: (WeightEntity) -> Unit,
    onDeleteWeight: (LocalDate) -> Unit,
    onSetRest: (LocalDate, Boolean) -> Unit,
    onSaveRun: (RunEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var date by rememberSaveable { mutableStateOf(state.today) }
    var showRunDialog by remember { mutableStateOf(false) }
    val existing = state.weights.firstOrNull { it.date == date }
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

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
            Text(
                if (date == state.today) "Today · $date" else withWeekday(date),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { date = date.plusDays(1) }, enabled = date < state.today) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
            }
        }

        SectionCard(null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { kgValue?.let { weight = fieldText(round((it - 0.1) * 10) / 10) } }) { Text("−", fontSize = 22.sp) }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    isError = !weightValid,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = { kgValue?.let { weight = fieldText(round((it + 0.1) * 10) / 10) } }) { Text("+", fontSize = 22.sp) }
            }
            state.dashboard.plan.at(date).let { planned ->
                Text(
                    "Plan for this day: ${kg(planned)}" + (kgValue?.let { " · ${signedKg(it - planned)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard("Notes (optional)") {
            NumberField("Sleep", sleep, { sleep = it }, Modifier.width(140.dp), suffix = "h", isError = !sleepValid)
            Text("Hunger", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { level ->
                    FilterChip(
                        selected = hunger == level,
                        onClick = { hunger = if (hunger == level) null else level },
                        label = { Text(level.toString()) },
                    )
                }
            }
            Text("1 = not hungry · 5 = very hungry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(snacks, { snacks = it }, label = { Text("Snacks") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
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
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (existing == null) "Save" else "Update") }
        if (existing != null) {
            TextButton(onClick = { onDeleteWeight(date) }, modifier = Modifier.fillMaxWidth()) { Text("Delete this entry") }
        }

        SectionCard(null) {
            val ranToday = state.runs.filter { it.date == date }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rest day", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (ranToday.isEmpty()) "No run logged" else ranToday.joinToString { "%.2f km".format(it.km) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = date in state.restDays, onCheckedChange = { onSetRest(date, it) })
            }
            OutlinedButton(onClick = { showRunDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Add run for this day") }
            if (state.dashboard.runStreak > 0) {
                Text("Run streak: ${state.dashboard.runStreak} day(s)", style = MaterialTheme.typography.bodySmall)
            }
        }

        Disclaimer()
    }

    if (showRunDialog) {
        RunDialog(
            initial = null,
            defaultDate = date,
            onDismiss = { showRunDialog = false },
            onSave = { onSaveRun(it); showRunDialog = false },
        )
    }
}
