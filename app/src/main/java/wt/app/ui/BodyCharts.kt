package wt.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.core.summary.BodyMetric
import wt.core.summary.MetricSeries
import wt.core.summary.chartRange
import wt.core.summary.monotoneSlopes
import kotlin.math.abs

/** Each measure's colour; they also differ in lightness, and every card names its measure. */
fun BodyMetric.color(): Color = when (this) {
    BodyMetric.Weight -> Palette.Accent
    BodyMetric.Bmi -> Color(0xFFF2C94C)
    BodyMetric.BodyFat -> Color(0xFFFF7A7A)
    BodyMetric.Visceral -> Color(0xFFB48CF2)
    BodyMetric.Muscle -> Color(0xFF5CC98A)
}

/** Stroke icons drawn on a 24-unit grid. */
private fun BodyMetric.iconPath(): String = when (this) {
    // A bathroom scale with its dial.
    BodyMetric.Weight -> "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M8.5 11a3.5 3.5 0 0 1 7 0z M12 11l1.5-2"
    // A gauge: BMI is an index read against ranges.
    BodyMetric.Bmi -> "M4.5 16a7.5 7.5 0 0 1 15 0z M12 16l3.5-4.5"
    // A drop of fat.
    BodyMetric.BodyFat -> "M12 3c3 4 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 3-7 6-11z"
    // A torso with the fat around the organs.
    BodyMetric.Visceral -> "M8 3c0 3-3 5-3 10a7 7 0 0 0 14 0c0-5-3-7-3-10 M12 12a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"
    // A dumbbell.
    BodyMetric.Muscle -> "M3 10v4 M6 7v10 M9 6v12 M15 6v12 M18 7v10 M21 10v4 M9 12h6"
}

/** The measure's icon in a filled circle of its colour. */
@Composable
fun MetricIcon(metric: BodyMetric, size: Dp) {
    val path = PathParser().parsePathString(metric.iconPath()).toPath()
    Box(Modifier.size(size).background(metric.color(), CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.5f)) {
            scale(this.size.width / 24f, pivot = Offset.Zero) {
                drawPath(path, Palette.Background, style = Stroke(2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** "↓ −1.1 kg", "↑ +0.3%" or "0.0": the change with an arrow and a real minus sign. */
fun MetricSeries.deltaText(): String {
    val tenths = Math.round(change * 10)
    val value = "%.1f".format(abs(tenths) / 10.0) + metric.unit
    return when {
        tenths < 0 -> "↓ −$value"
        tenths > 0 -> "↑ +$value"
        else -> value
    }
}

/** Orange when the change goes the good way, amber when not, grey when there is no direction yet. */
fun MetricSeries.deltaColor(): Color = when (improving) {
    true -> Palette.Accent
    false -> Palette.Warn
    null -> Palette.Muted
}

private fun MetricSeries.fmt(v: Double) = "%.1f".format(v) + metric.unit

/**
 * A smooth filled line through the values, oldest at the left, spread evenly one per measurement. The curve never
 * overshoots between two dots ([monotoneSlopes]). [range] is the value range from bottom to top (default: the data's).
 * [dots] marks each measurement; the latest one is always marked.
 */
@Composable
fun AreaChart(
    values: List<Double>,
    color: Color,
    modifier: Modifier,
    dots: Boolean = false,
    inset: Dp = 6.dp,
    range: ClosedFloatingPointRange<Double>? = null,
) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val pad = inset.toPx()
        val lo = range?.start ?: values.min()
        val hi = range?.endInclusive ?: values.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val plotH = size.height - 2 * pad
        val step = if (values.size > 1) (size.width - 2 * pad) / (values.size - 1) else 0f
        val pts = values.mapIndexed { i, v ->
            val x = if (values.size == 1) size.width / 2 else pad + i * step
            Offset(x, pad + ((hi - v) / span).toFloat() * plotH)
        }
        if (pts.size > 1) {
            // Slope in value per step → pixels per step (y grows downwards).
            val slopes = monotoneSlopes(values).map { (-it / span).toFloat() * plotH }
            fun Path.curve() {
                moveTo(pts[0].x, pts[0].y)
                for (i in 0 until pts.size - 1) {
                    cubicTo(
                        pts[i].x + step / 3, pts[i].y + slopes[i] / 3,
                        pts[i + 1].x - step / 3, pts[i + 1].y - slopes[i + 1] / 3,
                        pts[i + 1].x, pts[i + 1].y,
                    )
                }
            }
            val line = Path().apply { curve() }
            val area = Path().apply {
                curve()
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(area, color.copy(alpha = 0.2f))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val marked = if (dots) pts else listOf(pts.last())
        marked.forEach {
            drawCircle(Palette.Card, 4.dp.toPx(), it)
            drawCircle(color, 4.dp.toPx(), it, style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * The big chart for the chosen measure: its icon and name, the latest value with the change since the first, a
 * smooth filled chart with a dot per measurement and its range at the side, and min / max / average.
 */
@Composable
fun BodyChartCard(s: MetricSeries) {
    val color = s.metric.color()
    SectionCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricIcon(s.metric, 32.dp)
            Text(s.metric.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${dayMonth(s.points.first().first)} – ${dayMonth(s.points.last().first)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.fmt(s.last), style = numberStyle(40.sp))
            if (s.points.size > 1) {
                Text(
                    "${s.deltaText()} since ${dayMonth(s.points.first().first)}",
                    Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = s.deltaColor(),
                )
            }
        }
        if (s.points.size < 2) {
            Text("The chart starts after the second ${if (s.metric == BodyMetric.Weight || s.metric == BodyMetric.Bmi) "weigh-in" else "measurement"}.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        } else {
            // At least the measure's minimum range, so a small change doesn't look like a cliff.
            val range = chartRange(s.min, s.max, s.metric)
            Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.width(32.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("%.1f".format(range.endInclusive), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    Text("%.1f".format(range.start), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
                AreaChart(
                    s.points.map { it.second }, color,
                    Modifier.weight(1f).fillMaxHeight().semantics {
                        contentDescription = "${s.metric.label} from ${s.fmt(s.first)} to ${s.fmt(s.last)}"
                    },
                    dots = true, inset = 8.dp, range = range,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTileSmall("Min", s.fmt(s.min), Modifier.weight(1f))
                StatTileSmall("Max", s.fmt(s.max), Modifier.weight(1f))
                StatTileSmall("Average", s.fmt(s.avg), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTileSmall(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Background).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** One measure as a tappable row: icon, name, first → latest, a mini chart and the change. [selected] is outlined. */
@Composable
fun MetricCard(s: MetricSeries, selected: Boolean, onClick: () -> Unit) {
    val color = s.metric.color()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { contentDescription = "Chart ${s.metric.label}" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
        border = BorderStroke(1.5.dp, if (selected) color else Palette.Card),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricIcon(s.metric, 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.metric.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (s.points.size > 1) "${s.fmt(s.first)} → ${s.fmt(s.last)}" else s.fmt(s.last),
                    style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                )
            }
            if (s.points.size > 1) {
                AreaChart(s.points.map { it.second }, color, Modifier.size(width = 84.dp, height = 32.dp), inset = 3.dp, range = chartRange(s.min, s.max, s.metric))
            }
            Text(
                if (s.points.size > 1) s.deltaText() else "",
                Modifier.width(68.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = s.deltaColor(), fontSize = 13.sp,
            )
        }
    }
}
