package wt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val Step = 14.dp // one 0.1 kg tick
private const val MIN_TENTHS = 300
private const val MAX_TENTHS = 2500

/**
 * Horizontal ruler for the weekly weight, in 0.1 kg ticks under a fixed orange centre mark. Dragging moves the
 * scale (drag right = lighter) with a light haptic on each tick. The plan for the day shows as a blue dashed line
 * and last week's weight as a grey line, each labelled on its own row above the ticks.
 */
@Composable
fun WeightRuler(kg: Double, onKg: (Double) -> Unit, planKg: Double?, lastKg: Double?, modifier: Modifier = Modifier) {
    val tenths = (kg * 10).roundToInt().coerceIn(MIN_TENTHS, MAX_TENTHS)
    val current by rememberUpdatedState(tenths)
    val setKg by rememberUpdatedState(onKg)
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelMedium

    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .pointerInput(Unit) {
                val stepPx = Step.toPx()
                var start = 0
                var sent = 0
                var dragged = 0f
                detectHorizontalDragGestures(onDragStart = { start = current; sent = current; dragged = 0f }) { change, dx ->
                    change.consume()
                    dragged += dx
                    val next = (start - (dragged / stepPx).roundToInt()).coerceIn(MIN_TENTHS, MAX_TENTHS)
                    if (next != sent) {
                        sent = next
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        setKg(next / 10.0)
                    }
                }
            }
            .semantics {
                contentDescription = "Weight ruler"
                stateDescription = "%.1f kg".format(tenths / 10.0)
                progressBarRangeInfo = ProgressBarRangeInfo(tenths / 10f, MIN_TENTHS / 10f..MAX_TENTHS / 10f, steps = MAX_TENTHS - MIN_TENTHS - 1)
                setProgress { v -> setKg((v * 10).roundToInt().coerceIn(MIN_TENTHS, MAX_TENTHS) / 10.0); true }
            },
    ) {
        val step = Step.toPx()
        val cx = size.width / 2
        val baseline = size.height - 30.dp.toPx()
        val markTop = 40.dp.toPx()
        val reach = (cx / step).toInt() + 1

        // Ticks: whole kilograms tall and labelled, half kilograms medium, tenths short.
        for (i in -reach..reach) {
            val v = tenths + i
            if (v !in MIN_TENTHS..MAX_TENTHS) continue
            val x = cx + i * step
            val (h, color) = when {
                v % 10 == 0 -> 32.dp.toPx() to Palette.Text.copy(alpha = 0.8f)
                v % 5 == 0 -> 24.dp.toPx() to Palette.Outline
                else -> 14.dp.toPx() to Palette.Outline
            }
            drawLine(color, Offset(x, baseline - h), Offset(x, baseline), 2.dp.toPx(), StrokeCap.Round)
            // Skip labels that would be cut off at the edges.
            if (v % 10 == 0 && abs(x - cx) < cx - 16.dp.toPx()) {
                centeredText(measurer, "${v / 10}", x, baseline + 6.dp.toPx(), label.copy(color = Palette.Muted))
            }
        }

        // Fade the ticks into the card at both edges; the marks below are drawn over it so they stay readable.
        val fade = size.width * 0.2f
        drawRect(Brush.horizontalGradient(listOf(Palette.Card, Color.Transparent), 0f, fade))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Palette.Card), size.width - fade, size.width))

        // Last week (grey, top label row) and the plan (blue dashed, second row). Out of view, the label sits at
        // that edge with an arrow so the mark is never lost.
        fun xOf(kgMark: Double) = cx + ((kgMark * 10).roundToInt() - tenths) * step
        fun mark(kgMark: Double, name: String, color: Color, top: Float, dashed: Boolean) {
            val x = xOf(kgMark)
            val style = label.copy(color = color)
            if (abs(x - cx) < cx - 8.dp.toPx()) {
                drawLine(
                    color, Offset(x, markTop), Offset(x, baseline), 2.dp.toPx(),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                )
                // Centred on the mark, but kept inside the ruler.
                val layout = measurer.measure("$name %.1f".format(kgMark), style)
                drawText(layout, topLeft = Offset((x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), top))
            } else {
                val layout = measurer.measure(if (x < cx) "‹ $name %.1f".format(kgMark) else "$name %.1f ›".format(kgMark), style)
                val left = if (x < cx) 0f else size.width - layout.size.width
                drawText(layout, topLeft = Offset(left, top))
            }
        }
        lastKg?.let { mark(it, "Last week", Palette.Muted, 2.dp.toPx(), dashed = false) }
        planKg?.let { mark(it, "Plan", Palette.Planned, 20.dp.toPx(), dashed = true) }

        // Fixed centre mark on top.
        drawLine(Palette.Accent, Offset(cx, baseline - 60.dp.toPx()), Offset(cx, baseline + 4.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
    }
}

private fun DrawScope.centeredText(measurer: TextMeasurer, text: String, x: Float, top: Float, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, top))
}
