package wt.core.summary

import wt.core.model.BodyComp
import wt.core.model.WeightEntry
import java.time.LocalDate

/**
 * The measures charted on the Body tab, in display order. [lowerIsBetter] decides whether a change is good;
 * [minChartSpan] is the smallest range a chart shows, so a small change doesn't fill the whole height.
 */
enum class BodyMetric(val label: String, val unit: String, val lowerIsBetter: Boolean, val minChartSpan: Double) {
    Weight("Weight", " kg", true, 3.0),
    Bmi("BMI", "", true, 1.0),
    BodyFat("Body fat", "%", true, 3.0),
    Visceral("Visceral fat", "", true, 3.0),
    Muscle("Muscle", " kg", false, 3.0),
}

/** The chart's value range: the data's own when it spans at least [BodyMetric.minChartSpan], else that span centred on it. */
fun chartRange(min: Double, max: Double, metric: BodyMetric): ClosedFloatingPointRange<Double> {
    if (max - min >= metric.minChartSpan) return min..max
    val mid = (min + max) / 2
    return (mid - metric.minChartSpan / 2)..(mid + metric.minChartSpan / 2)
}

/**
 * Slopes (per step) for a smooth curve through evenly spaced [ys] that never overshoots: flat at a turning point,
 * and limited so each piece stays between its two values (Fritsch–Carlson monotone cubic).
 */
fun monotoneSlopes(ys: List<Double>): List<Double> {
    if (ys.size < 2) return List(ys.size) { 0.0 }
    val d = ys.zipWithNext { a, b -> b - a }
    val m = MutableList(ys.size) { i ->
        when (i) {
            0 -> d.first()
            ys.lastIndex -> d.last()
            else -> if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
        }
    }
    for (i in d.indices) {
        if (d[i] == 0.0) {
            m[i] = 0.0
            m[i + 1] = 0.0
            continue
        }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val h = a * a + b * b
        if (h > 9) {
            val t = 3 / Math.sqrt(h)
            m[i] = t * a * d[i]
            m[i + 1] = t * b * d[i]
        }
    }
    return m
}

/** One measure over time, oldest first, with the numbers its card shows. */
data class MetricSeries(val metric: BodyMetric, val points: List<Pair<LocalDate, Double>>) {
    val first: Double get() = points.first().second
    val last: Double get() = points.last().second
    val min: Double get() = points.minOf { it.second }
    val max: Double get() = points.maxOf { it.second }
    val avg: Double get() = points.sumOf { it.second } / points.size
    /** Last minus first. */
    val change: Double get() = last - first

    /** True when the change goes the good way, false when not, null with fewer than two points or no change. */
    val improving: Boolean?
        get() = when {
            points.size < 2 || Math.round(change * 10) == 0L -> null
            metric.lowerIsBetter -> change < 0
            else -> change > 0
        }
}

/**
 * The Body tab's measures. Weight (and BMI from it, given [heightCm]) uses each Monday–Sunday week's last weigh-in, so
 * extra weigh-ins don't zigzag the chart; body fat, visceral fat and muscle come from the scale measurements.
 * A measure without data is left out.
 */
fun bodyMetrics(weights: List<WeightEntry>, body: List<BodyComp>, heightCm: Double): List<MetricSeries> {
    val weekly = weights.groupBy { weekStart(it.date) }.values.map { week -> week.maxBy { it.date } }.sortedBy { it.date }
    val sortedBody = body.sortedBy { it.date }
    val m = heightCm / 100
    return listOfNotNull(
        MetricSeries(BodyMetric.Weight, weekly.map { it.date to it.kg }),
        if (heightCm > 0) MetricSeries(BodyMetric.Bmi, weekly.map { it.date to it.kg / (m * m) }) else null,
        MetricSeries(BodyMetric.BodyFat, sortedBody.map { it.date to it.fatPct }),
        MetricSeries(BodyMetric.Visceral, sortedBody.map { it.date to it.visceral }),
        MetricSeries(BodyMetric.Muscle, sortedBody.map { it.date to it.muscleKg }),
    ).filter { it.points.isNotEmpty() }
}
