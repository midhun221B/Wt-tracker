package wt.app.ui

import android.content.Intent
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
import wt.app.R
import wt.app.share.ShareError
import wt.core.io.DriveException
import java.io.IOException
import wt.core.model.TOKYO
import java.time.Instant

/** The message my partner gets with the link. */
fun shareLinkText(link: String) = "Open this in $APP_NAME to follow my progress: $link"

/** The share sheet for sending my partner the link (WhatsApp and the like). */
fun shareLinkIntent(link: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareLinkText(link)),
    "Send link to partner",
)

/**
 * Settings' Partner sharing group. Off: what it does and Turn on (asks my name, then Google sign-in). On: who it's
 * shared as and when it last uploaded (or why it stopped), the one action that matters now (Send link, Sign in
 * again or Make a new link), and Manage link (copy, new link, turn off) in a pop-up.
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
    var managing by remember { mutableStateOf(false) }
    var confirmNewLink by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    // While a sign-in or upload is running, actions wait.
    fun act(action: () -> Unit): (() -> Unit)? = if (state.busy) null else action

    if (!state.on) {
        SettingsGroup(
            "Partner sharing",
            footer = { GroupNote("Your data goes to one file in your Google Drive that only people with the link can open. It updates a minute after each change.") },
        ) {
            SettingsRow(
                R.drawable.ic_set_people, "Partner sharing", "Your partner follows along on their phone",
                end = RowEnd.Pill("Off", active = false), iconAccent = true,
            )
            RowDivider()
            SettingsRow(
                R.drawable.ic_set_link, if (state.busy) "Turning on…" else "Turn on", end = RowEnd.Empty, accent = true,
                onClick = act { askingName = true },
            )
        }
    } else {
        val status = when (state.error) {
            ShareError.NeedsSignIn -> "Sign in again to keep sharing"
            ShareError.FileRemoved -> "The shared file was removed"
            ShareError.Offline -> "Waiting for a connection"
            null -> if (state.lastUploadMillis > 0) {
                "Updated " + ageText(Instant.ofEpochMilli(state.lastUploadMillis).atZone(TOKYO).toString(), Instant.ofEpochMilli(now).atZone(TOKYO))
            } else {
                "Waiting for the first update"
            }
        }
        SettingsGroup("Partner sharing") {
            SettingsRow(
                R.drawable.ic_set_people, "Shared as ${state.name.orEmpty()}", status,
                end = RowEnd.Pill("On", active = true), iconAccent = true,
                subtitleColor = if (state.error != null) Palette.Warn else Palette.Muted,
            )
            RowDivider()
            when (state.error) {
                ShareError.NeedsSignIn -> SettingsRow(R.drawable.ic_set_shield, "Sign in again", end = RowEnd.Empty, accent = true, onClick = act(onSignIn))
                ShareError.FileRemoved -> SettingsRow(R.drawable.ic_set_link, "Make a new link", end = RowEnd.Empty, accent = true, onClick = act { confirmNewLink = true })
                else -> SettingsRow(R.drawable.ic_set_send, "Send link to partner", end = RowEnd.Empty, accent = true, onClick = act(onSendLink))
            }
            RowDivider()
            SettingsRow(R.drawable.ic_set_link, "Manage link", "Copy, new link, turn off", onClick = act { managing = true })
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
    if (managing) {
        ManageLinkDialog(
            state.link,
            onCopy = { managing = false; onCopyLink() },
            onNewLink = { managing = false; confirmNewLink = true },
            onTurnOff = { managing = false; confirmOff = true },
            onDismiss = { managing = false },
        )
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

/** Manage link: the link itself, Copy link, and the two that change it (New link, Turn off sharing). */
@Composable
internal fun ManageLinkDialog(link: String?, onCopy: () -> Unit, onNewLink: () -> Unit, onTurnOff: () -> Unit, onDismiss: () -> Unit) {
    FormDialog(
        title = "Your link",
        onDismiss = onDismiss,
        confirmLabel = "Copy link",
        onConfirm = onCopy,
        dismissLabel = "Close",
        below = {
            SecondaryButton("New link", onNewLink, Modifier.fillMaxWidth())
            SecondaryButton("Turn off sharing", onTurnOff, Modifier.fillMaxWidth())
        },
    ) {
        Text("Anyone with this link can see your progress.", color = Palette.Muted)
        Text(link.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

/** What a failed Turn on says: Drive's HTTP code (a setup mistake, see the guide) or the connection. */
fun turnOnErrorText(e: Exception): String = when (e) {
    is DriveException -> "Couldn't turn on sharing (Drive error ${e.code}). See the setup guide."
    is IOException -> "Couldn't turn on sharing. Check your connection and try again."
    else -> "Couldn't turn on sharing. See the setup guide."
}
