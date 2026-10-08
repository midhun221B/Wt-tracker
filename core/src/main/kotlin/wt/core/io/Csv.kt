package wt.core.io

import wt.core.model.BodyComp
import wt.core.model.Run
import wt.core.model.WeightEntry
import wt.core.model.formatMinSec
import java.time.LocalDate

/** Quotes a CSV field when it contains a comma, quote or newline. */
fun csvField(value: Any?): String {
    val s = value?.toString() ?: ""
    return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

private fun csv(header: List<String>, rows: List<List<Any?>>): String =
    (listOf(header) + rows).joinToString("\n", postfix = "\n") { row -> row.joinToString(",") { csvField(it) } }

fun weightsCsv(weights: List<WeightEntry>, restDays: Set<LocalDate> = emptySet()): String = csv(
    listOf("date", "weight_kg", "sleep_h", "hunger_1_5", "snacks", "note", "rest_day"),
    weights.sortedBy { it.date }.map {
        listOf(it.date, it.kg, it.sleepHours, it.hunger, it.snacks, it.note, if (it.date in restDays) "yes" else "")
    },
)

fun runsCsv(runs: List<Run>): String = csv(
    listOf("date", "distance_km", "time", "duration_s", "pace_min_per_km", "kcal", "source"),
    runs.sortedWith(compareBy(nullsLast()) { it.date }).map {
        listOf(it.date, it.km, formatMinSec(it.durationSec.toDouble()), it.durationSec, formatMinSec(it.paceSecPerKm), it.kcal, it.source)
    },
)

fun bodyCsv(body: List<BodyComp>): String = csv(
    listOf("date", "body_fat_pct", "visceral_fat", "muscle_kg", "skeletal_muscle_pct", "lean_kg", "bmr_kcal"),
    body.sortedBy { it.date }.map { listOf(it.date, it.fatPct, it.visceral, it.muscleKg, it.skeletalPct, it.leanKg, it.bmrKcal) },
)
