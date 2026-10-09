package wt.core.summary

import wt.core.model.WeightEntry
import wt.core.trend.fitTrend
import java.time.LocalDate

/**
 * Progress toward the goal on a day, the one rule every screen uses: from the first weigh-in ([startKg]) to the
 * trend on that day ([currentKg], [fromTrend]), or to the latest weight when there are too few weigh-ins to fit one.
 */
data class GoalProgress(
    val startKg: Double,
    val currentKg: Double,
    val fromTrend: Boolean,
    val lostKg: Double,
    val totalKg: Double,
    val fraction: Double,
)

/** [planStartKg] stands in for the start and current weight before the first weigh-in. */
fun goalProgress(weights: List<WeightEntry>, asOf: LocalDate, goalKg: Double, planStartKg: Double): GoalProgress {
    val sorted = weights.filter { it.date <= asOf }.sortedBy { it.date }
    val startKg = sorted.firstOrNull()?.kg ?: planStartKg
    val trend = fitTrend(sorted, asOf)?.valueAt(asOf)
    val currentKg = trend ?: sorted.lastOrNull()?.kg ?: startKg
    val totalKg = (startKg - goalKg).coerceAtLeast(0.1)
    val lostKg = (startKg - currentKg).coerceAtLeast(0.0)
    return GoalProgress(startKg, currentKg, trend != null, lostKg, totalKg, (lostKg / totalKg).coerceIn(0.0, 1.0))
}

/**
 * The "Weigh-in saved" card's numbers for the weigh-in on a day. [changeKg] is the scale change since the previous
 * weigh-in (negative = lost), null for the first. [goal] is [goalProgress] with this weigh-in, and
 * [previousFraction] the same without it, so the ring can move from one to the other.
 */
data class WeighInProgress(
    val kg: Double,
    val changeKg: Double?,
    val goal: GoalProgress,
    val previousFraction: Double,
)

/** Null when [date] has no weight. */
fun weighInProgress(weights: List<WeightEntry>, date: LocalDate, goalKg: Double, planStartKg: Double): WeighInProgress? {
    val upToDate = weights.filter { it.date <= date }.sortedBy { it.date }
    val entry = upToDate.lastOrNull { it.date == date } ?: return null
    val before = upToDate.filter { it.date < date }
    val goal = goalProgress(upToDate, date, goalKg, planStartKg)
    return WeighInProgress(
        kg = entry.kg,
        changeKg = before.lastOrNull()?.let { entry.kg - it.kg },
        goal = goal,
        previousFraction = if (before.isEmpty()) goal.fraction else goalProgress(before, date, goalKg, planStartKg).fraction,
    )
}
