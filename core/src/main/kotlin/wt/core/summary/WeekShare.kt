package wt.core.summary

import wt.core.dashboard.Dashboard
import wt.core.model.Checkpoint
import wt.core.model.WeightEntry
import wt.core.trend.trendWait
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Everything the partner share card shows for the weigh-in on [date].
 *
 * - The trail is the active plan ([trail]) with a post on the Monday of each program week ([posts]); my weigh-ins
 *   since the plan start ([weighIns], each week's last one, so extra weigh-ins don't zigzag) run beside it. [kgRange] covers both, so a weight above the start still fits.
 * - [planMarkerKg] is the planned weight on [date], shown only once the trend wait is over (three weigh-ins over two
 *   weeks), the same moment the Trend tab starts showing over/under plan.
 * - The strip is the Monday–Sunday week before [date]'s week ([stripIsLastWeek]), or [date]'s own week when there
 *   is no earlier week in use (the first week).
 */
data class WeekShare(
    val date: LocalDate,
    val week: Int,
    val totalWeeks: Int,
    val weeksToGo: Int,
    /** "Week 5 of 14", or "Goal week" once past the goal's week (never "Week 15 of 14"). */
    val headline: String,
    val kg: Double,
    val changeKg: Double?,
    val previousDate: LocalDate?,
    val fraction: Double,
    val trail: List<Checkpoint>,
    val posts: List<Checkpoint>,
    val currentPost: Checkpoint,
    val weighIns: List<WeightEntry>,
    /** Monday–Sunday weeks in a row, up to [date]'s week, with a weigh-in (a scale measurement counts too). */
    val streakWeeks: Int,
    val planMarkerKg: Double?,
    val kgRange: ClosedFloatingPointRange<Double>,
    val strip: List<DayDistance>,
    val stripIsLastWeek: Boolean,
    val runs: Int,
    val km: Double,
    val caption: String,
)

/** The share card for the weigh-in on [date]; null when that day has no weight. */
fun weekShare(dashboard: Dashboard, date: LocalDate): WeekShare? {
    val plan = dashboard.plan
    val p = weighInProgress(dashboard.weights, date, plan.goal.kg, plan.start.kg, plan.at(date)) ?: return null
    val week = programWeekIndex(dashboard.week1, date).coerceAtLeast(1)
    val totalWeeks = dashboard.programWeek().second
    val firstMonday = weekStart(dashboard.week1)
    val posts = (0 until totalWeeks).map { firstMonday.plusWeeks(it.toLong()) }.map { Checkpoint(it, plan.at(it)) }
    val weighIns = dashboard.weights.filter { it.date in plan.start.date..date }
        .groupBy { weekStart(it.date) }.values.map { week -> week.maxBy { it.date } }.sortedBy { it.date }
    val weighedWeeks = (dashboard.weights.map { it.date } + dashboard.body.map { it.date }).filter { it <= date }.map { weekStart(it) }.toSet()
    val streak = generateSequence(weekStart(date)) { it.minusWeeks(1) }.takeWhile { it in weighedWeeks }.count()
    // The shared weigh-in is always in range, even when it predates a re-baselined plan's start.
    val kgs = weighIns.map { it.kg } + p.kg
    val wait = trendWait(dashboard.weights, date)
    val readyOn = wait.readyOn
    val trendReady = wait.weighInsNeeded == 0 && readyOn != null && readyOn <= date

    val lastWeek = dashboard.distance.firstOrNull { it.monday == weekStart(date).minusWeeks(1) }
        ?.takeIf { w -> w.days.any { it.kind != DayKind.BeforeStart } }
    val stripWeek = lastWeek ?: dashboard.distance.first { it.monday == weekStart(date) }
    val runs = stripWeek.days.sumOf { it.runs }
    val headline = if (week > totalWeeks) "Goal week" else "Week $week of $totalWeeks"

    return WeekShare(
        date = date,
        week = week,
        totalWeeks = totalWeeks,
        weeksToGo = (totalWeeks - week).coerceAtLeast(0),
        headline = headline,
        kg = p.kg,
        changeKg = p.changeKg,
        previousDate = p.previousDate,
        fraction = p.goal.fraction,
        trail = plan.checkpoints,
        posts = posts,
        currentPost = Checkpoint(weekStart(date), plan.at(weekStart(date))),
        weighIns = weighIns,
        streakWeeks = streak,
        planMarkerKg = if (trendReady) plan.at(date) else null,
        kgRange = minOf(plan.goal.kg, kgs.min())..maxOf(plan.start.kg, kgs.max()),
        strip = stripWeek.days,
        stripIsLastWeek = lastWeek != null,
        runs = runs,
        km = stripWeek.km,
        caption = shareCaption(if (week > totalWeeks) "Goal week" else "Week $week", p.kg, p.changeKg, lastWeek != null, runs, stripWeek.km),
    )
}

/** "Week 5 weigh-in: 87.5 kg (−0.4). Last week 5 runs, 15.0 km." Always with a decimal point, whatever the locale. */
private fun shareCaption(week: String, kg: Double, changeKg: Double?, lastWeek: Boolean, runs: Int, km: Double): String {
    fun one(v: Double) = String.format(Locale.ROOT, "%.1f", v)
    val change = changeKg?.let {
        val tenths = (it * 10).roundToLong()
        val sign = when {
            tenths < 0 -> "−"
            tenths > 0 -> "+"
            else -> ""
        }
        " ($sign${one(abs(tenths) / 10.0)})"
    } ?: ""
    val ran = when (runs) {
        0 -> "no runs"
        1 -> "1 run, ${one(km)} km"
        else -> "$runs runs, ${one(km)} km"
    }
    return "$week weigh-in: ${one(kg)} kg$change. ${if (lastWeek) "Last week" else "This week"} $ran."
}
