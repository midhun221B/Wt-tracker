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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.abs

/**
 * The picture sent to my partner after a weigh-in (360 × 450 dp): the goal ring with the scale weight, the plan as a
 * downhill trail with my weigh-ins beside it, and last week's run strip. Everything comes from [WeekShare].
 */
@Composable
fun ShareCard(s: WeekShare, modifier: Modifier = Modifier) {
    Column(
        // Square corners: the picture sent keeps them, so apps that drop transparency show no black corners.
        modifier.size(360.dp, 450.dp).background(Palette.Background)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRing(s.fraction.toFloat(), Modifier.size(132.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(s.kg), style = numberStyle(38.sp, Palette.Text))
                    Text("kg on the scale", fontSize = 10.sp, color = Palette.Muted)
                    val change = s.changeKg
                    val since = s.previousDate
                    if (change != null && since != null) {
                        Text(
                            "${signedTenth(change)} kg since ${dayMonth(since)}",
                            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Palette.Accent.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent, maxLines = 1,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.headline, style = numberStyle(28.sp, Palette.Text))
                Text("Weigh-in · ${shortDay(s.date)}", fontSize = 13.sp, color = Palette.Muted)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Downhill to ${plainKg(s.trail.last().kg)} kg", Modifier.weight(1f), fontSize = 11.sp, color = Palette.Muted)
                Text(
                    when (s.weeksToGo) {
                        0 -> "Goal week"
                        1 -> "1 week to go"
                        else -> "${s.weeksToGo} weeks to go"
                    },
                    fontSize = 11.sp, color = Palette.Muted,
                )
            }
            Trail(s)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Card).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(if (s.stripIsLastWeek) "Last week" else "This week", Modifier.weight(1f), fontSize = 12.sp, color = Palette.Muted)
                Text(
                    when (s.runs) {
                        0 -> "No runs"
                        1 -> "1 run · ${"%.1f".format(s.km)} km"
                        else -> "${s.runs} runs · ${"%.1f".format(s.km)} km"
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
                )
            }
            Row(Modifier.fillMaxWidth()) { s.strip.forEach { StripDay(it, Modifier.weight(1f)) } }
        }

        // The footer keeps its own height; the spacer above it takes whatever is left.
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            Text("Estimates, not medical advice", Modifier.weight(1f), fontSize = 10.sp, color = Palette.Muted)
            Text("Wt-tracker", fontSize = 10.sp, color = Palette.Muted)
        }
    }
}

/** One day of the strip: orange ✓ for a run, the moon for rest, a dash when missed, an empty circle otherwise. */
@Composable
private fun StripDay(day: DayDistance, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        when (day.kind) {
            DayKind.Run -> Box(Modifier.size(28.dp).background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Palette.OnAccent)
            }
            DayKind.Rest -> RestMark(28.dp)
            DayKind.Missed -> Box(Modifier.size(28.dp).background(Palette.CardHigh, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 9.dp, height = 2.dp).background(Palette.Muted))
            }
            else -> Box(Modifier.size(28.dp).background(Palette.CardHigh, CircleShape))
        }
        Text(day.date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.ENGLISH), fontSize = 10.sp, color = Palette.Muted)
    }
}

/**
 * The plan as a downhill trail (dashed, on a darker hillside) from the start to the goal flag, a post per week,
 * my weigh-ins as a solid orange line ending at "You", and the plan marker once the trend exists.
 */
@Composable
private fun Trail(s: WeekShare) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Barlow, fontSize = 11.sp, color = Palette.Muted)
    val strong = label.copy(color = Palette.Text, fontWeight = FontWeight.SemiBold)
    val start = s.trail.first()
    val goal = s.trail.last()
    val span = ChronoUnit.DAYS.between(start.date, goal.date).coerceAtLeast(1).toFloat()
    val description = "The plan as a downhill trail from ${plainKg(start.kg)} to ${plainKg(goal.kg)} kg by ${dayMonth(goal.date)}; " +
        "you are at ${"%.1f".format(s.kg)} kg" + (s.planMarkerKg?.let { ", where the plan is at ${"%.1f".format(it)} kg" } ?: "")
    Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = description }) {
        val left = 16.dp.toPx()
        val right = size.width - 20.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val (lo, hi) = s.kgRange.start to s.kgRange.endInclusive
        fun x(d: LocalDate) = left + (ChronoUnit.DAYS.between(start.date, d) / span).coerceIn(0f, 1f) * (right - left)
        fun y(kg: Double) = top + ((hi - kg) / (hi - lo).coerceAtLeast(0.1)).toFloat() * (bottom - top)

        val trail = Path().apply {
            s.trail.forEachIndexed { i, c -> if (i == 0) moveTo(x(c.date), y(c.kg)) else lineTo(x(c.date), y(c.kg)) }
        }
        val hill = Path().apply {
            s.trail.forEachIndexed { i, c -> if (i == 0) moveTo(x(c.date), y(c.kg)) else lineTo(x(c.date), y(c.kg)) }
            lineTo(x(goal.date), size.height)
            lineTo(x(start.date), size.height)
            close()
        }
        drawPath(hill, Palette.Card)
        drawPath(trail, Palette.Planned, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))

        s.posts.forEach { p ->
            val current = p.date == s.currentPost.date
            val px = x(p.date)
            val py = y(p.kg)
            drawLine(if (current) Palette.Accent else Palette.CardHigh, Offset(px, py + 2.dp.toPx()), Offset(px, py + 8.dp.toPx()), (if (current) 2.5 else 2.0).dp.toPx(), StrokeCap.Round)
        }

        val mine = s.weighIns.map { Offset(x(it.date), y(it.kg)) }
        if (mine.size > 1) {
            drawPath(
                Path().apply { mine.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } },
                Palette.Accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        drawCircle(Palette.Text, 3.5.dp.toPx(), Offset(x(start.date), y(start.kg)))

        s.planMarkerKg?.let { kg ->
            val o = Offset(x(s.date), y(kg))
            drawCircle(Palette.Background, 5.dp.toPx(), o)
            drawCircle(Palette.Planned, 5.dp.toPx(), o, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()))))
            drawText(measurer, "plan ${"%.1f".format(kg)}", Offset(o.x + 9.dp.toPx(), o.y - 7.dp.toPx()), label)
        }

        val you = mine.lastOrNull() ?: Offset(x(s.date), y(s.kg))
        drawCircle(Palette.Accent, 7.dp.toPx(), you)
        drawCircle(Palette.Text, 7.dp.toPx(), you, style = Stroke(2.5.dp.toPx()))

        val flagX = x(goal.date)
        val flagY = y(goal.kg)
        drawLine(Palette.Text, Offset(flagX, flagY), Offset(flagX, flagY - 26.dp.toPx()), 2.dp.toPx())
        drawPath(
            Path().apply {
                moveTo(flagX, flagY - 26.dp.toPx())
                lineTo(flagX + 17.dp.toPx(), flagY - 20.dp.toPx())
                lineTo(flagX, flagY - 14.dp.toPx())
                close()
            },
            Palette.Text,
        )

        // The start label gives way to "You" when the two would overlap (the first weeks).
        if (you.x - x(start.date) > 70.dp.toPx()) drawText(measurer, plainKg(start.kg), Offset(x(start.date), y(start.kg) - 18.dp.toPx()), label)
        val youText = measurer.measure("You · ${"%.1f".format(s.kg)}", strong)
        drawText(youText, topLeft = Offset((you.x - youText.size.width / 2f).coerceIn(0f, size.width - youText.size.width), you.y - 13.dp.toPx() - youText.size.height))
        val goalText = measurer.measure("${plainKg(goal.kg)} kg · ${dayMonth(goal.date)}", strong)
        drawText(goalText, topLeft = Offset(flagX - 4.dp.toPx() - goalText.size.width, size.height - goalText.size.height - 2.dp.toPx()))
    }
}

/** "−0.4", "+0.3" or "0.0": one decimal with a real minus sign. */
private fun signedTenth(v: Double): String {
    val tenths = Math.round(v * 10)
    val sign = if (tenths < 0) "−" else if (tenths > 0) "+" else ""
    return sign + "%.1f".format(abs(tenths) / 10.0)
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
                            val uri = saveSharePng(context, layer.toImageBitmap(), "wt-week-${s.week}")
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
