package wt.core.trend

import wt.core.model.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt

data class DatedValue(val date: LocalDate, val value: Double)

/** Trailing moving average: for each logged day, the mean of entries in the [days]-day window ending that day. */
fun movingAverage(entries: List<WeightEntry>, days: Int = 7): List<DatedValue> {
    val sorted = entries.sortedBy { it.date }
    return sorted.map { e ->
        val from = e.date.minusDays(days - 1L)
        val window = sorted.filter { it.date in from..e.date }
        DatedValue(e.date, window.map { it.kg }.average())
    }
}

fun median(values: List<Double>): Double {
    require(values.isNotEmpty())
    val s = values.sorted()
    val m = s.size / 2
    return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
}

/** Theil–Sen estimator: slope is the median of all pairwise slopes; robust to outliers. */
fun theilSen(xs: DoubleArray, ys: DoubleArray): Pair<Double, Double> {
    require(xs.size == ys.size && xs.size >= 2)
    val slopes = ArrayList<Double>()
    for (i in xs.indices) for (j in i + 1 until xs.size) {
        if (xs[j] != xs[i]) slopes += (ys[j] - ys[i]) / (xs[j] - xs[i])
    }
    val slope = median(slopes)
    val intercept = median(xs.indices.map { ys[it] - slope * xs[it] })
    return slope to intercept
}

/**
 * Robust linear trend of weight. x is measured in days from [origin] (the as-of date),
 * so [intercept] is the trend weight on the as-of date.
 */
data class TrendFit(
    val origin: LocalDate,
    val slopePerDay: Double,
    val intercept: Double,
    /** Robust residual scale (corrected 1.4826 × MAD), floored at the scale resolution. */
    val sigma: Double,
    val n: Int,
    val xMean: Double,
    val sxx: Double,
    val windowStart: LocalDate,
    val lowConfidence: Boolean,
) {
    val kgPerWeek: Double get() = slopePerDay * 7
    val seSlope: Double get() = sigma / sqrt(sxx)

    fun x(date: LocalDate): Double = ChronoUnit.DAYS.between(origin, date).toDouble()

    fun valueAt(date: LocalDate): Double = intercept + slopePerDay * x(date)

    /** Standard error of the trend line itself at [date] (widens with distance from the data). */
    fun seAt(date: LocalDate): Double {
        val dx = x(date) - xMean
        return sigma * sqrt(1.0 / n + dx * dx / sxx)
    }

    /** Approximate 80 % band for the trend weight on [date]. */
    fun bandAt(date: LocalDate): Pair<Double, Double> {
        val half = t80(n - 2) * seAt(date)
        val v = valueAt(date)
        return (v - half) to (v + half)
    }
}

const val MIN_SIGMA_KG = 0.1
const val THEIL_SEN_SE_FACTOR = 1.05 // ≈ 1 / sqrt(0.91 asymptotic efficiency)

/**
 * Fits the trend on weights logged in the [windowDays] days up to [asOf].
 * If that window has fewer than [minPoints] weigh-ins, all data up to [asOf] is used
 * and the result is marked low-confidence. Returns null with fewer than 3 points or
 * when all points fall on one day.
 */
fun fitTrend(
    entries: List<WeightEntry>,
    asOf: LocalDate,
    windowDays: Int = 21,
    minPoints: Int = 7,
): TrendFit? {
    val upToAsOf = entries.filter { it.date <= asOf }.sortedBy { it.date }
    val windowFrom = asOf.minusDays(windowDays - 1L)
    var used = upToAsOf.filter { it.date >= windowFrom }
    var low = false
    if (used.size < minPoints) {
        used = upToAsOf
        low = true
    }
    if (used.size < 3) return null
    if (used.size < minPoints) low = true

    val xs = DoubleArray(used.size) { ChronoUnit.DAYS.between(asOf, used[it].date).toDouble() }
    val ys = DoubleArray(used.size) { used[it].kg }
    val xMean = xs.average()
    val sxx = xs.sumOf { (it - xMean) * (it - xMean) }
    if (sxx == 0.0) return null

    val (slope, intercept) = theilSen(xs, ys)
    val residuals = xs.indices.map { ys[it] - (intercept + slope * xs[it]) }
    val mad = median(residuals.map { abs(it - median(residuals)) })
    // 1.4826 × MAD estimates the noise SD; sqrt(n / (n − 2)) corrects for the two fitted
    // parameters and THEIL_SEN_SE_FACTOR for Theil–Sen being slightly less efficient than OLS.
    val sigma = maxOf(1.4826 * mad * sqrt(used.size / (used.size - 2.0)) * THEIL_SEN_SE_FACTOR, MIN_SIGMA_KG)

    return TrendFit(asOf, slope, intercept, sigma, used.size, xMean, sxx, used.first().date, low)
}

/** Two-sided 80 % Student-t quantile (t_0.90) for [df] degrees of freedom. */
fun t80(df: Int): Double {
    val table = sortedMapOf(
        1 to 3.078, 2 to 1.886, 3 to 1.638, 4 to 1.533, 5 to 1.476, 6 to 1.440,
        7 to 1.415, 8 to 1.397, 9 to 1.383, 10 to 1.372, 12 to 1.356, 15 to 1.341,
        20 to 1.325, 25 to 1.316, 30 to 1.310,
    )
    if (df < 1) return table.getValue(1)
    if (df > 30) return 1.2816
    return table.entries.first { it.key >= df }.value
}
