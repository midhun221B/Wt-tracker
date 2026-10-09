package wt.core

import wt.core.dashboard.buildDashboard
import wt.core.model.Defaults
import wt.core.model.WeightEntry
import wt.core.summary.goalProgress
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
    private val goal = 82.0
    private val planStart = 88.3

    @Test
    fun goalProgressIsTheDashboardsTrend() {
        val weights = Synthetic.series(30)
        val asOf = weights.last().date
        val dash = buildDashboard(weights, emptyList(), emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, asOf)
        val g = goalProgress(weights, asOf, goal, planStart)
        assertTrue(g.fromTrend)
        assertEquals(weights.first().kg, g.startKg)
        assertEquals(dash.forecast!!.trendToday, g.currentKg, 1e-9)
        assertEquals(g.startKg - g.currentKg, g.lostKg, 1e-9)
        assertEquals(g.lostKg / g.totalKg, g.fraction, 1e-9)
    }

    @Test
    fun goalProgressFallsBackToTheLatestWeight() {
        val g = goalProgress(listOf(w(1, 88.3), w(8, 87.6)), d(8), goal, planStart) // too few for a trend
        assertFalse(g.fromTrend)
        assertEquals(87.6, g.currentKg)
        assertEquals(0.7, g.lostKg, 1e-9)
    }

    @Test
    fun goalProgressBeforeAnyWeighIn() {
        val g = goalProgress(emptyList(), d(1), goal, planStart)
        assertEquals(planStart, g.startKg)
        assertEquals(0.0, g.fraction)
    }

    @Test
    fun weighInMovesTheRingFromTheTrendWithoutItToTheTrendWithIt() {
        val weights = listOf(w(1, 88.3), w(8, 87.9), w(15, 87.4), w(22, 87.2), w(29, 86.5))
        val p = weighInProgress(weights, d(29), goal, planStart)!!
        assertEquals(86.5, p.kg)
        assertEquals(-0.7, p.changeKg!!, 1e-9) // the scale change, not the trend's
        assertEquals(goalProgress(weights, d(29), goal, planStart), p.goal)
        assertEquals(goalProgress(weights.dropLast(1), d(29), goal, planStart).fraction, p.previousFraction, 1e-9)
    }

    @Test
    fun aGainMovesTheRingBack() {
        val weights = listOf(w(1, 88.3), w(8, 87.9), w(15, 87.4), w(22, 87.2), w(29, 88.4))
        val p = weighInProgress(weights, d(29), goal, planStart)!!
        assertEquals(1.2, p.changeKg!!, 1e-9)
        assertTrue(p.goal.fraction < p.previousFraction)
    }

    @Test
    fun firstWeighInHasNoChangeAndNoProgress() {
        val p = weighInProgress(listOf(w(1, 88.3)), d(1), goal, planStart)!!
        assertNull(p.changeKg)
        assertEquals(0.0, p.goal.fraction)
        assertEquals(p.goal.fraction, p.previousFraction)
    }

    @Test
    fun progressIsClampedPastTheGoal() {
        val p = weighInProgress(listOf(w(1, 88.3), w(8, 84.0), w(15, 81.0), w(22, 78.0)), d(22), goal, planStart)!!
        assertEquals(1.0, p.goal.fraction)
    }

    @Test
    fun noProgressWithoutAWeightThatDay() {
        assertNull(weighInProgress(listOf(w(1, 88.3)), d(2), goal, planStart))
    }
}
