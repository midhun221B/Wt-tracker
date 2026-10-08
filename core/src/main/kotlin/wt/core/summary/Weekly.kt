package wt.core.summary

import wt.core.model.Run
import wt.core.model.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class WeekSummary(
    val index: Int, // 1-based week of the program
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

/** Weekly table with weeks anchored on the program start (e.g. Thu–Wed). */
fun weeklySummary(
    weights: List<WeightEntry>,
    runs: List<Run>,
    anchor: LocalDate,
    asOf: LocalDate,
): List<WeekSummary> {
    if (asOf < anchor) return emptyList()
    val weeks = ChronoUnit.DAYS.between(anchor, asOf) / 7 + 1
    var previousAvg: Double? = null
    return (0 until weeks).map { w ->
        val start = anchor.plusWeeks(w)
        val end = start.plusDays(6)
        val range = start..end
        val avg = weights.filter { it.date in range }.map { it.kg }.takeIf { it.isNotEmpty() }?.average()
        val weekRuns = runs.filter { it.date != null && it.date in range }
        val summary = WeekSummary(
            index = w.toInt() + 1,
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
