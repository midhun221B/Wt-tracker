package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import wt.app.data.ProfileEntity

@Composable
fun SettingsScreen(
    profile: ProfileEntity,
    notificationsAllowed: Boolean,
    onReminder: (enabled: Boolean, hour: Int, minute: Int) -> Unit,
    onExport: (ExportKind) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var time by remember(profile) { mutableStateOf("%02d:%02d".format(profile.reminderHour, profile.reminderMinute)) }
    var confirmRestore by remember { mutableStateOf(false) }
    val parsedTime = Regex("""^(\d{1,2}):(\d{2})$""").find(time.trim())?.destructured?.let { (h, m) ->
        (h.toInt() to m.toInt()).takeIf { it.first in 0..23 && it.second in 0..59 }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Daily weigh-in reminder") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remind me if I haven't logged by", Modifier.weight(1f))
                Switch(
                    checked = profile.reminderEnabled,
                    onCheckedChange = { on -> parsedTime?.let { onReminder(on, it.first, it.second) } },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Time (JST)", time, { time = it }, Modifier.width(140.dp), keyboardType = KeyboardType.Text, isError = parsedTime == null)
                Button(
                    onClick = { parsedTime?.let { onReminder(true, it.first, it.second) } },
                    enabled = parsedTime != null,
                ) { Text("Set") }
            }
            if (profile.reminderEnabled && !notificationsAllowed) {
                Text(
                    "Notifications are blocked for this app. Allow them in Android settings to get the reminder.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard("Export") {
            Text("Files are saved where you choose (e.g. Downloads or Drive). Nothing is uploaded by the app.", style = MaterialTheme.typography.bodySmall)
            ExportButton("Weights CSV") { onExport(ExportKind.WEIGHTS_CSV) }
            ExportButton("Runs CSV") { onExport(ExportKind.RUNS_CSV) }
            ExportButton("Body composition CSV") { onExport(ExportKind.BODY_CSV) }
        }

        SectionCard("Backup") {
            Text("A JSON file with all data: weights, runs, body, rest days, plans and settings.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onExport(ExportKind.BACKUP_JSON) }, modifier = Modifier.fillMaxWidth()) { Text("Save backup") }
            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup…") }
        }

        SectionCard("About") {
            Text("All data stays on this phone. No accounts, no cloud. Dates use Asia/Tokyo time.", style = MaterialTheme.typography.bodySmall)
            Disclaimer()
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore backup?") },
            text = { Text("This replaces ALL current data with the backup's contents. Save a backup first if unsure.") },
            confirmButton = { TextButton(onClick = { confirmRestore = false; onRestore() }) { Text("Choose file") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExportButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
