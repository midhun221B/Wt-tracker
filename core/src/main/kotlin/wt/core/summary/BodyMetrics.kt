package wt.core.summary

import wt.core.model.BodyComp
import wt.core.model.WeightEntry
import java.time.LocalDate

/** The measures charted on the Body tab, in display order. [lowerIsBetter] decides whether a change is good. */
enum class BodyMetric(val label: String, val unit: String, val lowerIsBetter: Boolean) {
    Weight("Weight", " kg", true),
    Bmi("BMI", "", true),
    BodyFat("Body fat", "%", true),
    Visceral("Visceral fat", "", true),
    Muscle("Muscle", " kg", false),
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
