package wt.core

import wt.core.io.StoredRun
import wt.core.io.parseCsv
import wt.core.io.parseStravaActivities
import wt.core.io.parseStravaDate
import wt.core.io.planStravaImport
import wt.core.model.Defaults
import wt.core.model.Run
import wt.core.model.formatMinSec
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StravaTest {
    private val sample = javaClass.getResource("/strava/activities.csv")!!.readText()

    @Test
    fun csvQuotingAndMultilineFields() {
        val rows = parseCsv("a,b,c\r\n\"x, y\",\"say \"\"hi\"\"\",\"line1\nline2\"\n\n1,,3")
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("x, y", "say \"hi\"", "line1\nline2"), rows[1])
        assertEquals(listOf("1", "", "3"), rows[2])
        assertEquals(3, rows.size)
    }

    @Test
    fun datesAreConvertedFromUtcToTokyo() {
        assertEquals(LocalDate.of(2026, 10, 9), parseStravaDate("Oct 8, 2026, 10:15:30 PM")) // 07:15 JST next day
        assertEquals(LocalDate.of(2026, 10, 10), parseStravaDate("Oct 10, 2026, 9:00:00 AM"))
        assertEquals(LocalDate.of(2026, 10, 10), parseStravaDate("2026-10-10 14:59:59"))
        assertEquals(LocalDate.of(2026, 10, 11), parseStravaDate("2026-10-10T15:00:00"))
        assertEquals(LocalDate.of(2026, 10, 9), parseStravaDate("Oct 8, 2026, 10:15:30 PM"))
        assertNull(parseStravaDate("not a date"))
    }

    @Test
    fun parsesSampleExport() {
        val r = parseStravaActivities(sample)
        assertEquals(8, r.totalActivities)
        assertEquals(mapOf("Ride" to 1, "Walk" to 1), r.skippedTypes)
        assertEquals(listOf("row 7: unreadable date 'not a date'"), r.errors)
        assertEquals(5, r.runs.size)

        val first = r.runs[0]
        assertEquals(LocalDate.of(2026, 10, 9), first.date)
        assertEquals(2.81, first.km, 1e-9)
        assertEquals(1216, first.durationSec) // moving time, not elapsed
        assertEquals(250.0, first.kcal)
        assertEquals("15000000001", first.externalId)
        assertEquals("strava", first.source)

        assertNull(r.runs[1].kcal) // empty calories
        val virtual = r.runs.first { it.externalId == "15000000007" }
        assertEquals(1283, virtual.durationSec) // no moving time → elapsed time
    }

    @Test
    fun singleDistanceColumnIsKm() {
        val csv = "Activity ID,Activity Date,Activity Type,Elapsed Time,Distance\n1,2026-10-20 12:00:00,Run,1800,\"4,5\"\n"
        val run = parseStravaActivities(csv).runs.single()
        assertEquals(4.5, run.km, 1e-9)
        assertEquals(1800, run.durationSec)
    }

    @Test
    fun rejectsOtherCsvFiles() {
        assertFailsWith<IllegalArgumentException> { parseStravaActivities("date,weight\n2026-10-08,88.3\n") }
        assertFailsWith<IllegalArgumentException> { parseStravaActivities("") }
    }

    @Test
    fun importDatesTheSeedRunsAndIsIdempotent() {
        val seeds = Defaults.sampleRuns.mapIndexed { i, run -> StoredRun(i + 1L, run) }
        val incoming = parseStravaActivities(sample).runs
        val plan = planStravaImport(seeds, incoming)
        // All five sample runs (2.81/20:16, 2.37/15:46, 3.45/24:19, 3.14/21:23, 2.54/17:28) are in the export.
        assertEquals(5, plan.updates.size)
        assertTrue(plan.inserts.isEmpty())
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), plan.updates.map { it.id }.toSet())
        assertEquals(LocalDate.of(2026, 10, 9), plan.updates.first { it.id == 1L }.run.date)

        // Importing the same file again changes nothing.
        val stored = plan.updates
        val again = planStravaImport(stored, incoming)
        assertEquals(5, again.alreadyImported)
        assertTrue(again.inserts.isEmpty() && again.updates.isEmpty())
    }

    @Test
    fun importMatchesManualRunsOnTheSameDay() {
        val manual = StoredRun(10, Run(LocalDate.of(2026, 10, 14), 3.43, 1500))
        val other = StoredRun(11, Run(LocalDate.of(2026, 10, 14), 5.0, 1800))
        val incoming = listOf(
            Run(LocalDate.of(2026, 10, 14), 3.45, 1459, 300.0, "strava", "A"),
            Run(LocalDate.of(2026, 10, 20), 4.0, 1700, null, "strava", "B"),
        )
        val plan = planStravaImport(listOf(manual, other), incoming)
        assertEquals(listOf(10L), plan.updates.map { it.id })
        assertEquals(listOf("B"), plan.inserts.map { it.externalId })
    }

    @Test
    fun printParseReport() {
        val r = parseStravaActivities(sample)
        val plan = planStravaImport(Defaults.sampleRuns.mapIndexed { i, run -> StoredRun(i + 1L, run) }, r.runs)
        println("==== Strava import report for sample activities.csv ====")
        println("${r.totalActivities} activities: ${r.runs.size} runs, skipped ${r.skippedTypes}, ${r.errors.size} unreadable")
        r.errors.forEach { println("  ! $it") }
        r.runs.forEach {
            println("  ${it.date}  %.2f km  %s  %s/km  %s".format(it.km, formatMinSec(it.durationSec.toDouble()), formatMinSec(it.paceSecPerKm), it.kcal?.let { k -> "%.0f kcal".format(k) } ?: "-"))
        }
        println("Plan: ${plan.inserts.size} new, ${plan.updates.size} matched existing (seed runs get dates), ${plan.alreadyImported} already imported")
    }
}
