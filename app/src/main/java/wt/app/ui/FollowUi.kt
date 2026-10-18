package wt.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import wt.app.R
import wt.core.io.FollowError
import wt.core.io.driveFileId
import wt.core.io.driveLink
import wt.core.model.TOKYO
import java.time.ZonedDateTime

/** What each follow problem says, on the setup step and on the follow bar. */
fun followErrorText(e: FollowError): String = when (e) {
    FollowError.NotALink -> "This isn't a $APP_NAME share link"
    FollowError.LinkDead -> "This link no longer works. Ask for a new one."
    FollowError.Unreadable -> "Couldn't read the latest update"
    FollowError.NewerVersion -> "Update $APP_NAME to read this"
    FollowError.Offline -> "No connection. Try again when you're online."
}

/**
 * The slim line above the tabs on a phone that follows someone: whose data it is and how old, or what went wrong
 * (the last copy stays on screen), with a Refresh button.
 */
@Composable
fun FollowBar(name: String, updatedAt: String?, error: FollowError?, busy: Boolean, onRefresh: () -> Unit, now: ZonedDateTime = ZonedDateTime.now(TOKYO)) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp))
            .background(Palette.Card).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                "Following $name" + (updatedAt?.let { " · updated ${ageText(it, now)}" } ?: ""),
                style = MaterialTheme.typography.labelLarge, color = Palette.Text,
            )
            error?.let { Text(followErrorText(it), style = MaterialTheme.typography.labelMedium, color = Palette.Warn) }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), color = Palette.Accent, strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Refresh, "Refresh", tint = Palette.Muted) }
        }
    }
}

/**
 * First-run "Follow a partner": paste the link my partner sent (the whole WhatsApp message works too). Nothing is
 * stored until the link reads.
 */
@Composable
fun FollowSetupStep(error: FollowError?, busy: Boolean, onFollow: (String) -> Unit, onBack: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    Surface(Modifier.fillMaxSize(), color = Palette.Background, contentColor = Palette.Text) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Palette.Card, contentColor = Palette.Text),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back") }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Follow a partner", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Paste the link your partner sent you. This phone then shows their weigh-ins, runs and plan, read-only, " +
                        "and updates each time you open the app.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Link from your partner") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, focusedLabelColor = Palette.Accent, cursorColor = Palette.Accent),
                )
                SecondaryButton("Paste", { clipboard.getText()?.text?.let { link = it } }, Modifier.fillMaxWidth())
                error?.let { Text(followErrorText(it), style = MaterialTheme.typography.bodyMedium, color = Palette.Error) }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (busy) "Following…" else "Follow", { onFollow(link) }, Modifier.fillMaxWidth(), enabled = link.isNotBlank() && !busy)
        }
    }
}

/** Settings on a phone that follows someone: whose data, how fresh, the link, Stop following, and About. */
@Composable
fun FollowSettings(name: String, updatedAt: String?, link: String, onStop: () -> Unit, modifier: Modifier = Modifier, now: ZonedDateTime = ZonedDateTime.now(TOKYO)) {
    var confirm by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SettingsGroup("Following", footer = { GroupNote(driveFileId(link)?.let(::driveLink) ?: link) }) {
            SettingsRow(
                R.drawable.ic_set_people, "Following $name", updatedAt?.let { "Updated ${ageText(it, now)}" } ?: "Not updated yet",
                end = RowEnd.Pill("Read-only", active = false), iconAccent = true,
            )
            RowDivider()
            SettingsRow(R.drawable.ic_set_stop, "Stop following", end = RowEnd.Empty, onClick = { confirm = true })
        }
        AboutGroup("This phone shows $name's data, read-only. Dates and times use Japan time.")
    }
    if (confirm) {
        FormDialog(
            title = "Stop following?",
            onDismiss = { confirm = false },
            confirmLabel = "Stop following",
            onConfirm = { confirm = false; onStop() },
        ) { Text("Remove $name's data from this phone?", color = Palette.Muted) }
    }
}
