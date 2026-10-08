package wt.core.io

import wt.core.model.Run
import wt.core.model.TOKYO
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Minimal RFC 4180 CSV reader: quoted fields, doubled quotes, commas and newlines inside quotes. */
fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    val s = text.removePrefix("﻿")
    while (i < s.length) {
        val c = s[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
        } else {
            when (c) {
                '"' -> inQuotes = true
                ',' -> { row += field.toString(); field.clear() }
                '\r' -> {}
                '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row += field.toString()
        rows += row
    }
    return rows.filter { r -> r.any { it.isNotBlank() } }
}

data class StravaParseResult(
    val runs: List<Run>,
    val totalActivities: Int,
    /** Activity types that were skipped, with counts (e.g. Ride → 12). */
    val skippedTypes: Map<String, Int>,
    /** Rows that could not be read, as "row N: reason". */
    val errors: List<String>,
)

private val stravaDateFormats = listOf(
    DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
)

/** Strava's "Activity Date" is in UTC; the local (Asia/Tokyo) date is what counts for a run. */
fun parseStravaDate(text: String): LocalDate? {
    val t = text.trim().replace(' ', ' ') // some exports use a narrow no-break space before AM/PM
    for (f in stravaDateFormats) {
        try {
            return LocalDateTime.parse(t, f).atOffset(ZoneOffset.UTC).atZoneSameInstant(TOKYO).toLocalDate()
        } catch (_: DateTimeParseException) {
        }
    }
    return null
}

private fun number(text: String?): Double? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return null
    // "3,45" (decimal comma) when there is no dot; otherwise commas are thousands separators.
    val normalised = if ('.' !in t && t.count { it == ',' } == 1) t.replace(',', '.') else t.replace(",", "")
    return normalised.toDoubleOrNull()
}

/**
 * Parses Strava's bulk-export activities.csv and keeps running activities (any type containing "Run").
 * Distance comes from the metres column when present (it doesn't depend on the km/mile setting).
 * Duration is moving time, falling back to elapsed time.
 */
fun parseStravaActivities(text: String): StravaParseResult {
    val rows = parseCsv(text)
    require(rows.isNotEmpty()) { "The file is empty" }
    val header = rows.first().map { it.trim() }

    fun columns(name: String) = header.indices.filter { header[it].equals(name, ignoreCase = true) }
    val idCol = columns("Activity ID").firstOrNull()
    val dateCol = columns("Activity Date").firstOrNull()
    val typeCol = columns("Activity Type").firstOrNull()
    val distanceCols = columns("Distance")
    val elapsedCols = columns("Elapsed Time")
    val movingCol = columns("Moving Time").firstOrNull()
    val caloriesCol = columns("Calories").firstOrNull()
    require(dateCol != null && typeCol != null && distanceCols.isNotEmpty() && (elapsedCols.isNotEmpty() || movingCol != null)) {
        "This doesn't look like Strava's activities.csv (missing Activity Date / Activity Type / Distance / time columns)"
    }

    val runs = mutableListOf<Run>()
    val skipped = sortedMapOf<String, Int>()
    val errors = mutableListOf<String>()

    rows.drop(1).forEachIndexed { index, row ->
        val rowNo = index + 2 // 1-based, counting the header
        fun cell(col: Int?) = col?.let { row.getOrNull(it) }
        val type = cell(typeCol)?.trim().orEmpty()
        if (!type.contains("Run", ignoreCase = true)) {
            skipped.merge(type.ifEmpty { "(no type)" }, 1, Int::plus)
            return@forEachIndexed
        }
        val date = cell(dateCol)?.let(::parseStravaDate)
        // With two Distance columns, the second is in metres; a single one is in km.
        val km = if (distanceCols.size >= 2) number(cell(distanceCols.last()))?.div(1000) else number(cell(distanceCols.first()))
        val seconds = (number(cell(movingCol))?.takeIf { it > 0 } ?: elapsedCols.firstNotNullOfOrNull { number(cell(it))?.takeIf { s -> s > 0 } })
            ?.roundToInt()
        val problem = when {
            date == null -> "unreadable date '${cell(dateCol)}'"
            km == null || km <= 0.05 -> "no distance"
            seconds == null -> "no time"
            else -> null
        }
        if (problem != null) {
            errors += "row $rowNo: $problem"
            return@forEachIndexed
        }
        runs += Run(
            date = date,
            km = Math.round(km!! * 100) / 100.0,
            durationSec = seconds!!,
            kcal = number(cell(caloriesCol))?.takeIf { it > 0 },
            source = "strava",
            externalId = cell(idCol)?.trim()?.ifEmpty { null },
        )
    }
    return StravaParseResult(runs, rows.size - 1, skipped, errors)
}

/** A run already stored in the app, with its database id. */
data class StoredRun(val id: Long, val run: Run)

data class ImportPlan(
    val inserts: List<Run>,
    /** Existing runs to replace with Strava's version (keeps their id). */
    val updates: List<StoredRun>,
    val alreadyImported: Int,
)

/**
 * Decides what to do with each Strava run:
 * - same Activity ID already stored → skip;
 * - a manual run on the same date with about the same distance (±0.05 km) → update it;
 * - an undated run (e.g. a seed run) with the same distance (±0.02 km) and time (±10 s) → update it, which sets its date;
 * - otherwise → insert.
 * Each stored run is matched at most once.
 */
fun planStravaImport(existing: List<StoredRun>, incoming: List<Run>): ImportPlan {
    val knownIds = existing.mapNotNull { it.run.externalId }.toMutableSet()
    val available = existing.filter { it.run.externalId == null }.toMutableList()
    val inserts = mutableListOf<Run>()
    val updates = mutableListOf<StoredRun>()
    var already = 0

    for (run in incoming) {
        if (run.externalId != null && !knownIds.add(run.externalId)) {
            already++
            continue
        }
        val match = available.firstOrNull { it.run.date == run.date && abs(it.run.km - run.km) <= 0.05 }
            ?: available.firstOrNull {
                it.run.date == null && abs(it.run.km - run.km) <= 0.02 && abs(it.run.durationSec - run.durationSec) <= 10
            }
        if (match != null) {
            available.remove(match)
            updates += StoredRun(match.id, run)
        } else {
            inserts += run
        }
    }
    return ImportPlan(inserts, updates, already)
}
