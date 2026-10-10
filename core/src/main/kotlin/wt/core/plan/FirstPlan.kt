package wt.core.plan

import wt.core.model.WeightEntry
import java.time.LocalDate
import kotlin.math.ceil

/**
 * The plan made during first-run setup: from today's weight to the goal, with monthly checkpoints.
 * Same shape and safety flags as a re-baseline (above 0.7 kg/week unrealistic, above 1 kg/week not allowed).
 */
fun firstPlan(today: LocalDate, startKg: Double, goalDate: LocalDate, goalKg: Double): RebaselineResult =
    rebaseline(listOf(WeightEntry(today, startKg)), today, goalDate, goalKg)

/** The date a steady [kgPerWeek] loss from [startKg] reaches [goalKg], at least a week after [from]. */
fun steadyGoalDate(from: LocalDate, startKg: Double, goalKg: Double, kgPerWeek: Double = 0.5): LocalDate =
    from.plusDays(ceil((startKg - goalKg) / kgPerWeek * 7).toLong().coerceAtLeast(7))
