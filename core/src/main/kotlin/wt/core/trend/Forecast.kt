package wt.core.trend

import wt.core.plan.PlannedLine
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Realistic forecast compared against the plan. */
data class Forecast(
    val asOf: LocalDate,
    val fit: TrendFit,
    val trendToday: Double,
    val plannedToday: Double,
    /** Trend minus plan today; positive means heavier than planned. */
    val gapKgToday: Double,
    val goalDate: LocalDate,
    val goalKg: Double,
    val predictedAtGoal: Double,
    val bandAtGoal: Pair<Double, Double>,
    /** Predicted minus planned weight on the goal date; positive means behind plan. */
    val gapKgAtGoal: Double,
    /** Realistic date for reaching the goal weight; null if the trend never gets there. */
    val eta: LocalDate?,
    val etaEarly: LocalDate?,
    val etaLate: LocalDate?,
    /** eta − goal date in days; positive means later than planned. Null when eta is null. */
    val gapDays: Long?,
) {
    val kgPerWeek: Double get() = fit.kgPerWeek
    val lowConfidence: Boolean get() = fit.lowConfidence
}

/** Forecasts are not projected further than this; beyond it the ETA is "not reached". */
const val MAX_ETA_DAYS = 3 * 365L

fun forecast(fit: TrendFit, plan: PlannedLine): Forecast {
    val asOf = fit.origin
    val goal = plan.goal
    val trendToday = fit.valueAt(asOf)
    val plannedToday = plan.at(asOf)
    val predicted = fit.valueAt(goal.date)

    val half = t80(fit.n - 2) * fit.seSlope
    val eta = etaFor(fit.intercept, fit.slopePerDay, goal.kg, asOf)
    // A steeper slope reaches the goal earlier, a shallower one later.
    val early = etaFor(fit.intercept, fit.slopePerDay - half, goal.kg, asOf)
    val late = etaFor(fit.intercept, fit.slopePerDay + half, goal.kg, asOf)

    return Forecast(
        asOf = asOf,
        fit = fit,
        trendToday = trendToday,
        plannedToday = plannedToday,
        gapKgToday = trendToday - plannedToday,
        goalDate = goal.date,
        goalKg = goal.kg,
        predictedAtGoal = predicted,
        bandAtGoal = fit.bandAt(goal.date),
        gapKgAtGoal = predicted - plan.at(goal.date),
        eta = eta,
        etaEarly = early,
        etaLate = late,
        gapDays = eta?.let { ChronoUnit.DAYS.between(goal.date, it) },
    )
}

/** Date a line starting at [startKg] on [asOf] with [slopePerDay] reaches [targetKg]. */
fun etaFor(startKg: Double, slopePerDay: Double, targetKg: Double, asOf: LocalDate): LocalDate? {
    if (startKg <= targetKg) return asOf
    if (slopePerDay >= -1e-6) return null
    val days = ceil((targetKg - startKg) / slopePerDay).toLong()
    return if (days > MAX_ETA_DAYS) null else asOf.plusDays(days)
}
