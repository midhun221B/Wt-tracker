package wt.core.plan

import wt.core.Safety
import wt.core.model.Checkpoint
import wt.core.model.WeightEntry
import wt.core.trend.fitTrend
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class RebaselineResult(
    val startKg: Double,
    /** True when the start is the trend weight; false when it is the latest raw weigh-in. */
    val fromTrend: Boolean,
    /** New checkpoints (start, monthly points, goal); empty when the goal date has passed. */
    val checkpoints: List<Checkpoint>,
    /** Loss per week the new line requires; null when the goal date has passed. */
    val requiredKgPerWeek: Double?,
    /** Above 0.7 kg/week. */
    val unrealistic: Boolean,
    /** Above the 1 kg/week safety cap. */
    val exceedsSafeMax: Boolean,
)

/**
 * Builds a new planned line from the current weight to the same goal.
 * Uses the trend weight when the fit is confident, otherwise the latest weigh-in.
 */
fun rebaseline(weights: List<WeightEntry>, asOf: LocalDate, goalDate: LocalDate, goalKg: Double): RebaselineResult {
    val fit = fitTrend(weights, asOf)?.takeUnless { it.lowConfidence }
    val latest = weights.filter { it.date <= asOf }.maxByOrNull { it.date }
        ?: throw IllegalStateException("Log a weight before re-baselining")
    val startKg = fit?.valueAt(asOf) ?: latest.kg

    val days = ChronoUnit.DAYS.between(asOf, goalDate)
    if (days <= 0) return RebaselineResult(startKg, fit != null, emptyList(), null, unrealistic = true, exceedsSafeMax = true)

    val perDay = (startKg - goalKg) / days
    val points = mutableListOf(Checkpoint(asOf, startKg))
    var month = asOf.plusMonths(1)
    while (ChronoUnit.DAYS.between(month, goalDate) >= 7) {
        points += Checkpoint(month, startKg - perDay * ChronoUnit.DAYS.between(asOf, month))
        month = month.plusMonths(1)
    }
    points += Checkpoint(goalDate, goalKg)

    val perWeek = (perDay * 7).coerceAtLeast(0.0)
    return RebaselineResult(
        startKg = startKg,
        fromTrend = fit != null,
        checkpoints = points,
        requiredKgPerWeek = perWeek,
        unrealistic = perWeek > Safety.UNREALISTIC_KG_PER_WEEK,
        exceedsSafeMax = perWeek > Safety.MAX_LOSS_KG_PER_WEEK,
    )
}
