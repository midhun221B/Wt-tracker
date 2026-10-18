package wt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.core.summary.DayDistance
import wt.core.summary.DayKind
import wt.core.summary.WeekShare
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The picture sent to my partner after a weigh-in (360 × 450 dp), "road" style: the weight, how far down and how far
 * to go, my weigh-ins as an orange road beside the dashed plan down to the goal flag, and the run strip with the
 * streak. Everything comes from [WeekShare].
 */
@Composable
fun ShareCard(s: WeekShare, modifier: Modifier = Modifier) {
    Column(
        // Square corners: the picture sent keeps them, so apps that drop transparency show no black corners.
        modifier.size(360.dp, 450.dp).background(Palette.Background)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(20.dp, 18.sp, Modifier.weight(1f))
            Text("${s.headline} · ${shortDay(s.date)}", fontSize = 11.sp, color = Palette.Muted)
        }

        Column {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("%.1f".format(s.kg), Modifier.alignByBaseline(), style = numberStyle(72.sp, Palette.Text))
                Text("kg", Modifier.alignByBaseline(), style = numberStyle(22.sp, Palette.Muted))
            }
            Text(
                buildAnnotatedString {
                    append(downText(s.downKg))
                    withStyle(SpanStyle(color = Palette.Muted)) { append(" · ${toGoText(s.toGoKg)}") }
                },
                fontSize = 14.sp, color = Palette.Text,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Road(s)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Swatch("Me", Palette.Accent, dashed = false)
                Swatch("Plan", Palette.Planned, dashed = true)
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Card).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (s.stripIsLastWeek) "Runs last week" else "Runs this week", fontSize = 10.sp, color = Palette.Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) { s.strip.forEach { StripCell(it, Modifier.weight(1f)) } }
            }
            // The run streak (weeks in a row with 3+ runs) from 2 weeks; before that, the week's total.
            if (s.runStreakWeeks >= 2) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StreakFlame(s.runStreakWeeks, FlameLook.Solid, 26.dp)
                    Text("${s.runStreakWeeks}-week\nstreak", fontSize = 11.sp, lineHeight = 13.sp, color = Palette.Text)
                }
            } else {
                Text(
                    when (s.runs) {
                        0 -> "No runs"
                        1 -> "1 run\n${"%.1f".format(s.km)} km"
                        else -> "${s.runs} runs\n${"%.1f".format(s.km)} km"
                    },
                    fontSize = 11.sp, lineHeight = 13.sp, color = Palette.Text,
                )
            }
        }

        // The footer keeps its own height; the spacer above it takes whatever is left.
        Spacer(Modifier.weight(1f))
        Text("Estimates, not medical advice", fontSize = 9.sp, color = Palette.Muted)
    }
}

/** A legend entry: a short solid or dashed line and its name. */
@Composable
private fun Swatch(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(16.dp, 6.dp)) {
            val y = size.height / 2
            drawLine(
                color, Offset(1.dp.toPx(), y), Offset(size.width - 1.dp.toPx(), y),
                (if (dashed) 2.0 else 3.5).dp.toPx(), StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(label, fontSize = 10.sp, color = Palette.Muted)
    }
}

/** One day of the strip: an orange cell with the km for a run, the moon for rest, a dash when missed, empty otherwise. */
@Composable
private fun StripCell(day: DayDistance, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val cell = Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(6.dp))
        when (day.kind) {
            DayKind.Run -> Box(cell.background(Palette.Accent), contentAlignment = Alignment.Center) {
                Text("%.1f".format(day.km), style = numberStyle(11.sp, Palette.OnAccent))
            }
            DayKind.Rest -> Box(cell.background(Palette.CardHigh), contentAlignment = Alignment.Center) { RestMark(15.dp) }
            DayKind.Missed -> Box(cell.background(Palette.CardHigh), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 8.dp, height = 2.dp).background(Palette.Muted))
            }
            else -> Box(cell.background(Palette.CardHigh))
        }
        Text(day.date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.ENGLISH), fontSize = 9.sp, color = Palette.Muted)
    }
}

/**
 * The plan as a dashed line from the start to the goal flag, with my weigh-ins (each week's last) as a thick orange
 * road beside it, ending at a big dot for this weigh-in.
 */
@Composable
private fun Road(s: WeekShare) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Barlow, fontSize = 10.sp, color = Palette.Muted)
    val start = s.trail.first()
    val goal = s.trail.last()
    val span = ChronoUnit.DAYS.between(start.date, goal.date).coerceAtLeast(1).toFloat()
    val description = "My weigh-ins beside the plan from ${plainKg(start.kg)} to ${plainKg(goal.kg)} kg by ${dayMonth(goal.date)}; " +
        "now ${"%.1f".format(s.kg)} kg"
    Canvas(Modifier.fillMaxWidth().height(136.dp).semantics { contentDescription = description }) {
        val left = 8.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val (lo, hi) = s.kgRange.start to s.kgRange.endInclusive
        fun x(d: LocalDate) = left + (ChronoUnit.DAYS.between(start.date, d) / span).coerceIn(0f, 1f) * (right - left)
        fun y(kg: Double) = top + ((hi - kg) / (hi - lo).coerceAtLeast(0.1)).toFloat() * (bottom - top)

        drawPath(
            Path().apply { s.trail.forEachIndexed { i, c -> if (i == 0) moveTo(x(c.date), y(c.kg)) else lineTo(x(c.date), y(c.kg)) } },
            Palette.Planned,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))),
        )

        val mine = s.weighIns.map { Offset(x(it.date), y(it.kg)) }
        val you = mine.lastOrNull() ?: Offset(x(s.date), y(s.kg))
        if (mine.size > 1) {
            drawPath(
                Path().apply { mine.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } },
                Palette.Accent, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            mine.dropLast(1).forEach { drawCircle(Palette.Text, 2.dp.toPx(), it) }
        }
        drawCircle(Palette.Background, 10.dp.toPx(), you)
        drawCircle(Palette.Accent, 7.5.dp.toPx(), you)
        drawCircle(Palette.Text, 2.8.dp.toPx(), you)

        val flagX = x(goal.date)
        val flagY = y(goal.kg)
        drawLine(Palette.Text, Offset(flagX, flagY), Offset(flagX, flagY - 18.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(flagX, flagY - 18.dp.toPx())
                lineTo(flagX + 10.dp.toPx(), flagY - 14.5.dp.toPx())
                lineTo(flagX, flagY - 11.dp.toPx())
                close()
            },
            Palette.Text,
        )

        // The start label gives way to the big dot when the two would overlap (the first weeks).
        if (you.x - x(start.date) > 44.dp.toPx()) drawText(measurer, plainKg(start.kg), Offset(x(start.date) + 6.dp.toPx(), y(start.kg) - 16.dp.toPx()), label)
        val goalText = measurer.measure("${plainKg(goal.kg)} · ${dayMonth(goal.date)}", label)
        drawText(goalText, topLeft = Offset(flagX - 6.dp.toPx() - goalText.size.width, size.height - goalText.size.height))
    }
}

/**
 * Preview before sending: the card (scaled down on narrow screens, captured at full size), Cancel and Send.
 * Send captures the card, saves it to the cache and opens the share menu with the caption.
 */
@Composable
fun ShareDialog(s: WeekShare, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    // One share at a time: a second tap on Send while saving would open a second share menu.
    var sending by remember { mutableStateOf(false) }
    val inline = LocalInlineDialogs.current
    val sheet = @Composable {
        // Created inside the sheet so the layer belongs to the dialog's window, where the card is drawn.
        val layer = rememberGraphicsLayer()
        // Screenshot tests draw the dialog inline, where there is nothing to capture.
        val capture = if (inline) Modifier else Modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
        Column(
            Modifier.padding(8.dp).clip(RoundedCornerShape(24.dp)).background(Palette.Card)
                .verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Share with partner", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val scale = (maxWidth / 360.dp).coerceAtMost(1f)
                // Rounded on screen only; the capture inside records the square card.
                Box(Modifier.size(360.dp * scale, 450.dp * scale).clip(RoundedCornerShape(8.dp))) {
                    ShareCard(
                        s,
                        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
                            .then(capture),
                    )
                }
            }
            Text(s.caption, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            error?.let { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = Palette.Warn) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = Palette.Muted) }
                PrimaryButton("Send", {
                    sending = true
                    scope.launch {
                        try {
                            val uri = saveSharePng(context, layer.toImageBitmap(), "downhill-week-${s.week}")
                            context.startShare(uri, s.caption)
                            onDismiss()
                        } catch (e: Exception) {
                            error = "Couldn't share the card"
                            sending = false
                        }
                    }
                }, Modifier.weight(1f), enabled = !sending)
            }
        }
    }
    if (inline) {
        sheet()
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { sheet() }
    }
}
