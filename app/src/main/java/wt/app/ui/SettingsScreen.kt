package wt.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import wt.app.BuildConfig
import wt.app.data.ProfileEntity

/** The privacy policy page (also linked from Google's consent screen). */
private const val PRIVACY_URL = "https://midhun221b.github.io/Wt-tracker/privacy.html"

/** Settings, most used first: weekly weigh-in, partner sharing, your data (backup and CSV), about. */
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
    var pickingTime by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Most used first: the weekly routine, then sharing, then files, then the small print.
        SectionCard("Weekly weigh-in") {
            Text("Weigh-in day", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            WeekdayPicker(profile.weighInDay, onWeighInDay)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reminder")
                    Text(
                        reminderSummary(profile.weighInDay, profile.reminderHour, profile.reminderMinute, profile.reminderEnabled),
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    )
                }
                Switch(
                    checked = profile.reminderEnabled,
                    onCheckedChange = { on -> onReminder(on, profile.reminderHour, profile.reminderMinute) },
                )
            }
            // The time only matters while the reminder is on.
            if (profile.reminderEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Time", Modifier.weight(1f))
                    SecondaryButton("%02d:%02d".format(profile.reminderHour, profile.reminderMinute), { pickingTime = true })
                }
                if (!notificationsAllowed) {
                    Text(
                        "Notifications are blocked for this app. Allow them in Android settings to get the reminder.",
                        color = Palette.Error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        partnerSharing?.invoke()

        SectionCard("Your data") {
            Text("Files are saved where you choose, like Downloads or Drive.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            PrimaryButton("Save backup", { onExport(ExportKind.BACKUP_JSON) }, Modifier.fillMaxWidth())
            SecondaryButton("Restore from backup…", { confirmRestore = true }, Modifier.fillMaxWidth())
            Text(
                "A backup holds everything: weigh-ins, runs, body, rest days, plans and settings.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            Text("Export a spreadsheet (CSV)", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Weights", { onExport(ExportKind.WEIGHTS_CSV) }, Modifier.weight(1f))
                SecondaryButton("Runs", { onExport(ExportKind.RUNS_CSV) }, Modifier.weight(1f))
                SecondaryButton("Body", { onExport(ExportKind.BODY_CSV) }, Modifier.weight(1f))
            }
        }

        SectionCard("About") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version", Modifier.weight(1f))
                Text(BuildConfig.VERSION_NAME, color = Palette.Muted)
            }
            Text(
                "Your data stays on this phone unless you turn on partner sharing. No accounts. Dates and times use Japan time.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            val uri = LocalUriHandler.current
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { uri.openUri(PRIVACY_URL) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Privacy policy", Modifier.weight(1f), color = Palette.Accent)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Palette.Accent)
            }
            Disclaimer()
        }
    }

    if (pickingTime) ReminderTimeDialog(profile.reminderHour, profile.reminderMinute, { pickingTime = false }) { h, m ->
        pickingTime = false
        onReminder(true, h, m)
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
