package wt.core.energy

import wt.core.Safety
import wt.core.model.Profile
import wt.core.model.Run
import wt.core.trend.TrendFit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Energy balance implied by the weight trend, compared with what the plan expects.
 * All kcal values are per day. A positive deficit means losing weight.
 */
data class EnergyReport(
    /** Deficit implied by the trend slope (7700 kcal per kg). */
    val actualDeficit: Double,
    /** Planned food deficit plus average net running burn over the fit window. */
    val expectedDeficit: Double,
    val runNetKcalPerDay: Double,
    /** Loss rate (kg/week) needed to reach the goal on time; null if the goal date has passed. */
    val requiredKgPerWeek: Double?,
    /** True when the required rate was capped at the safe maximum. */
    val requiredCapped: Boolean,
    /** BMR × activity factor, excluding running. */
    val maintenanceKcal: Double,
    /** Maintenance + running − actual deficit: what the weight trend says you eat. */
    val estimatedIntake: Double,
    /** Maintenance + running − expected deficit: what the plan assumes you eat. */
    val plannedIntake: Double,
    /** Change in daily intake to get back on plan; negative means eat less. */
    val intakeChange: Double,
    /** Suggested daily intake, never below the safety floor. */
    val targetIntake: Double,
    /** True when the suggestion was limited by the minimum intake. */
    val intakeFloored: Boolean,
)

/** Net kcal of a run: logged (gross) kcal minus resting burn, or ≈ 0.9 kcal/kg/km when not logged. */
fun runNetKcal(run: Run, bodyKg: Double, bmrKcal: Double): Double {
    val logged = run.kcal ?: return 0.9 * bodyKg * run.km
    val restingDuringRun = bmrKcal / 1440.0 * (run.durationSec / 60.0)
    return (logged - restingDuringRun).coerceAtLeast(0.0)
}

fun energyBalance(
    fit: TrendFit,
    runs: List<Run>,
    profile: Profile,
    goalKg: Double = profile.goalKg,
    goalDate: LocalDate = profile.goalDate,
): EnergyReport {
    val asOf = fit.origin
    val trendKg = fit.valueAt(asOf)
    val windowDays = ChronoUnit.DAYS.between(fit.windowStart, asOf) + 1
    val runKcal = runs
        .filter { it.date != null && it.date in fit.windowStart..asOf }
        .sumOf { runNetKcal(it, trendKg, profile.bmrKcal) }
    val runPerDay = runKcal / windowDays

    val actualDeficit = -fit.slopePerDay * Safety.KCAL_PER_KG
    val expectedDeficit = profile.plannedFoodDeficitKcal + runPerDay

    val daysLeft = ChronoUnit.DAYS.between(asOf, goalDate)
    val requiredRaw: Double? = when {
        trendKg <= goalKg -> 0.0
        daysLeft <= 0 -> null
        else -> (trendKg - goalKg) / daysLeft * 7
    }
    val required = requiredRaw?.let { Safety.capLossPerWeek(it) }
    val requiredDeficit = (required ?: 0.0) / 7 * Safety.KCAL_PER_KG

    val maintenance = profile.bmrKcal * profile.activityFactor
    val intake = maintenance + runPerDay - actualDeficit
    var change = if (required == null) 0.0 else -(requiredDeficit - actualDeficit)
    val target = Safety.floorIntake(intake + change)
    val floored = target > intake + change
    if (floored) change = target - intake

    return EnergyReport(
        actualDeficit = actualDeficit,
        expectedDeficit = expectedDeficit,
        runNetKcalPerDay = runPerDay,
        requiredKgPerWeek = required,
        requiredCapped = requiredRaw != null && required != requiredRaw,
        maintenanceKcal = maintenance,
        estimatedIntake = intake,
        plannedIntake = maintenance + runPerDay - expectedDeficit,
        intakeChange = change,
        targetIntake = target,
        intakeFloored = floored,
    )
}
