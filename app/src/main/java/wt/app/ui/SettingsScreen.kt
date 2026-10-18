package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wt.app.R
import wt.app.data.ProfileEntity

/**
 * Settings as a grouped list, most used first: weekly weigh-in, partner sharing, your data, about. Less used
 * choices (the weigh-in day, CSV export) open small pop-ups.
 */
@Composable
fun SettingsScreen(
    profile: ProfileEntity,
    notificationsAllowed: Boolean,
    onReminder: (enabled: Boolean, hour: Int, minute: Int) -> Unit,
    onWeighInDay: (Int) -> Unit,
    onExport: (ExportKind) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    partnerSharing: (@Composable () -> Unit)? = null,
) {
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val reminderOn = profile.reminderEnabled

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SettingsGroup(
            "Weekly weigh-in",
            footer = if (reminderOn && !notificationsAllowed) {
                { GroupNote("Notifications are blocked for this app. Allow them in Android settings to get the reminder.", Palette.Error) }
            } else null,
        ) {
            SettingsRow(
                R.drawable.ic_set_calendar, "Weigh-in day", value = weekdayName(profile.weighInDay), iconAccent = true,
                onClick = { pickingDay = true },
            )
            RowDivider()
            // Tapping the row picks the time (which turns the reminder on); the switch turns it on or off.
            SettingsRow(
                R.drawable.ic_set_bell, reminderTitle(profile.reminderHour, profile.reminderMinute, reminderOn),
                subtitle = if (reminderOn) "If you haven't weighed in yet" else "Off",
                end = RowEnd.Toggle(reminderOn) { on -> onReminder(on, profile.reminderHour, profile.reminderMinute) },
                iconAccent = true, onClick = { pickingTime = true },
            )
        }

        partnerSharing?.invoke()

        SettingsGroup("Your data", footer = { GroupNote("Files are saved where you choose, like Downloads or Drive.") }) {
            SettingsRow(
                R.drawable.ic_set_download, "Save backup", "Everything in one file", iconAccent = true,
                onClick = { onExport(ExportKind.BACKUP_JSON) },
            )
            RowDivider()
            SettingsRow(R.drawable.ic_set_upload, "Restore from backup", onClick = { confirmRestore = true })
            RowDivider()
            SettingsRow(R.drawable.ic_set_table, "Export as spreadsheet", "Weights, runs or body (CSV)", onClick = { exporting = true })
        }

        AboutGroup("Your data stays on this phone unless you turn on partner sharing. No accounts. Dates and times use Japan time.")
    }

    if (pickingDay) WeighInDayDialog(profile.weighInDay, { pickingDay = false }) { day ->
        pickingDay = false
        onWeighInDay(day)
    }

    if (pickingTime) ReminderTimeDialog(profile.reminderHour, profile.reminderMinute, { pickingTime = false }) { h, m ->
        pickingTime = false
        onReminder(true, h, m)
    }

    if (exporting) ExportDialog({ exporting = false }) { kind ->
        exporting = false
        onExport(kind)
    }

    if (confirmRestore) {
        FormDialog(
            title = "Restore backup?",
            onDismiss = { confirmRestore = false },
            confirmLabel = "Choose file",
            onConfirm = { confirmRestore = false; onRestore() },
        ) { Text("This replaces all of your current data with the backup's contents. Save a backup first if unsure.", color = Palette.Muted) }
    }
}

/** The weigh-in day pop-up: the week's days, saved with Save. */
@Composable
internal fun WeighInDayDialog(selected: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var day by remember { mutableIntStateOf(selected) }
    FormDialog(title = "Weigh-in day", onDismiss = onDismiss, confirmLabel = "Save", onConfirm = { onSave(day) }) {
        Text("The day you step on the scale each week. The reminder comes that morning.", color = Palette.Muted)
        WeekdayPicker(day, { day = it })
    }
}

/** The CSV export pop-up: weights, runs or body, then Export. */
@Composable
internal fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportKind) -> Unit) {
    val kinds = listOf("Weights" to ExportKind.WEIGHTS_CSV, "Runs" to ExportKind.RUNS_CSV, "Body" to ExportKind.BODY_CSV)
    var picked by remember { mutableStateOf(ExportKind.WEIGHTS_CSV) }
    FormDialog(title = "Export as spreadsheet", onDismiss = onDismiss, confirmLabel = "Export", onConfirm = { onExport(picked) }) {
        Text("A CSV file that opens in Sheets or Excel.", color = Palette.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kinds.forEach { (label, kind) ->
                val on = picked == kind
                Button(
                    onClick = { picked = kind },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (on) Palette.Accent else Palette.CardHigh,
                        contentColor = if (on) Palette.OnAccent else Palette.Text,
                    ),
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** Clock-face picker for the reminder time (24-hour). Setting a time also turns the reminder on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onSet: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    FormDialog(title = "Reminder time", onDismiss = onDismiss, confirmLabel = "Set", onConfirm = { onSet(state.hour, state.minute) }) {
        TimePicker(
            state,
            Modifier.align(Alignment.CenterHorizontally),
            colors = TimePickerDefaults.colors(
                clockDialColor = Palette.CardHigh,
                selectorColor = Palette.Accent,
                timeSelectorSelectedContainerColor = Palette.Accent,
                timeSelectorSelectedContentColor = Palette.OnAccent,
                timeSelectorUnselectedContainerColor = Palette.CardHigh,
                timeSelectorUnselectedContentColor = Palette.Text,
            ),
        )
    }
}
