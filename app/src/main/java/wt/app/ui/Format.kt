package wt.app.ui

import wt.core.model.formatMinSec
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun kg(v: Double) = "%.1f kg".format(v)

fun signedKg(v: Double) = "%+.1f kg".format(v)

fun kcal(v: Double) = "%,.0f kcal".format(v)

fun signedKcal(v: Double) = "%+,.0f kcal".format(v)

private val shortDate = DateTimeFormatter.ofPattern("MM-dd")
private val weekdayDate = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd")

fun short(d: LocalDate): String = d.format(shortDate)

fun withWeekday(d: LocalDate): String = d.format(weekdayDate)

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
