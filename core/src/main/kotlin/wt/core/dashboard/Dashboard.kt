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
import wt.core.summary.AllTimeDistance
import wt.core.summary.DistanceWeek
import wt.core.summary.allTimeDistance
import wt.core.summary.WeekSummary
import wt.core.summary.dailyDistance
import wt.core.summary.goalProgress
import wt.core.summary.programWeekIndex
import wt.core.summary.weekStart
import wt.core.summary.weeklySummary
import wt.core.trend.DatedValue
import wt.core.trend.Forecast
import wt.core.trend.TrendFit
import wt.core.trend.fitTrend
import wt.core.trend.forecast
import wt.core.trend.movingAverage
import java.time.LocalDate

/** Today's weight on the goal's terms ([fromTrend] = the trend, else the latest weigh-in) and [gapKg] to the plan. */
data class PlanPosition(val kg: Double, val gapKg: Double, val fromTrend: Boolean)

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
    /** Monday of program week 1: the week of the first weigh-in (or of the plan start before any). */
    val week1: LocalDate,
    /** Every day's distance (run km, rest, missed), Monday–Sunday weeks from the first entry, oldest first. */
    val distance: List<DistanceWeek> = emptyList(),
) {
    /** Current program week and the week of the goal date, e.g. 1 to 14 for "Week 1 of 14". */
    fun programWeek(): Pair<Int, Int> =
        programWeekIndex(week1, asOf).coerceAtLeast(1) to programWeekIndex(week1, plan.goal.date).coerceAtLeast(1)


    /**
     * Where today stands against the plan, counted like every other screen: today's trend (or the latest weigh-in
     * before there is one) and its gap to the planned weight (+ = behind). Null before the first weigh-in.
     */
    fun todayVsPlan(): PlanPosition? {
        if (weights.isEmpty()) return null
        val g = goalProgress(weights, asOf, plan.goal.kg, plan.start.kg)
        return PlanPosition(g.currentKg, g.currentKg - plan.at(asOf), g.fromTrend)
    }

    /** Every week from the first entry to the goal week, for the all-time distance views. */
    fun allTime(): AllTimeDistance = allTimeDistance(distance, asOf, plan.goal.date)

    /** Preview of re-baselining from today to the current goal; null without any weigh-in. */
    fun rebaselinePreview(): RebaselineResult? =
        if (latestWeight == null) null else rebaseline(weights, asOf, plan.goal.date, plan.goal.kg)
}

/**
 * The goal (date and kg) is the last checkpoint of the active plan.
 * Weeks run Monday to Sunday; week 1 is the week of the first weigh-in, so re-baselining doesn't renumber weeks.
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
    val week1 = weekStart(sorted.firstOrNull()?.date ?: plan.start.date)
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
        weekly = weeklySummary(sorted, runs, week1, asOf).reversed(),
        body = body.sortedBy { it.date },
        latestWeight = sorted.lastOrNull(),
        runStreak = runStreak(runs, restDays, asOf),
        week1 = week1,
        // From the first weigh-in or run, like the week dots on Today.
        distance = dailyDistance(
            runs, restDays,
            listOfNotNull(sorted.firstOrNull()?.date, runs.mapNotNull { it.date }.minOrNull()).minOrNull(),
            asOf,
        ),
    )
}
