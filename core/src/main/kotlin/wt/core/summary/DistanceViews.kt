package wt.core.summary

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A calendar month of days: Monday–Sunday rows, null outside the month, with the month's totals. */
data class DistanceMonth(
    val month: YearMonth,
    val rows: List<List<DayDistance?>>,
    val km: Double,
    val runs: Int,
    val restDays: Int,
)

/**
 * [month] as calendar rows from the daily distances in [all]. Days the list doesn't cover are "to come" after
 * it and "before the start" before it.
 */
fun distanceMonth(all: List<DistanceWeek>, month: YearMonth): DistanceMonth {
    val byDate = all.flatMap { it.days }.associateBy { it.date }
    val lastCovered = all.lastOrNull()?.days?.last()?.date
    val first = month.atDay(1)
    val rows = generateSequence(weekStart(first)) { it.plusWeeks(1) }
        .takeWhile { it <= month.atEndOfMonth() }
        .map { monday ->
            (0L..6L).map { monday.plusDays(it) }.map { d ->
                when {
                    YearMonth.from(d) != month -> null
                    else -> byDate[d] ?: DayDistance(
                        d, 0.0, 0,
                        if (lastCovered != null && d > lastCovered) DayKind.Future else DayKind.BeforeStart,
                    )
                }
            }
        }.toList()
    val days = rows.flatten().filterNotNull()
    return DistanceMonth(month, rows, days.sumOf { it.km }, days.sumOf { it.runs }, days.count { it.kind == DayKind.Rest })
}

/** The months to page through, oldest first: from the first week's month to [asOf]'s month. */
fun distanceMonths(all: List<DistanceWeek>, asOf: LocalDate): List<YearMonth> {
    val first = all.flatMap { it.days }.firstOrNull { it.kind != DayKind.BeforeStart }?.date ?: asOf
    return generateSequence(YearMonth.from(first)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(asOf) }.toList()
}

/** One week in the all-time views: its km, null for weeks still to come. */
data class WeekKm(val monday: LocalDate, val km: Double?, val current: Boolean)

/** Every week from the first entry to the goal week, with the totals the all-time views show. */
data class AllTimeDistance(
    val weeks: List<WeekKm>,
    val totalKm: Double,
    val runs: Int,
    /** First day with a run or a rest day. */
    val since: LocalDate?,
    val bestWeekKm: Double,
    /** Mean of the finished weeks (this week left out); null before one has finished. */
    val averageWeekKm: Double?,
    val weeksToGo: Int,
    /** How far today is through the span from the first week to the end of the goal week, 0–1. */
    val progress: Double,
    /** Where the goal date falls in that span, 0–1. */
    val goalAt: Double,
)

fun allTimeDistance(all: List<DistanceWeek>, asOf: LocalDate, goalDate: LocalDate): AllTimeDistance {
    val thisWeek = weekStart(asOf)
    val goalWeek = maxOf(weekStart(goalDate), thisWeek)
    val past = all.map { WeekKm(it.monday, it.km, it.monday == thisWeek) }
    val start = all.firstOrNull()?.monday ?: thisWeek
    val future = generateSequence((all.lastOrNull()?.monday ?: thisWeek.minusWeeks(1)).plusWeeks(1)) { it.plusWeeks(1) }
        .takeWhile { it <= goalWeek }
        .map { WeekKm(it, null, false) }
        .toList()
    val finished = all.filter { it.monday < thisWeek }
    val days = all.flatMap { it.days }
    val span = ChronoUnit.DAYS.between(start, goalWeek.plusWeeks(1)).toDouble()
    return AllTimeDistance(
        weeks = past + future,
        totalKm = days.sumOf { it.km },
        runs = days.sumOf { it.runs },
        since = days.firstOrNull { it.kind == DayKind.Run || it.kind == DayKind.Rest }?.date,
        bestWeekKm = all.maxOfOrNull { it.km } ?: 0.0,
        averageWeekKm = if (finished.isEmpty()) null else finished.sumOf { it.km } / finished.size,
        weeksToGo = future.size,
        progress = (ChronoUnit.DAYS.between(start, asOf) + 1) / span,
        goalAt = (ChronoUnit.DAYS.between(start, goalDate) + 0.5) / span,
    )
}
