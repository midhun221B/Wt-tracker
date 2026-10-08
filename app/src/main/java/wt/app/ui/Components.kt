package wt.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import wt.core.Safety
import wt.core.model.todayInTokyo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
            }
            content()
        }
    }
}

/**
 * A figure with a small label above and an optional line below.
 * [highlight] fills the tile with the accent (used once per screen for the main call to action).
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = Color.Unspecified,
    subColor: Color = Color.Unspecified,
    highlight: Boolean = false,
) {
    val container = if (highlight) Palette.Accent else Palette.Card
    val content = if (highlight) Palette.OnAccent else Palette.Text
    val muted = if (highlight) Palette.OnAccent else Palette.Muted
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = muted, fontWeight = if (highlight) FontWeight.SemiBold else null)
            Text(value, style = numberStyle(32.sp), color = if (valueColor == Color.Unspecified) content else valueColor)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.labelMedium, color = if (subColor == Color.Unspecified) muted else subColor)
            }
        }
    }
}

/** Small rounded pill, e.g. "Plan 86.4 kg". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean = false, textColor: Color = Color.Unspecified) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            textColor != Color.Unspecified -> textColor
            filled -> Palette.OnAccent
            else -> Palette.Muted
        },
        modifier = modifier
            .background(if (filled) Palette.Accent else Palette.CardHigh, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Filled dark text field used on the Today screen. */
@Composable
fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
        cursorBrush = SolidColor(Palette.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .height(48.dp)
            .background(Palette.CardHigh, RoundedCornerShape(12.dp))
            .then(if (isError) Modifier.border(1.dp, Palette.Error, RoundedCornerShape(12.dp)) else Modifier)
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
                inner()
            }
        },
    )
}

/** Ring showing progress (0–1) toward the goal, with [center] content inside. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Palette.CardHigh, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                Palette.Accent, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        center()
    }
}

/** Two tiles side by side. */
@Composable
fun TileRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content(Modifier.weight(1f))
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

/** Read-only date field that opens a date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate?, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, isError: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = date?.toString() ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Set date") },
        isError = isError,
        trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.DateRange, "Pick date") } },
        singleLine = true,
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: todayInTokyo()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** Full-width button at the top of the add dialogs that fills the entry from a screenshot instead. */
@Composable
fun ScreenshotButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Palette.Accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Accent),
    ) { Text(label) }
}

@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Text(
        "${Safety.DISCLAIMER} Suggestions never go below ${Safety.MIN_INTAKE_KCAL.toInt()} kcal/day " +
            "or above ${Safety.MAX_LOSS_KG_PER_WEEK.toInt()} kg/week of loss.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun LegendItem(color: Color, label: String, dashed: Boolean = false, thick: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
            val y = size.height / 2
            drawLine(
                color, Offset(0f, y), Offset(size.width, y),
                strokeWidth = (if (thick) 8.dp else 3.dp).toPx().coerceAtMost(size.height),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
