package wt.core

import wt.core.model.Run
import wt.core.summary.DayKind
import wt.core.summary.dailyDistance
import wt.core.summary.distanceSpan
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyDistanceTest {
    private fun d(day: Int, month: Int = 10) = LocalDate.of(2026, month, day)

    // First run Thursday 8 Oct; today Thursday 15 Oct.
    private val runs = listOf(
        Run(d(8), 3.0, 1200), Run(d(10), 2.0, 800), Run(d(10), 1.5, 600),
        Run(d(12), 3.2, 1300), Run(d(14), 2.8, 1100), Run(null, 5.0, 2000),
    )
    private val rest = setOf(d(9), d(13), d(14))
    private val weeks = dailyDistance(runs, rest, firstDay = d(8), asOf = d(15))

    @Test
    fun oneRowPerMondayToSundayWeek() {
        assertEquals(listOf(d(5), d(12)), weeks.map { it.monday })
        assertTrue(weeks.all { it.days.size == 7 })
    }

    @Test
    fun classifiesEachDay() {
        val first = weeks[0].days.map { it.kind }
        assertEquals(
            listOf(DayKind.BeforeStart, DayKind.BeforeStart, DayKind.BeforeStart, DayKind.Run, DayKind.Rest, DayKind.Run, DayKind.Missed),
            first,
        )
        val second = weeks[1].days.map { it.kind }
        // A run on a rest day counts as a run; today without a run is open, not missed.
        assertEquals(
            listOf(DayKind.Run, DayKind.Rest, DayKind.Run, DayKind.Open, DayKind.Future, DayKind.Future, DayKind.Future),
            second,
        )
    }

    @Test
    fun runsOnTheSameDayAddUpAndUndatedRunsAreLeftOut() {
        val sat = weeks[0].days[5]
        assertEquals(3.5, sat.km, 1e-9)
        assertEquals(2, sat.runs)
        assertEquals(6.5, weeks[0].km, 1e-9)
        assertEquals(6.0, weeks[1].km, 1e-9)
    }

    @Test
    fun spanTotals() {
        val s = distanceSpan(weeks, 2)
        assertEquals(14, s.days.size)
        assertEquals(6.0, s.thisWeekKm, 1e-9)
        assertEquals(6.5, s.lastWeekKm!!, 1e-9)
        assertEquals(3.5, s.longestKm, 1e-9)
        assertEquals(12.5 / 5, s.averageRunKm!!, 1e-9)
        assertEquals(4, s.runDays)
        assertEquals(8, s.daysSoFar) // 8–15 Oct
    }

    @Test
    fun emptyBeforeAnythingIsLogged() {
        assertTrue(dailyDistance(emptyList(), emptySet(), null, d(15)).isEmpty())
        val s = distanceSpan(emptyList(), 2)
        assertNull(s.lastWeekKm)
        assertNull(s.averageRunKm)
    }
}
