package wt.core.summary

import wt.core.model.WeightEntry
import java.time.LocalDate

/**
 * Progress toward the goal right after the weigh-in on a day, from raw weights (not the trend), for the
 * "Weigh-in saved" card. [changeKg] is the change since the previous weigh-in (negative = lost), null for the first.
 * [fraction] and [previousFraction] are the share of start → goal done after this and the previous weigh-in, 0..1.
 */
data class WeighInProgress(
    val kg: Double,
    val changeKg: Double?,
    val lostKg: Double,
    val totalKg: Double,
    val fraction: Double,
    val previousFraction: Double,
)

/** Null when [date] has no weight. The start weight is the first weigh-in. */
fun weighInProgress(weights: List<WeightEntry>, date: LocalDate, goalKg: Double): WeighInProgress? {
    val sorted = weights.sortedBy { it.date }
    val entry = sorted.lastOrNull { it.date == date } ?: return null
    val startKg = sorted.first().kg
    val previous = sorted.lastOrNull { it.date < date }
    val totalKg = (startKg - goalKg).coerceAtLeast(0.1)
    fun done(kg: Double) = ((startKg - kg) / totalKg).coerceIn(0.0, 1.0)
    return WeighInProgress(
        kg = entry.kg,
        changeKg = previous?.let { entry.kg - it.kg },
        lostKg = (startKg - entry.kg).coerceAtLeast(0.0),
        totalKg = totalKg,
        fraction = done(entry.kg),
        previousFraction = previous?.let { done(it.kg) } ?: done(entry.kg),
    )
}
