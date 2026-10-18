package wt.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.app.BuildConfig
import wt.app.R

/** The privacy policy page (also linked from Google's consent screen). */
const val PRIVACY_URL = "https://midhun221b.github.io/Wt-tracker/privacy.html"

/** What sits at the right end of a settings row. */
sealed interface RowEnd {
    /** Opens something: a pop-up, a file picker or a page. */
    data object Chevron : RowEnd
    data object Empty : RowEnd
    data class Toggle(val on: Boolean, val onChange: (Boolean) -> Unit) : RowEnd
    /** A small status label, orange when [active] ("On"), grey otherwise ("Off"). */
    data class Pill(val text: String, val active: Boolean) : RowEnd
}

/**
 * A settings section: a small grey [label], then its rows on one dark rounded group (separate rows with
 * [RowDivider]), then an optional [footer] under the group.
 */
@Composable
fun SettingsGroup(
    label: String,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.padding(start = 4.dp).semantics { heading() }, fontSize = 14.sp, color = Palette.Muted)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card), content = rows)
        footer?.invoke(this)
    }
}

/** The line between two rows of a [SettingsGroup]. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF232B31)))
}

/** A small note under a [SettingsGroup]. */
@Composable
fun GroupNote(text: String, color: Color = Palette.Muted) {
    Text(text, Modifier.padding(horizontal = 4.dp), fontSize = 13.sp, lineHeight = 19.sp, color = color)
}

/**
 * One settings row: [icon], [title] with an optional [subtitle], an optional [value] and [end]. [accent] marks the
 * section's main action (orange icon and title); [iconAccent] makes only the icon orange. Rows without [onClick]
 * are plain information.
 */
@Composable
fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    end: RowEnd = RowEnd.Chevron,
    accent: Boolean = false,
    iconAccent: Boolean = accent,
    subtitleColor: Color = Palette.Muted,
    onClick: (() -> Unit)? = null,
) {
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (subtitle != null) 68.dp else 56.dp).then(click)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = if (iconAccent) Palette.Accent else Palette.Muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title, fontSize = 17.sp, color = if (accent) Palette.Accent else Palette.Text,
                fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal,
            )
            subtitle?.let { Text(it, fontSize = 14.sp, lineHeight = 19.sp, color = subtitleColor) }
        }
        value?.let { Text(it, fontSize = 16.sp, color = Palette.Muted) }
        when (end) {
            RowEnd.Chevron -> Icon(painterResource(R.drawable.ic_set_chevron), null, Modifier.size(18.dp), tint = Palette.Muted)
            RowEnd.Empty -> Unit
            is RowEnd.Toggle -> Switch(checked = end.on, onCheckedChange = end.onChange)
            is RowEnd.Pill -> Text(
                end.text,
                Modifier.clip(RoundedCornerShape(50)).background(if (end.active) Palette.Accent.copy(alpha = 0.14f) else Palette.CardHigh)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (end.active) Palette.Accent else Palette.Muted,
            )
        }
    }
}

/** Settings' last section on both phones: the app and version, the privacy policy, [note] and the disclaimer. */
@Composable
fun AboutGroup(note: String) {
    val uri = LocalUriHandler.current
    SettingsGroup(
        "About",
        footer = {
            GroupNote(note)
            Disclaimer(Modifier.padding(horizontal = 4.dp))
        },
    ) {
        SettingsRow(R.drawable.ic_set_info, APP_NAME, value = BuildConfig.VERSION_NAME, end = RowEnd.Empty)
        RowDivider()
        SettingsRow(R.drawable.ic_set_shield, "Privacy policy", onClick = { uri.openUri(PRIVACY_URL) })
    }
}
