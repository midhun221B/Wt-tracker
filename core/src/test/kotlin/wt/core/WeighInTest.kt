package wt.core

import wt.core.model.WeightEntry
import wt.core.summary.weighInProgress
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

    private fun w(day: Int, kg: Double) = WeightEntry(d(day), kg)

    @Test
    fun progressAfterALoss() {
        val p = weighInProgress(listOf(w(1, 88.3), w(8, 87.4), w(15, 86.9)), d(15), goalKg = 82.0)!!
        assertEquals(86.9, p.kg)
        assertEquals(-0.5, p.changeKg!!, 1e-9)
        assertEquals(1.4, p.lostKg, 1e-9)
        assertEquals(6.3, p.totalKg, 1e-9)
        assertEquals(1.4 / 6.3, p.fraction, 1e-9)
        assertEquals(0.9 / 6.3, p.previousFraction, 1e-9)
    }

    @Test
    fun progressGoesBackAfterAGain() {
        val p = weighInProgress(listOf(w(1, 88.3), w(8, 87.4), w(15, 87.7)), d(15), goalKg = 82.0)!!
        assertEquals(0.3, p.changeKg!!, 1e-9)
        assertTrue(p.fraction < p.previousFraction)
    }

    @Test
    fun firstWeighInHasNoChangeAndNoProgress() {
        val p = weighInProgress(listOf(w(1, 88.3)), d(1), goalKg = 82.0)!!
        assertNull(p.changeKg)
        assertEquals(0.0, p.fraction)
        assertEquals(p.fraction, p.previousFraction)
    }

    @Test
    fun progressIsClampedPastTheGoal() {
        val p = weighInProgress(listOf(w(1, 88.3), w(8, 81.5)), d(8), goalKg = 82.0)!!
        assertEquals(1.0, p.fraction)
        assertEquals(6.8, p.lostKg, 1e-9)
    }

    @Test
    fun noProgressWithoutAWeightThatDay() {
        assertNull(weighInProgress(listOf(w(1, 88.3)), d(2), goalKg = 82.0))
    }
}
