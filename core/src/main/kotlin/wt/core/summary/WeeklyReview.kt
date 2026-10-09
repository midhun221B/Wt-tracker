package wt.core.summary

import wt.core.Safety
import wt.core.dashboard.Dashboard
import wt.core.model.Run
import wt.core.trend.fitTrend
import java.time.LocalDate
import kotlin.math.ceil

/** One suggestion for the coming week. Every value already respects the [Safety] limits. */
sealed interface Suggestion {
    /** Fewer than 3 weigh-ins: no trend to judge yet. */
    data object TooEarly : Suggestion

    /** On or ahead of the plan line. */
    data object OnPlan : Suggestion

    /** Behind the plan line, but the current pace still reaches the goal on time. */
    data object OnPace : Suggestion

    /** Behind, and the missing deficit is small enough to run off: about [kmPerWeek] more a week. */
    data class RunMore(val kmPerWeek: Double) : Suggestion

    /** Behind: eat about [targetKcal] a day, [lessKcal] less than now. [floored] means the 1800 kcal floor applied. */
    data class EatLess(val targetKcal: Double, val lessKcal: Double, val floored: Boolean) : Suggestion

    /** Losing faster than 1 kg a week: eat about [targetKcal] a day. */
    data class SlowDown(val targetKcal: Double) : Suggestion
}

/**
 * The weekly check-in for the weigh-in on a day. The stretch is from the previous weigh-in ([since]) up to the day
 * before this one; [previousRuns] and [previousKm] cover the stretch before that. Trend values use the same fit as
 * the Trend tab, evaluated on each weigh-in day with the weights known then. [gapKg] is trend minus plan
 * (+ = behind) and [gapChangeKg] its change since the previous weigh-in (− = catching up).
 */
data class WeeklyReview(
    val week: Int,
    val since: LocalDate,
    val trendChangeKg: Double?,
    val runs: Int,
    val km: Double,
    val previousRuns: Int?,
    val previousKm: Double?,
    val gapKg: Double?,
    val gapChangeKg: Double?,
    val suggestion: Suggestion,
)

/** Running can close the gap when it takes at most this many extra km a week; otherwise food is suggested. */
const val MAX_EXTRA_KM_PER_WEEK = 6.0

/**
 * Null when [date] has no weigh-in or it is the first one. The suggestion comes from the dashboard's forecast and
 * energy report (as of the dashboard's day), so it shares their safety limits.
 */
fun weeklyReview(dashboard: Dashboard, runs: List<Run>, date: LocalDate): WeeklyReview? {
    val weights = dashboard.weights.sortedBy { it.date }
    if (weights.none { it.date == date }) return null
    val earlier = weights.filter { it.date < date }.map { it.date }.distinct()
    val since = earlier.lastOrNull() ?: return null
    val before = earlier.dropLast(1).lastOrNull()

    fun trendOn(day: LocalDate) = fitTrend(weights.filter { it.date <= day }, day)?.valueAt(day)
    fun gapOn(day: LocalDate) = trendOn(day)?.let { it - dashboard.plan.at(day) }
    fun runsIn(from: LocalDate, until: LocalDate) = runs.filter { r -> r.date != null && r.date >= from && r.date < until }

    val now = runsIn(since, date)
    val prev = before?.let { runsIn(it, since) }
    val trendNow = trendOn(date)
    val trendThen = trendOn(since)
    val gapNow = gapOn(date)
    val gapThen = gapOn(since)
    val bodyKg = trendNow ?: weights.last { it.date <= date }.kg

    return WeeklyReview(
        week = programWeekIndex(dashboard.week1, date),
        since = since,
        trendChangeKg = if (trendNow != null && trendThen != null) trendNow - trendThen else null,
        runs = now.size,
        km = now.sumOf { it.km },
        previousRuns = prev?.size,
        previousKm = prev?.sumOf { it.km },
        gapKg = gapNow,
        gapChangeKg = if (gapNow != null && gapThen != null) gapNow - gapThen else null,
        suggestion = suggest(dashboard, gapNow, bodyKg),
    )
}

private fun suggest(dashboard: Dashboard, gapKg: Double?, bodyKg: Double): Suggestion {
    val forecast = dashboard.forecast
    val energy = dashboard.energy
    if (forecast == null || energy == null || gapKg == null) return Suggestion.TooEarly
    if (forecast.kgPerWeek < -Safety.MAX_LOSS_KG_PER_WEEK) return Suggestion.SlowDown(energy.targetIntake)
    if (gapKg <= 0.05) return Suggestion.OnPlan
    val missing = -energy.intakeChange // kcal/day still needed to reach the goal on time
    if (missing <= 0.0) return Suggestion.OnPace
    // Net running burn ≈ 0.9 kcal per kg per km (CLAUDE.md), so the same deficit as km a week, in 0.5 km steps.
    val km = ceil(missing * 7 / (0.9 * bodyKg) * 2) / 2
    return if (km <= MAX_EXTRA_KM_PER_WEEK) {
        Suggestion.RunMore(km)
    } else {
        Suggestion.EatLess(energy.targetIntake, missing, energy.intakeFloored)
    }
}
