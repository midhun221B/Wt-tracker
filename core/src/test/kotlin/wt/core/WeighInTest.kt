package wt.core

import wt.core.summary.weighInStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeighInTest {
    private fun d(day: Int) = LocalDate.of(2026, 10, day) // 5 Oct 2026 is a Monday

    @Test
    fun doneEarlierInTheWeekMeansNotDueOnTheDay() {
        // Weighed on Thu 8 Oct; weigh-in day Friday; on Fri 9 Oct nothing is due.
        val s = weighInStatus(listOf(d(8)), d(9), weighInDay = 5)
        assertEquals(d(8), s.doneOn)
        assertFalse(s.due)
        assertEquals(d(16), s.next)
    }

    @Test
    fun dueOnTheDayWhenNothingLoggedThisWeek() {
        val s = weighInStatus(listOf(d(1)), d(12), weighInDay = 1)
        assertNull(s.doneOn)
        assertTrue(s.due)
        assertEquals(d(12), s.next)
    }

    @Test
    fun stillDueAfterAMissedDay() {
        assertTrue(weighInStatus(emptyList(), d(14), weighInDay = 1).due)
    }

    @Test
    fun notDueBeforeTheDay() {
        val s = weighInStatus(emptyList(), d(6), weighInDay = 4)
        assertFalse(s.due)
        assertEquals(d(8), s.next)
    }

    @Test
    fun laterEntriesDoNotCountForAnEarlierDate() {
        // Viewing Tue 6 Oct when the week's weigh-in is on Thu 8 Oct.
        val s = weighInStatus(listOf(d(8)), d(6), weighInDay = 2)
        assertNull(s.doneOn)
        assertTrue(s.due)
    }
}
