package wt.core.summary

import java.time.LocalDate

/**
 * Where the weekly weigh-in (or body measurement) stands on [date].
 * [doneOn] is the latest entry this Monday–Sunday week up to [date]; [due] means none yet and the chosen day has
 * come; [next] is this week's chosen day, or next week's once this week is done.
 */
data class WeighInStatus(val doneOn: LocalDate?, val due: Boolean, val next: LocalDate)

/** [logged] are the dates with an entry; [weighInDay] is an ISO weekday (1 = Monday … 7 = Sunday). */
fun weighInStatus(logged: Collection<LocalDate>, date: LocalDate, weighInDay: Int): WeighInStatus {
    val monday = weekStart(date)
    val dayThisWeek = monday.plusDays(weighInDay.coerceIn(1, 7) - 1L)
    val doneOn = logged.filter { it in monday..date }.maxOrNull()
    return WeighInStatus(
        doneOn = doneOn,
        due = doneOn == null && date >= dayThisWeek,
        next = if (doneOn != null) dayThisWeek.plusWeeks(1) else dayThisWeek,
    )
}
