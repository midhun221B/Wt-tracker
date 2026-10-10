package wt.core.summary

import wt.core.model.Run
import java.time.LocalDate

/** What a day shows in the distance views. */
enum class DayKind {
    /** At least one run. */
    Run,
    /** Marked as a rest day, no run. */
    Rest,
    /** A past day with neither: missed. */
    Missed,
    /** Today, nothing logged yet (not missed). */
    Open,
    /** Before the first weigh-in or run: the app wasn't in use, so not missed. */
    BeforeStart,
    /** After today. */
    Future,
}

data class DayDistance(val date: LocalDate, val km: Double, val runs: Int, val kind: DayKind)

/** One Monday–Sunday row of days and the week's km. */
data class DistanceWeek(val monday: LocalDate, val days: List<DayDistance>) {
    val km: Double get() = days.sumOf { it.km }
}

/**
 * Every day from the Monday of [firstDay]'s week to the Sunday of [asOf]'s week, oldest first. Runs on the same day
 * add up. A rest day with a run counts as a run. Empty when there is no [firstDay] (nothing logged yet).
 */
fun dailyDistance(runs: List<Run>, restDays: Set<LocalDate>, firstDay: LocalDate?, asOf: LocalDate): List<DistanceWeek> {
    if (firstDay == null) return emptyList()
    val byDay = runs.filter { it.date != null }.groupBy { it.date!! }
    val weeks = mutableListOf<DistanceWeek>()
    var monday = weekStart(minOf(firstDay, asOf))
    val last = weekStart(asOf)
    while (monday <= last) {
        val days = (0L..6L).map { monday.plusDays(it) }.map { d ->
            val dayRuns = byDay[d].orEmpty()
            val kind = when {
                dayRuns.isNotEmpty() -> DayKind.Run
                d > asOf -> DayKind.Future
                d in restDays -> DayKind.Rest
                d < firstDay -> DayKind.BeforeStart
                d == asOf -> DayKind.Open
                else -> DayKind.Missed
            }
            DayDistance(d, dayRuns.sumOf { it.km }, dayRuns.size, kind)
        }
        weeks += DistanceWeek(monday, days)
        monday = monday.plusWeeks(1)
    }
    return weeks
}

/** The last [weeks] Monday–Sunday weeks of [all] for the distance strip, with its totals. */
data class DistanceSpan(
    val days: List<DayDistance>,
    val thisWeekKm: Double,
    val lastWeekKm: Double?,
    /** Longest single day's distance in the span. */
    val longestKm: Double,
    /** Km per run in the span; null without runs. */
    val averageRunKm: Double?,
    val runDays: Int,
    /** Days in the span from the first day in use through today. */
    val daysSoFar: Int,
)

fun distanceSpan(all: List<DistanceWeek>, weeks: Int): DistanceSpan {
    val shown = all.takeLast(weeks)
    val days = shown.flatMap { it.days }
    val runs = days.sumOf { it.runs }
    return DistanceSpan(
        days = days,
        thisWeekKm = all.lastOrNull()?.km ?: 0.0,
        lastWeekKm = all.getOrNull(all.size - 2)?.km,
        longestKm = days.maxOfOrNull { it.km } ?: 0.0,
        averageRunKm = if (runs == 0) null else days.sumOf { it.km } / runs,
        runDays = days.count { it.kind == DayKind.Run },
        daysSoFar = days.count { it.kind != DayKind.BeforeStart && it.kind != DayKind.Future },
    )
}
