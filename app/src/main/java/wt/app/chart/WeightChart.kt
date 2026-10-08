package wt.app.chart

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import wt.app.ui.LocalSeriesColors
import wt.core.dashboard.Dashboard
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Maps dates and kg to pixels inside the plot area. */
private class Axes(
    val start: LocalDate,
    end: LocalDate,
    val yMin: Double,
    val yMax: Double,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    private val days = ChronoUnit.DAYS.between(start, end).toFloat().coerceAtLeast(1f)
    fun x(d: LocalDate) = left + ChronoUnit.DAYS.between(start, d) / days * width
    fun y(kg: Double) = top + ((yMax - kg) / (yMax - yMin)).toFloat() * height
}

/**
 * Planned line (dashed), realistic trend with its 80 % band (solid within the data, dashed as a projection),
 * 7-day average and raw weigh-ins.
 */
@Composable
fun WeightChart(d: Dashboard, modifier: Modifier = Modifier) {
    val colors = LocalSeriesColors.current
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.merge(TextStyle(color = labelColor))

    val fit = d.fit
    val xStart = minOf(d.plan.start.date, d.weights.firstOrNull()?.date ?: d.plan.start.date)
    val xEnd = maxOf(d.plan.goal.date, d.asOf).plusDays(3)

    val core = buildList {
        addAll(d.plan.checkpoints.map { it.kg })
        addAll(d.weights.map { it.kg })
        if (fit != null) add(fit.valueAt(xEnd))
    }
    val dataMin = core.min()
    val dataMax = core.max()
    // The band can be very wide with little data; let it widen the axis by at most 2 kg each way.
    val band = fit?.bandAt(xEnd)
    val yMin = floor(minOf(dataMin, band?.first?.coerceAtLeast(dataMin - 2) ?: dataMin) - 0.3)
    val yMax = ceil(maxOf(dataMax, band?.second?.coerceAtMost(dataMax + 2) ?: dataMax) + 0.3)

    Canvas(modifier) {
        val left = 34.dp.toPx()
        val bottom = 18.dp.toPx()
        val top = 6.dp.toPx()
        val axes = Axes(xStart, xEnd, yMin, yMax, left, top, size.width - left - 6.dp.toPx(), size.height - top - bottom)

        // Horizontal grid with kg labels.
        val step = if (yMax - yMin <= 8) 1 else 2
        var kg = yMin
        while (kg <= yMax + 1e-9) {
            val y = axes.y(kg)
            drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
            val text = measurer.measure("%.0f".format(kg), labelStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 4.dp.toPx(), y - text.size.height / 2))
            kg += step
        }

        // Month ticks.
        var month = xStart.withDayOfMonth(1).plusMonths(1)
        while (month <= xEnd) {
            val x = axes.x(month)
            drawLine(gridColor, Offset(x, top), Offset(x, top + axes.height), strokeWidth = 1f)
            val text = measurer.measure(month.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()), labelStyle)
            drawText(text, topLeft = Offset(x - text.size.width / 2, top + axes.height + 2.dp.toPx()))
            month = month.plusMonths(1)
        }

        clipRect(left, top, size.width, top + axes.height) {
            // Goal level and today.
            drawLine(
                colors.planned.copy(alpha = 0.4f), Offset(left, axes.y(d.plan.goal.kg)), Offset(size.width, axes.y(d.plan.goal.kg)),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f)),
            )
            val todayX = axes.x(d.asOf)
            drawLine(labelColor.copy(alpha = 0.5f), Offset(todayX, top), Offset(todayX, top + axes.height), strokeWidth = 1.dp.toPx())

            if (fit != null) {
                val from = maxOf(fit.windowStart, xStart)
                // 80 % band from the start of the fit window to the end of the chart.
                val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= xEnd }.toList()
                val bandPath = Path().apply {
                    days.forEachIndexed { i, day ->
                        val p = Offset(axes.x(day), axes.y(fit.bandAt(day).second))
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    days.asReversed().forEach { day -> lineTo(axes.x(day), axes.y(fit.bandAt(day).first)) }
                    close()
                }
                drawPath(bandPath, colors.band)
                // Trend: solid over the data, dashed as a projection.
                drawLine(
                    colors.realistic, Offset(axes.x(from), axes.y(fit.valueAt(from))), Offset(todayX, axes.y(fit.valueAt(d.asOf))),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                )
                drawLine(
                    colors.realistic, Offset(todayX, axes.y(fit.valueAt(d.asOf))), Offset(axes.x(xEnd), axes.y(fit.valueAt(xEnd))),
                    strokeWidth = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)),
                )
            }

            polyline(d.plan.checkpoints.map { Offset(axes.x(it.date), axes.y(it.kg)) }, colors.planned, 2.5.dp.toPx(), dashed = true)
            polyline(d.movingAverage.map { Offset(axes.x(it.date), axes.y(it.value)) }, colors.average, 1.5.dp.toPx())
            d.weights.forEach { drawCircle(colors.raw, radius = 2.5.dp.toPx(), center = Offset(axes.x(it.date), axes.y(it.kg))) }
        }
    }
}

private fun DrawScope.polyline(points: List<Offset>, color: androidx.compose.ui.graphics.Color, width: Float, dashed: Boolean = false) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path, color,
        style = Stroke(width = width, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null),
    )
}

/** Small line chart for body-composition trends. */
@Composable
fun MiniLineChart(points: List<Pair<LocalDate, Double>>, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.merge(TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant))
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val values = points.map { it.second }
        val pad = maxOf((values.max() - values.min()) * 0.15, 0.5)
        val yMin = values.min() - pad
        val yMax = values.max() + pad
        val start = points.first().first
        val end = maxOf(points.last().first, start.plusDays(1))
        val axes = Axes(start, end, yMin, yMax, 30.dp.toPx(), 4.dp.toPx(), size.width - 38.dp.toPx(), size.height - 8.dp.toPx())
        listOf(values.min(), values.max()).distinct().forEach { v ->
            val y = axes.y(v)
            drawLine(gridColor, Offset(axes.left, y), Offset(size.width, y), strokeWidth = 1f)
            val t = measurer.measure("%.1f".format(v), labelStyle)
            drawText(t, topLeft = Offset(0f, y - t.size.height / 2))
        }
        val offsets = points.map { Offset(axes.x(it.first), axes.y(it.second)) }
        polyline(offsets, color, 2.dp.toPx())
        offsets.forEach { drawCircle(color, radius = 3.dp.toPx(), center = it) }
    }
}
