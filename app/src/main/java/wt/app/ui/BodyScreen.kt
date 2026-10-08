package wt.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import wt.app.chart.MiniLineChart
import wt.app.data.BodyCompEntity
import java.time.LocalDate

/** Body-scale measurements (weekly or monthly) with fat % and visceral fat trends. */
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
    val c = LocalSeriesColors.current
    val sorted = body.sortedBy { it.date }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sorted.size >= 2) item {
                SectionCard("Trends") {
                    Text("Body fat %", style = MaterialTheme.typography.labelMedium)
                    MiniLineChart(sorted.map { it.date to it.fatPct }, c.realistic, Modifier.fillMaxWidth().height(90.dp))
                    Text("Visceral fat", style = MaterialTheme.typography.labelMedium)
                    MiniLineChart(sorted.map { it.date to it.visceral }, c.planned, Modifier.fillMaxWidth().height(90.dp))
                    Text("Muscle mass (kg)", style = MaterialTheme.typography.labelMedium)
                    MiniLineChart(sorted.map { it.date to it.muscleKg }, c.good, Modifier.fillMaxWidth().height(90.dp))
                }
            }
            items(sorted.asReversed(), key = { it.date.toString() }) { entry ->
                val first = sorted.first()
                Card(Modifier.fillMaxWidth().clickable { editing = entry }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(withWeekday(entry.date), style = MaterialTheme.typography.titleSmall)
                        Text("Fat %.1f%% · visceral %.1f · muscle %.1f kg".format(entry.fatPct, entry.visceral, entry.muscleKg))
                        val extra = listOfNotNull(
                            entry.skeletalPct?.let { "skeletal %.1f%%".format(it) },
                            entry.leanKg?.let { "lean %.1f kg".format(it) },
                            entry.bmrKcal?.let { "BMR %.0f".format(it) },
                        )
                        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (entry != first) {
                            Text(
                                "Since start: fat %+.1f%%, visceral %+.1f, muscle %+.1f kg"
                                    .format(entry.fatPct - first.fatPct, entry.visceral - first.visceral, entry.muscleKg - first.muscleKg),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (sorted.isEmpty()) item { Text("No measurements yet.") }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add measurement") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) BodyDialog(null, today, sorted.lastOrNull(), { adding = false }, { onSave(it); adding = false })
    editing?.let { e ->
        BodyDialog(
            e, today, null, { editing = null },
            onSave = {
                if (it.date != e.date) onDelete(e.date) // date changed: move the entry
                onSave(it)
                editing = null
            },
            onDelete = { onDelete(e.date); editing = null },
        )
    }
}

@Composable
private fun BodyDialog(
    initial: BodyCompEntity?,
    today: LocalDate,
    previous: BodyCompEntity?,
    onDismiss: () -> Unit,
    onSave: (BodyCompEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    // New entries start from the previous measurement so only changed values need typing.
    val base = initial ?: previous
    var date by remember { mutableStateOf(initial?.date ?: today) }
    var fat by remember { mutableStateOf(fieldText(base?.fatPct)) }
    var visceral by remember { mutableStateOf(fieldText(base?.visceral)) }
    var muscle by remember { mutableStateOf(fieldText(base?.muscleKg)) }
    var skeletal by remember { mutableStateOf(fieldText(base?.skeletalPct)) }
    var lean by remember { mutableStateOf(fieldText(base?.leanKg)) }
    var bmr by remember { mutableStateOf(fieldText(base?.bmrKcal)) }

    val fatV = parseDecimal(fat)?.takeIf { it in 2.0..70.0 }
    val visceralV = parseDecimal(visceral)?.takeIf { it in 0.0..60.0 }
    val muscleV = parseDecimal(muscle)?.takeIf { it in 10.0..150.0 }
    fun optional(s: String) = if (s.isBlank()) true else parseDecimal(s) != null
    val valid = fatV != null && visceralV != null && muscleV != null && optional(skeletal) && optional(lean) && optional(bmr)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add measurement" else "Edit measurement") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Date", date, { date = it }, Modifier.fillMaxWidth())
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
                onSave(BodyCompEntity(date, fatV!!, visceralV!!, muscleV!!, parseDecimal(skeletal), parseDecimal(lean), parseDecimal(bmr)))
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
