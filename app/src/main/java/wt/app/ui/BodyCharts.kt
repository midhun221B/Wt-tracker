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
    BodyMetric.Weight -> "M6 9h12l-1.5 11h-9z M9 9a3 3 0 0 1 6 0"
    BodyMetric.Bmi -> "M3 9h18v6H3z M7 9v3 M11 9v3 M15 9v3"
    BodyMetric.BodyFat -> "M12 3c3 4 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 3-7 6-11z"
    BodyMetric.Visceral -> "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
    BodyMetric.Muscle -> "M4 9v6 M7 7v10 M17 7v10 M20 9v6 M7 12h10"
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
 * A smooth filled line through the values (Catmull-Rom curves), oldest at the left. [dots] marks each measurement;
 * the latest one is always marked. Values are spread evenly, one per measurement.
 */
@Composable
fun AreaChart(values: List<Double>, color: Color, modifier: Modifier, dots: Boolean = false, inset: Dp = 6.dp) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val pad = inset.toPx()
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0 } ?: 1.0
        val pts = values.mapIndexed { i, v ->
            val x = if (values.size == 1) size.width / 2 else pad + i * (size.width - 2 * pad) / (values.size - 1)
            Offset(x, pad + ((values.max() - v) / span).toFloat() * (size.height - 2 * pad))
        }
        if (pts.size > 1) {
            fun Path.curve() {
                moveTo(pts[0].x, pts[0].y)
                for (i in 0 until pts.size - 1) {
                    val p0 = pts.getOrElse(i - 1) { pts[i] }
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val p3 = pts.getOrElse(i + 2) { p2 }
                    cubicTo(
                        p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
                        p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
                        p2.x, p2.y,
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
            Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.width(32.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("%.1f".format(s.max), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    Text("%.1f".format(s.min), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
                AreaChart(
                    s.points.map { it.second }, color,
                    Modifier.weight(1f).fillMaxHeight().semantics {
                        contentDescription = "${s.metric.label} from ${s.fmt(s.first)} to ${s.fmt(s.last)}"
                    },
                    dots = true, inset = 8.dp,
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
            if (s.points.size > 1) AreaChart(s.points.map { it.second }, color, Modifier.size(width = 84.dp, height = 32.dp), inset = 3.dp)
            Text(
                if (s.points.size > 1) s.deltaText() else "",
                Modifier.width(68.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = s.deltaColor(), fontSize = 13.sp,
            )
        }
    }
}
