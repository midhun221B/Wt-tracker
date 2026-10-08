package wt.core.dashboard

import wt.core.alerts.Alert
import wt.core.alerts.AlertSettings
import wt.core.alerts.evaluateAlerts
import wt.core.alerts.runStreak
import wt.core.energy.EnergyReport
import wt.core.energy.energyBalance
import wt.core.model.BodyComp
import wt.core.model.Checkpoint
import wt.core.model.Profile
import wt.core.model.Run
import wt.core.model.WeightEntry
import wt.core.plan.PlannedLine
import wt.core.plan.RebaselineResult
import wt.core.plan.rebaseline
import wt.core.summary.WeekSummary
import wt.core.summary.weeklySummary
import wt.core.trend.DatedValue
import wt.core.trend.Forecast
import wt.core.trend.TrendFit
import wt.core.trend.fitTrend
import wt.core.trend.forecast
import wt.core.trend.movingAverage
import java.time.LocalDate

/** Everything the dashboard shows, computed from stored data in one place. */
data class Dashboard(
    val asOf: LocalDate,
    val plan: PlannedLine,
    val weights: List<WeightEntry>,
    val movingAverage: List<DatedValue>,
    val fit: TrendFit?,
    val forecast: Forecast?,
    val energy: EnergyReport?,
    val alerts: List<Alert>,
    val weekly: List<WeekSummary>,
    val body: List<BodyComp>,
    val latestWeight: WeightEntry?,
    val runStreak: Int,
) {
    /** Preview of re-baselining from today to the current goal; null without any weigh-in. */
    fun rebaselinePreview(): RebaselineResult? =
        if (latestWeight == null) null else rebaseline(weights, asOf, plan.goal.date, plan.goal.kg)
}

/**
 * The goal (date and kg) is the last checkpoint of the active plan.
 * Weekly rows are anchored on the first weigh-in, so re-baselining doesn't renumber weeks.
 */
fun buildDashboard(
    weights: List<WeightEntry>,
    runs: List<Run>,
    restDays: Set<LocalDate>,
    body: List<BodyComp>,
    checkpoints: List<Checkpoint>,
    profile: Profile,
    asOf: LocalDate,
    alertSettings: AlertSettings = AlertSettings(),
): Dashboard {
    val plan = PlannedLine(checkpoints)
    val sorted = weights.filter { it.date <= asOf }.sortedBy { it.date }
    val fit = fitTrend(sorted, asOf)
    val energy = fit?.let { energyBalance(it, runs, profile, plan.goal.kg, plan.goal.date) }
    return Dashboard(
        asOf = asOf,
        plan = plan,
        weights = sorted,
        movingAverage = movingAverage(sorted),
        fit = fit,
        forecast = fit?.let { forecast(it, plan) },
        energy = energy,
        alerts = evaluateAlerts(sorted, runs, restDays, asOf, energy?.estimatedIntake, alertSettings),
        weekly = weeklySummary(sorted, runs, sorted.firstOrNull()?.date ?: plan.start.date, asOf).reversed(),
        body = body.sortedBy { it.date },
        latestWeight = sorted.lastOrNull(),
        runStreak = runStreak(runs, restDays, asOf),
    )
}
