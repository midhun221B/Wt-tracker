package wt.core

import wt.core.model.Run
import wt.core.summary.DayKind
import wt.core.summary.allTimeDistance
import wt.core.summary.dailyDistance
import wt.core.summary.distanceMonth
import wt.core.summary.distanceMonths
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DistanceViewsTest {
    private fun d(day: Int, month: Int = 10) = LocalDate.of(2026, month, day)

    // First run Thursday 8 Oct; today Thursday 15 Oct; goal 7 Jan.
    private val runs = listOf(
        Run(d(8), 3.0, 1200), Run(d(10), 2.0, 800), Run(d(10), 1.5, 600), Run(d(12), 3.2, 1300), Run(d(14), 2.8, 1100),
    )
    private val weeks = dailyDistance(runs, setOf(d(9), d(13)), firstDay = d(8), asOf = d(15))

    @Test
    fun dayTotalsIncludeTheRunTime() {
        assertEquals(1400, weeks[0].days[5].durationSec) // two runs on 10 Oct
    }

    @Test
    fun monthIsCalendarRowsWithTotals() {
        val m = distanceMonth(weeks, YearMonth.of(2026, 10))
        assertEquals(5, m.rows.size) // weeks of 28 Sep, 5, 12, 19 and 26 Oct
        assertNull(m.rows[0][0]) // 28 Sep is outside October
        assertEquals(DayKind.BeforeStart, m.rows[0][3]!!.kind) // 1 Oct
        assertEquals(DayKind.Open, m.rows[2][3]!!.kind) // today, 15 Oct
        assertEquals(DayKind.Future, m.rows[3][1]!!.kind) // 20 Oct, after the covered weeks
        assertEquals(12.5, m.km, 1e-9)
        assertEquals(5, m.runs)
        assertEquals(2, m.restDays)
    }

    @Test
    fun monthsFromTheFirstEntryToToday() {
        assertEquals(listOf(YearMonth.of(2026, 10)), distanceMonths(weeks, d(15)))
        val later = dailyDistance(runs, emptySet(), d(8), d(2, 11))
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), distanceMonths(later, d(2, 11)))
    }

    @Test
    fun allTimeRunsToTheGoalWeek() {
        val a = allTimeDistance(weeks, d(15), LocalDate.of(2027, 1, 7))
        assertEquals(14, a.weeks.size) // 5 Oct to the week of 4 Jan
        assertEquals(listOf(6.5, 6.0), a.weeks.take(2).map { it.km })
        assertEquals(true, a.weeks[1].current)
        assertNull(a.weeks[2].km)
        assertEquals(12, a.weeksToGo)
        assertEquals(12.5, a.totalKm, 1e-9)
        assertEquals(5, a.runs)
        assertEquals(d(8), a.since)
        assertEquals(6.5, a.bestWeekKm, 1e-9)
        assertEquals(6.5, a.averageWeekKm!!, 1e-9) // only the finished week
        assertEquals(11.0 / 98, a.progress, 1e-9)
        assertEquals(94.5 / 98, a.goalAt, 1e-9)
    }
}
