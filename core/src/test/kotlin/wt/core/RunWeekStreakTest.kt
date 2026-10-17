package wt.core

import wt.core.summary.runWeekStreak
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunWeekStreakTest {
    // Monday–Sunday weeks from Monday 5 Oct 2026.
    private val w1 = LocalDate.of(2026, 10, 5)
    private fun week(n: Int) = w1.plusWeeks(n - 1L)
    private fun runs(n: Int, vararg days: Long) = days.map { week(n).plusDays(it) }

    @Test
    fun countsWeeksWithThreeRunsInARow() {
        val dates = runs(1, 0, 2, 4) + runs(2, 0, 1, 2, 3, 4) + runs(3, 1, 3, 5)
        // Monday of week 4, no run yet: the open week doesn't break it.
        val s = runWeekStreak(dates, week(4))
        assertEquals(3, s.weeks)
        assertFalse(s.thisWeekCounts)
        assertEquals(0, s.runsThisWeek)
        assertEquals(3, s.runsToGo)
    }

    @Test
    fun thisWeekCountsOnceItHasThreeRuns() {
        val dates = runs(1, 0, 2, 4) + runs(2, 0, 2, 4)
        assertEquals(1, runWeekStreak(dates, week(2).plusDays(3)).weeks) // two runs so far: week 1 only
        val s = runWeekStreak(dates, week(2).plusDays(4))
        assertEquals(2, s.weeks)
        assertTrue(s.thisWeekCounts)
        assertEquals(0, s.runsToGo)
    }

    @Test
    fun aWeekWithTwoRunsBreaksIt() {
        val dates = runs(1, 0, 2, 4) + runs(2, 0, 2) + runs(3, 0, 2, 4)
        val s = runWeekStreak(dates, week(4))
        assertEquals(1, s.weeks)
        assertNull(s.endedWeeks)
    }

    @Test
    fun anEndedStreakRemembersItsLength() {
        val dates = runs(1, 0, 2, 4) + runs(2, 0, 2, 4) + runs(3, 1)
        val s = runWeekStreak(dates, week(4).plusDays(1))
        assertEquals(0, s.weeks)
        assertEquals(2, s.endedWeeks)
    }

    @Test
    fun runsAfterTheDayDontCount() {
        val dates = runs(1, 0, 2, 4)
        assertEquals(0, runWeekStreak(dates, week(1).plusDays(3)).weeks)
        assertNull(runWeekStreak(emptyList(), week(1)).endedWeeks)
    }
}
