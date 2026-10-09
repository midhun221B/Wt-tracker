package wt.core.summary

import wt.core.model.Run
import wt.core.model.WeightEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class WeekSummary(
    val index: Int, // program week: 1 is the week of the first weigh-in, 0 and below are before it
    val start: LocalDate,
    val end: LocalDate,
    val avgKg: Double?,
    /** Change in average weight from the previous week with data. */
    val changeKg: Double?,
    val runs: Int,
    val km: Double,
    /** Days so far in this week without a run (explicitly marked rest days included). */
    val restDays: Int,
)

/** Monday of the week containing [date]; weeks run Monday to Sunday. */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Program week of [date] when week 1 is the week containing [week1Day]; earlier weeks are 0, −1, … */
fun programWeekIndex(week1Day: LocalDate, date: LocalDate): Int =
    Math.floorDiv(ChronoUnit.DAYS.between(weekStart(week1Day), weekStart(date)), 7L).toInt() + 1

/** How many weeks before week 1 the table reaches back to show runs logged before the plan started. */
const val WEEKS_BEFORE_PLAN = 4

/**
 * Weekly table of Monday–Sunday weeks, oldest first, up to the week of [asOf].
 * Week 1 is the week containing [week1Day]. Weeks before it (index 0, −1, …) are included when they hold runs,
 * going back at most [WEEKS_BEFORE_PLAN] weeks, so runs logged just before the plan still count.
 */
fun weeklySummary(
    weights: List<WeightEntry>,
    runs: List<Run>,
    week1Day: LocalDate,
    asOf: LocalDate,
): List<WeekSummary> {
    val week1 = weekStart(week1Day)
    val earliestRun = runs.mapNotNull { it.date }.filter { it <= asOf }.minOrNull()
    val first = if (earliestRun != null && earliestRun < week1) {
        maxOf(weekStart(earliestRun), week1.minusWeeks(WEEKS_BEFORE_PLAN.toLong()))
    } else {
        week1
    }
    if (asOf < first) return emptyList()
    val weeks = ChronoUnit.DAYS.between(first, weekStart(asOf)) / 7 + 1
    var previousAvg: Double? = null
    return (0 until weeks).map { w ->
        val start = first.plusWeeks(w)
        val end = start.plusDays(6)
        val range = start..end
        val avg = weights.filter { it.date in range }.map { it.kg }.takeIf { it.isNotEmpty() }?.average()
        val weekRuns = runs.filter { it.date != null && it.date in range }
        val summary = WeekSummary(
            index = programWeekIndex(week1, start),
            start = start,
            end = end,
            avgKg = avg,
            changeKg = if (avg != null && previousAvg != null) avg - previousAvg!! else null,
            runs = weekRuns.size,
            km = weekRuns.sumOf { it.km },
            restDays = (0L..6L).map { start.plusDays(it) }
                .count { d -> d <= asOf && weekRuns.none { it.date == d } },
        )
        if (avg != null) previousAvg = avg
        summary
    }
}
