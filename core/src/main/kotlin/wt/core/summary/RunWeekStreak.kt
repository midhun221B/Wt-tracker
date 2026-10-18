package wt.core.summary

import java.time.LocalDate

/** A Monday–Sunday week counts toward the run streak once it has this many runs. */
const val STREAK_RUNS_PER_WEEK = 3

/**
 * The run streak on a day: [weeks] in a row with at least [STREAK_RUNS_PER_WEEK] runs. The day's own week adds one
 * once it has them ([thisWeekCounts]); until then it doesn't break the streak. [endedWeeks] is the last streak's
 * length when there is none now (null if there never was one).
 */
data class RunWeekStreak(val weeks: Int, val thisWeekCounts: Boolean, val runsThisWeek: Int, val endedWeeks: Int?) {
    /** Runs still needed for this week to count; 0 once it does. */
    val runsToGo: Int get() = (STREAK_RUNS_PER_WEEK - runsThisWeek).coerceAtLeast(0)
}

/** The run streak on [asOf] from the dates of every run (one entry per run; runs after [asOf] are ignored). */
fun runWeekStreak(runDates: List<LocalDate>, asOf: LocalDate): RunWeekStreak {
    val perWeek = runDates.filter { it <= asOf }.groupingBy { weekStart(it) }.eachCount()
    fun full(monday: LocalDate) = (perWeek[monday] ?: 0) >= STREAK_RUNS_PER_WEEK
    fun streakFrom(monday: LocalDate) = generateSequence(monday) { it.minusWeeks(1) }.takeWhile(::full).count()

    val thisWeek = weekStart(asOf)
    val counts = full(thisWeek)
    val weeks = streakFrom(thisWeek.minusWeeks(1)) + if (counts) 1 else 0
    val ended = if (weeks > 0) null else perWeek.keys.filter { it < thisWeek && full(it) }.maxOrNull()?.let(::streakFrom)
    return RunWeekStreak(weeks, counts, perWeek[thisWeek] ?: 0, ended)
}
