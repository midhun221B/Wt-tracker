package wt.core

import wt.core.dashboard.buildDashboard
import wt.core.model.Defaults
import wt.core.model.Run
import wt.core.model.WeightEntry
import wt.core.summary.Suggestion
import wt.core.summary.weeklyReview
import wt.core.trend.fitTrend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeeklyReviewTest {
    private val start = Defaults.START // Thu 8 Oct 2026
    private fun day(n: Int) = start.plusDays(n.toLong())

    /** Weekly weigh-ins on days 0, 7, … starting at the plan's start weight and changing [kgPerWeek] a week. */
    private fun weekly(weeks: Int, kgPerWeek: Double): List<WeightEntry> =
        (0 until weeks).map { w -> WeightEntry(day(7 * w), Defaults.START_KG + kgPerWeek * w) }

    private fun review(weights: List<WeightEntry>, runs: List<Run> = emptyList(), on: java.time.LocalDate = weights.last().date) =
        weeklyReview(buildDashboard(weights, runs, emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, on), runs, on)

    @Test
    fun noReviewWithoutAWeighInThatDayOrForTheFirstOne() {
        assertNull(review(weekly(3, -0.5), on = day(15)))
        assertNull(review(weekly(1, 0.0)))
    }

    @Test
    fun countsRunsSinceTheLastWeighInAndTheStretchBefore() {
        val runs = listOf(0, 3, 7, 13, 14).map { Run(day(it), 3.0, 1200) }
        val r = review(weekly(3, -0.5), runs)!!
        assertEquals(day(7), r.since)
        assertEquals(2, r.runs) // days 7 and 13; the run on the weigh-in day counts next week
        assertEquals(6.0, r.km, 1e-9)
        assertEquals(2, r.previousRuns) // days 0 and 3
    }

    @Test
    fun tooEarlyWithTwoWeighIns() {
        val r = review(weekly(2, -0.5))!!
        assertEquals(Suggestion.TooEarly, r.suggestion)
        assertNull(r.trendChangeKg)
    }

    @Test
    fun onPlanWhenAhead() {
        val weights = weekly(5, -0.6)
        val r = review(weights)!!
        assertEquals(Suggestion.OnPlan, r.suggestion)
        assertTrue(r.gapKg!! < 0)
        val expected = fitTrend(weights, day(28))!!.valueAt(day(28)) - fitTrend(weights.dropLast(1), day(21))!!.valueAt(day(21))
        assertEquals(expected, r.trendChangeKg!!, 1e-9)
    }

    @Test
    fun runMoreWhenSlightlyBehind() {
        // Plan pace (~0.47 kg/week) but 0.12 kg behind: a few extra km a week closes it.
        val r = review((0 until 5).map { w -> WeightEntry(day(7 * w), Defaults.START_KG + 0.12 - 0.474 * w) })!!
        val s = assertIs<Suggestion.RunMore>(r.suggestion)
        assertTrue(s.kmPerWeek in 0.5..6.0)
    }

    @Test
    fun eatLessWhenWellBehind() {
        val s = assertIs<Suggestion.EatLess>(review(weekly(5, -0.2))!!.suggestion)
        assertTrue(s.lessKcal > 0)
        assertTrue(s.targetKcal >= Safety.MIN_INTAKE_KCAL)
    }

    @Test
    fun neverSuggestsBelowTheFloor() {
        val s = assertIs<Suggestion.EatLess>(review(weekly(5, +0.5))!!.suggestion) // gaining
        assertTrue(s.floored)
        assertEquals(Safety.MIN_INTAKE_KCAL, s.targetKcal, 1e-9)
    }

    @Test
    fun slowDownWhenLosingFasterThanOneKgAWeek() {
        val s = assertIs<Suggestion.SlowDown>(review(weekly(5, -1.5))!!.suggestion)
        assertTrue(s.targetKcal >= Safety.MIN_INTAKE_KCAL)
    }
}
