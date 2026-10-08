package wt.core.alerts

import wt.core.Safety
import wt.core.model.Run
import wt.core.model.WeightEntry
import wt.core.trend.fitTrend
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AlertKind { SLOW_LOSS, FAST_LOSS, REST_NEEDED, LOG_REMINDER }

enum class Severity { INFO, WARNING }

data class Alert(val kind: AlertKind, val severity: Severity, val title: String, val message: String)

data class AlertSettings(
    val slowKgPerWeek: Double = 0.3,
    val fastKgPerWeek: Double = 0.8,
    val maxConsecutiveRunDays: Int = 5,
    val reminderAfterDays: Int = 3,
    val slowLossKcalCut: Double = 150.0,
)

/**
 * Dashboard alerts as of [asOf].
 * [estimatedIntake] (from the energy report) lets the slow-loss advice respect the intake floor.
 */
fun evaluateAlerts(
    weights: List<WeightEntry>,
    runs: List<Run>,
    restDays: Set<LocalDate>,
    asOf: LocalDate,
    estimatedIntake: Double? = null,
    settings: AlertSettings = AlertSettings(),
): List<Alert> {
    val alerts = mutableListOf<Alert>()

    val now = fitTrend(weights, asOf)?.takeUnless { it.lowConfidence }
    val weekAgo = fitTrend(weights, asOf.minusDays(7))?.takeUnless { it.lowConfidence }

    if (now != null && weekAgo != null &&
        -now.kgPerWeek < settings.slowKgPerWeek && -weekAgo.kgPerWeek < settings.slowKgPerWeek
    ) {
        val room = estimatedIntake?.let { it - Safety.MIN_INTAKE_KCAL }
        val cut = if (room == null) settings.slowLossKcalCut else minOf(settings.slowLossKcalCut, room).coerceAtLeast(0.0)
        val advice = if (cut >= 1) {
            "Try eating about ${cut.toInt()} kcal/day less. Keep running as it is; don't add more running."
        } else {
            "Your estimated intake is already near ${Safety.MIN_INTAKE_KCAL.toInt()} kcal/day, so don't cut further. " +
                "Check portion accuracy and sleep, and give it another week."
        }
        alerts += Alert(
            AlertKind.SLOW_LOSS, Severity.WARNING, "Loss has slowed",
            "Trend loss has been below ${settings.slowKgPerWeek} kg/week for 2 weeks " +
                "(now %.2f kg/week). $advice".format(-now.kgPerWeek),
        )
    }

    if (now != null && -now.kgPerWeek > settings.fastKgPerWeek) {
        alerts += Alert(
            AlertKind.FAST_LOSS, Severity.WARNING, "Losing fast",
            "Trend loss is %.2f kg/week, above ${settings.fastKgPerWeek}. ".format(-now.kgPerWeek) +
                "That risks losing muscle. Consider eating a bit more, especially protein.",
        )
    }

    val streak = runStreak(runs, restDays, asOf)
    if (streak > settings.maxConsecutiveRunDays) {
        alerts += Alert(
            AlertKind.REST_NEEDED, Severity.INFO, "Time for a rest day",
            "You've run $streak days in a row. A rest day helps recovery and lowers injury risk.",
        )
    }

    val last = weights.filter { it.date <= asOf }.maxOfOrNull { it.date }
    val gap = last?.let { ChronoUnit.DAYS.between(it, asOf) }
    if (gap == null || gap >= settings.reminderAfterDays) {
        alerts += Alert(
            AlertKind.LOG_REMINDER, Severity.INFO, "Log your weight",
            if (gap == null) "No weight logged yet." else "No weight logged for $gap days. Daily weigh-ins keep the forecast accurate.",
        )
    }
    return alerts
}

/**
 * Consecutive run days ending today, or yesterday if there's no run today yet.
 * A marked rest day breaks the streak.
 */
fun runStreak(runs: List<Run>, restDays: Set<LocalDate>, asOf: LocalDate): Int {
    val runDays = runs.mapNotNull { it.date }.toSet()
    var d = if (asOf in runDays) asOf else asOf.minusDays(1)
    var count = 0
    while (d in runDays && d !in restDays) {
        count++
        d = d.minusDays(1)
    }
    return count
}
