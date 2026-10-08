package wt.core.io

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** One line of text found by on-device text recognition, with its bounding box in image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * Joins recognised lines into visual rows, top to bottom. A line belongs to a row when its vertical centre lies
 * inside the box of the row's first line, so a label on the left and its value on the right end up as one text
 * ("体脂肪率 29.2 %").
 */
fun ocrRows(lines: List<OcrLine>): List<String> {
    val rows = mutableListOf<MutableList<OcrLine>>()
    for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
        val row = rows.lastOrNull()?.takeIf { line.centerY in it.first().top..it.first().bottom }
        if (row != null) row += line else rows += mutableListOf(line)
    }
    return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
}

/** What a screenshot turned out to be. Every value can be missing; the user confirms them before saving. */
sealed interface ScreenshotReading

/** A run from a Strava share image or activity screen. Strava's share image has no date. */
data class RunReading(
    val km: Double?,
    val durationSec: Int?,
    val kcal: Double?,
    val paceSecPerKm: Int?,
    /** Things the user should double-check, in plain words. */
    val notes: List<String> = emptyList(),
) : ScreenshotReading

/** Body-scale values, e.g. from a Japanese scale app (体脂肪率, 内臓脂肪, 筋肉量 …). */
data class BodyReading(
    val date: LocalDate?,
    val weightKg: Double?,
    /** True when the weight wasn't on screen and was worked out from lean mass and body fat (or BMI). */
    val weightEstimated: Boolean,
    val fatPct: Double?,
    val visceral: Double?,
    val muscleKg: Double?,
    val skeletalPct: Double?,
    val leanKg: Double?,
    val bmrKcal: Double?,
) : ScreenshotReading

/**
 * Reads the text rows of a screenshot (see [ocrRows]) and returns a run or body measurement, or null when
 * neither is recognised. [heightCm] lets the body weight be worked out from BMI as a last resort.
 */
fun readScreenshot(rows: List<String>, heightCm: Double? = null): ScreenshotReading? =
    readBody(rows, heightCm) ?: readRun(rows)

// ---- Runs ----

private val distanceRe = Regex("""(\d{1,3}[.,]\d{1,2})\s*km(?!\s*/)""", RegexOption.IGNORE_CASE)
private val paceRe = Regex("""(\d{1,2})\s*[:'’]\s*(\d{2})\s*/\s*km""", RegexOption.IGNORE_CASE)
private val hmsRe = Regex("""(?:(\d{1,2})\s*h\s*)?(\d{1,2})\s*m\s*(?:(\d{1,2})\s*s)?(?![a-z/])""", RegexOption.IGNORE_CASE)
private val hmsJaRe = Regex("""(?:(\d{1,2})\s*時間\s*)?(\d{1,2})\s*分\s*(?:(\d{1,2})\s*秒)?""")
private val clockRe = Regex("""^(?:(\d{1,2}):)?(\d{1,2}):(\d{2})$""")
private val kcalNumberRe = Regex("""\d{1,2}[,.]?\d{3}|\d{1,3}""")
private val kcalRe = Regex("""(${kcalNumberRe.pattern})\s*(?:k?cal|カロリー)""", RegexOption.IGNORE_CASE)

private val timeLabels = listOf("moving time", "elapsed time", "time", "移動時間", "経過時間", "タイム", "時間")
private val calLabels = listOf("calories", "cal", "カロリー")

/** The row right after a row that is just [labels] (Strava puts each value under its label). */
private fun valueAfter(rows: List<String>, labels: List<String>): String? {
    val i = rows.indexOfFirst { row -> labels.any { row.trim().equals(it, ignoreCase = true) } }
    return if (i >= 0) rows.getOrNull(i + 1) else null
}

private fun durationOf(text: String): Int? {
    val t = text.trim()
    clockRe.find(t)?.let { m ->
        val (h, mi, s) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + mi.toInt() * 60 + s.toInt()
    }
    val m = hmsRe.find(t) ?: hmsJaRe.find(t) ?: return null
    val (h, mi, s) = m.destructured
    // A bare "3 m" is an elevation, not a time: need hours or seconds as well.
    if (h.isEmpty() && s.isEmpty()) return null
    val seconds = s.toIntOrNull() ?: 0
    if (mi.toInt() > 59 && h.isNotEmpty() || seconds > 59) return null
    return (h.toIntOrNull() ?: 0) * 3600 + mi.toInt() * 60 + seconds
}

private fun readRun(rows: List<String>): RunReading? {
    val all = rows.joinToString("\n")
    val km = distanceRe.find(all)?.groupValues?.get(1)?.replace(',', '.')?.toDouble()?.takeIf { it in 0.1..100.0 }
    val pace = paceRe.find(all)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }?.takeIf { it in 120..1200 }
    val time = (valueAfter(rows, timeLabels)?.let(::durationOf) ?: rows.firstNotNullOfOrNull { row ->
        // Without a label, only accept the "24m 19s" style, which can't be confused with a pace or clock time.
        if (paceRe.containsMatchIn(row)) null else (hmsRe.find(row) ?: hmsJaRe.find(row))?.let { durationOf(it.value) }
    })?.takeIf { it in 60..36_000 }
    // Under a "Cal" label the number may have no unit; elsewhere it needs one.
    val kcalText = valueAfter(rows, calLabels)?.let { kcalNumberRe.find(it)?.value } ?: kcalRe.find(all)?.groupValues?.get(1)
    val kcal = kcalText?.replace(Regex("[,.]"), "")?.toDouble()?.takeIf { it in 1.0..5000.0 }

    if (km == null && time == null) return null
    if (pace == null && !all.contains("strava", ignoreCase = true) && (km == null || time == null)) return null

    val notes = mutableListOf<String>()
    var outKm = km
    var outTime = time
    when {
        km != null && time == null && pace != null -> {
            outTime = (km * pace).roundToInt()
            notes += "Time worked out from distance and pace."
        }
        km == null && time != null && pace != null -> {
            outKm = Math.round(time.toDouble() / pace * 100) / 100.0
            notes += "Distance worked out from time and pace."
        }
        km != null && time != null && pace != null -> {
            // Pace is rounded to the second, so allow a little slack before calling it a mismatch.
            if (abs(time - km * pace) > maxOf(30.0, 0.03 * time)) notes += "Distance, time and pace don't agree. Please check them."
        }
    }
    if (outKm == null) notes += "Distance not found."
    if (outTime == null) notes += "Time not found."
    return RunReading(outKm, outTime, kcal, pace, notes)
}

// ---- Body scale ----

private enum class BodyField(vararg val labels: String) {
    // Longer labels first where one could be mistaken for another (除脂肪体重 vs 体重).
    LEAN("除脂肪体重", "除脂肪量", "lean mass", "fat-free mass"),
    FAT("体脂肪率", "body fat"),
    VISCERAL("内臓脂肪レベル", "内臓脂肪", "visceral fat", "visceral"),
    SKELETAL("骨格筋率", "skeletal muscle"),
    MUSCLE("筋肉量", "muscle mass"),
    BMR("基礎代謝量", "基礎代謝", "bmr", "basal metabolism"),
    WEIGHT("体重", "weight"),
    BMI("bmi"),
}

private val numberRe = Regex("""\d+(?:[.,]\d+)?""")
private val dateRe = Regex("""(20\d{2})\s*[/.\-年]\s*(\d{1,2})\s*[/.\-月]\s*(\d{1,2})""")

/** The number on the label's row, or on the next row when that row starts with a number. */
private fun valueFor(rows: List<String>, field: BodyField): Double? {
    for ((i, raw) in rows.withIndex()) {
        val row = raw.trim()
        val label = field.labels.firstOrNull { row.startsWith(it, ignoreCase = true) } ?: continue
        val rest = row.substring(label.length)
        val number = numberRe.find(rest)?.value
            ?: rows.getOrNull(i + 1)?.trim()?.takeIf { it.firstOrNull()?.isDigit() == true }?.let { numberRe.find(it)?.value }
        if (number != null) return number.replace(',', '.').toDoubleOrNull()
    }
    return null
}

private fun Double.round1() = Math.round(this * 10) / 10.0

private fun readBody(rows: List<String>, heightCm: Double?): BodyReading? {
    val v = BodyField.entries.associateWith { valueFor(rows, it) }
    val fat = v[BodyField.FAT]?.takeIf { it in 2.0..70.0 }
    val visceral = v[BodyField.VISCERAL]?.takeIf { it in 0.0..60.0 }
    val muscle = v[BodyField.MUSCLE]?.takeIf { it in 10.0..150.0 }
    val skeletal = v[BodyField.SKELETAL]?.takeIf { it in 5.0..70.0 }
    val lean = v[BodyField.LEAN]?.takeIf { it in 20.0..150.0 }
    val bmr = v[BodyField.BMR]?.takeIf { it in 800.0..4000.0 }
    val bmi = v[BodyField.BMI]?.takeIf { it in 10.0..80.0 }
    if (listOfNotNull(fat, visceral, muscle, skeletal, lean, bmr).size < 2) return null

    val shown = v[BodyField.WEIGHT]?.takeIf { it in 30.0..250.0 }
    // Lean mass is the weight without fat, so weight = lean / (1 − fat %). Both are rounded to 0.1, which keeps the
    // estimate within about ±0.1 kg. BMI × height² is the fallback but BMI only has one decimal (±0.15 kg).
    val estimated = when {
        lean != null && fat != null -> lean / (1 - fat / 100)
        bmi != null && heightCm != null -> bmi * (heightCm / 100) * (heightCm / 100)
        else -> null
    }?.takeIf { it in 30.0..250.0 }

    val date = rows.firstNotNullOfOrNull { dateRe.find(it) }?.let { m ->
        val (y, mo, d) = m.destructured
        runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }
    return BodyReading(
        date = date,
        weightKg = (shown ?: estimated)?.round1(),
        weightEstimated = shown == null && estimated != null,
        fatPct = fat, visceral = visceral, muscleKg = muscle, skeletalPct = skeletal, leanKg = lean, bmrKcal = bmr,
    )
}
