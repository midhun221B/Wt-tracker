package wt.app.ui

import wt.core.model.formatMinSec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun kg(v: Double) = "%.1f kg".format(v)

fun signedKg(v: Double) = "%+.1f kg".format(v)

fun kcal(v: Double) = "%,.0f kcal".format(v)

fun signedKcal(v: Double) = "%+,.0f kcal".format(v)

/**
 * Daily kcal rounded to the nearest 50 ("2,350"): intake is estimated from the weight trend and a BMR guess,
 * so more digits would look more precise than they are.
 */
fun roughKcal(v: Double): String = "%,d".format(round50(v))

fun round50(v: Double): Int = (Math.round(v / 50.0) * 50).toInt()

private val shortDate = DateTimeFormatter.ofPattern("MM-dd")
private val longDayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val weekdayDate = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd")

fun short(d: LocalDate): String = d.format(shortDate)

fun withWeekday(d: LocalDate): String = d.format(weekdayDate)

/** "Thursday, 5 November" */
fun longDay(d: LocalDate): String = d.format(longDayFmt)

private val dayMonthYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "8 Oct 2026" */
fun dayMonthYear(d: LocalDate): String = d.format(dayMonthYearFmt)

/** "7 Jan" */
fun dayMonth(d: LocalDate): String = d.format(dayMonthFmt)

/** Monday-to-Sunday range starting [monday]: "12 – 18 Oct", or "28 Sep – 4 Oct" across months. */
fun weekRange(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    return if (monday.month == sunday.month) "${monday.dayOfMonth} – ${dayMonth(sunday)}" else "${dayMonth(monday)} – ${dayMonth(sunday)}"
}

fun pace(secPerKm: Double) = formatMinSec(secPerKm) + " /km"

/** "20:16" for under an hour, otherwise "1:05:30". */
fun duration(sec: Int): String =
    if (sec < 3600) formatMinSec(sec.toDouble()) else "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)

/** Accepts "20:16", "1:05:30" or plain minutes ("21"); returns seconds or null. */
fun parseDuration(text: String): Int? {
    val parts = text.trim().split(":").map { it.trim().toIntOrNull() ?: return null }
    return when (parts.size) {
        1 -> parts[0] * 60
        2 -> if (parts[1] in 0..59) parts[0] * 60 + parts[1] else null
        3 -> if (parts[1] in 0..59 && parts[2] in 0..59) parts[0] * 3600 + parts[1] * 60 + parts[2] else null
        else -> null
    }?.takeIf { it > 0 }
}

/** Parses a decimal typed with either "." or ","; blank → null. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Number for an input field: no trailing ".0" noise. */
fun fieldText(v: Double?): String = when {
    v == null -> ""
    v == Math.rint(v) -> v.toLong().toString()
    else -> v.toString()
}

/** Short bar label for a week: its Monday ("5 Oct"), or "Before" for weeks before the plan. */
fun weekLabel(w: wt.core.summary.WeekSummary): String = if (w.index < 1) "Before" else dayMonth(w.start)
