package wt.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wt.app.share.ShareError
import wt.core.model.TOKYO
import java.time.Instant

/** The message my partner gets with the link. */
fun shareLinkText(link: String) = "Open this in Wt-tracker to follow my progress: $link"

/** The share sheet for sending my partner the link (WhatsApp and the like). */
fun shareLinkIntent(link: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareLinkText(link)),
    "Send link to partner",
)

/**
 * Settings' Partner sharing card. Off: what it does and Turn on (asks my name, then Google sign-in). On: when it
 * last uploaded or why it stopped, Send link, Copy link, New link and Turn off.
 */
@Composable
fun PartnerSharingCard(
    state: AppViewModel.SharingState,
    onTurnOn: (name: String) -> Unit,
    onSendLink: () -> Unit,
    onCopyLink: () -> Unit,
    onNewLink: () -> Unit,
    onTurnOff: () -> Unit,
    onSignIn: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    var askingName by remember { mutableStateOf(false) }
    var confirmNewLink by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    SectionCard("Partner sharing") {
        if (!state.on) {
            Text(
                "Let your partner follow your progress on their phone. Your data goes to one file in your Google Drive " +
                    "that only people with the link can open, and it updates a minute after each change.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            PrimaryButton(if (state.busy) "Turning on…" else "Turn on", { askingName = true }, Modifier.fillMaxWidth(), enabled = !state.busy)
        } else {
            val status = when (state.error) {
                ShareError.NeedsSignIn -> "Sign in again to keep sharing"
                ShareError.FileRemoved -> "The shared file was removed"
                ShareError.Offline -> "Waiting for a connection"
                null -> if (state.lastUploadMillis > 0) {
                    "Last update " + ageText(Instant.ofEpochMilli(state.lastUploadMillis).atZone(TOKYO).toString(), Instant.ofEpochMilli(now).atZone(TOKYO))
                } else {
                    "Waiting for the first update"
                }
            }
            Text("Shared as ${state.name.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (state.error != null) Palette.Warn else Palette.Muted)
            when (state.error) {
                ShareError.NeedsSignIn -> PrimaryButton("Sign in", onSignIn, Modifier.fillMaxWidth(), enabled = !state.busy)
                ShareError.FileRemoved -> PrimaryButton("New link", { confirmNewLink = true }, Modifier.fillMaxWidth(), enabled = !state.busy)
                else -> {
                    PrimaryButton("Send link to partner", onSendLink, Modifier.fillMaxWidth(), enabled = !state.busy)
                    SecondaryButton("Copy link", onCopyLink, Modifier.fillMaxWidth(), enabled = !state.busy)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.error != ShareError.FileRemoved) {
                    SecondaryButton("New link", { confirmNewLink = true }, Modifier.weight(1f), enabled = !state.busy)
                }
                SecondaryButton("Turn off", { confirmOff = true }, Modifier.weight(1f), enabled = !state.busy)
            }
        }
    }

    if (askingName) {
        var name by remember { mutableStateOf(state.name.orEmpty()) }
        FormDialog(
            title = "Your name",
            onDismiss = { askingName = false },
            confirmLabel = "Sign in with Google",
            onConfirm = { askingName = false; onTurnOn(name.trim()) },
            confirmEnabled = name.isNotBlank(),
        ) {
            Text("Your partner sees this above your numbers, e.g. \"Following Midhun\".", color = Palette.Muted)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, focusedLabelColor = Palette.Accent, cursorColor = Palette.Accent),
            )
        }
    }
    if (confirmNewLink) {
        FormDialog(title = "New link?", onDismiss = { confirmNewLink = false }, confirmLabel = "New link", onConfirm = { confirmNewLink = false; onNewLink() }) {
            Text("Your partner's current link will stop working. Send them the new one.", color = Palette.Muted)
        }
    }
    if (confirmOff) {
        FormDialog(title = "Turn off sharing?", onDismiss = { confirmOff = false }, confirmLabel = "Turn off", onConfirm = { confirmOff = false; onTurnOff() }) {
            Text("Delete the shared file and stop sharing? Your partner's link stops working.", color = Palette.Muted)
        }
    }
}

/** What a failed Turn on says: Drive's HTTP code (a setup mistake, see the guide) or the connection. */
fun turnOnErrorText(e: Exception): String = TODO()
