package wt.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import wt.app.data.RunEntity
import java.time.LocalDate

@Composable
fun RunsScreen(
    runs: List<RunEntity>,
    today: LocalDate,
    onSave: (RunEntity) -> Unit,
    onDelete: (RunEntity) -> Unit,
    modifier: Modifier = Modifier,
    headerContent: @Composable () -> Unit = {},
) {
    var editing by remember { mutableStateOf<RunEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { headerContent() }
            val dated = runs.filter { it.date != null }
            item {
                val last28 = dated.filter { it.date!! > today.minusDays(28) }
                Text(
                    "Last 28 days: ${last28.size} runs · %.1f km".format(last28.sumOf { it.km }),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(runs, key = { it.id }) { run -> RunRow(run) { editing = run } }
            if (runs.isEmpty()) item { Text("No runs yet.") }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add run") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) RunDialog(null, today, onDismiss = { adding = false }, onSave = { onSave(it); adding = false })
    editing?.let { run ->
        RunDialog(
            initial = run,
            defaultDate = today,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = { onDelete(run); editing = null },
        )
    }
}

@Composable
private fun RunRow(run: RunEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    run.date?.let(::withWeekday) ?: "Date not set. Tap to set it.",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (run.date == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "%.2f km · %s · %s".format(run.km, duration(run.durationSec), pace(run.durationSec / run.km)) +
                        (run.kcal?.let { " · ${kcal(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (run.source != "manual") Text(run.source, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Add or edit a run. Pace is computed from distance and time. */
@Composable
fun RunDialog(
    initial: RunEntity?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (RunEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var date by remember { mutableStateOf(if (initial == null) defaultDate else initial.date) }
    var km by remember { mutableStateOf(fieldText(initial?.km)) }
    var time by remember { mutableStateOf(initial?.let { duration(it.durationSec) } ?: "") }
    var kcalText by remember { mutableStateOf(fieldText(initial?.kcal)) }

    val kmValue = parseDecimal(km)?.takeIf { it in 0.1..100.0 }
    val seconds = parseDuration(time)
    val kcalValue = parseDecimal(kcalText)
    val kcalValid = kcalText.isBlank() || (kcalValue != null && kcalValue in 0.0..5000.0)
    val valid = date != null && kmValue != null && seconds != null && kcalValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add run" else "Edit run") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Date", date, { date = it }, Modifier.fillMaxWidth(), isError = date == null)
                NumberField("Distance", km, { km = it }, Modifier.fillMaxWidth(), suffix = "km", isError = km.isNotBlank() && kmValue == null)
                NumberField("Time (mm:ss)", time, { time = it }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Text, isError = time.isNotBlank() && seconds == null)
                NumberField("Calories (optional)", kcalText, { kcalText = it }, Modifier.fillMaxWidth(), suffix = "kcal", isError = !kcalValid)
                if (kmValue != null && seconds != null) Text("Pace ${pace(seconds / kmValue)}")
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        (initial ?: RunEntity(date = date, km = 0.0, durationSec = 0))
                            .copy(date = date, km = kmValue!!, durationSec = seconds!!, kcal = kcalValue),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
