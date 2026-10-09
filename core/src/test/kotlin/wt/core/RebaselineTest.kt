package wt.core

import wt.core.model.Defaults
import wt.core.model.Run
import wt.core.model.WeightEntry
import wt.core.plan.PlannedLine
import wt.core.plan.rebaseline
import wt.core.summary.programWeekIndex
import wt.core.summary.weeklySummary
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RebaselineTest {
    @Test
    fun keepsGoalDateAndComputesRequiredRate() {
        val data = Synthetic.series(days = 28, kgPerWeek = 0.3, noiseSd = 0.0)
        val asOf = data.last().date
        val r = rebaseline(data, asOf, Defaults.GOAL_DATE, Defaults.GOAL_KG)
        assertTrue(r.fromTrend)
        val days = java.time.temporal.ChronoUnit.DAYS.between(asOf, Defaults.GOAL_DATE)
        assertEquals((r.startKg - 82.0) / days * 7, r.requiredKgPerWeek!!, 1e-9)
        assertEquals(asOf, r.checkpoints.first().date)
        assertEquals(Defaults.GOAL_DATE, r.checkpoints.last().date)
        // Checkpoints lie on a straight, decreasing line and form a valid plan.
        assertTrue(r.checkpoints.zipWithNext().all { (a, b) -> b.kg < a.kg })
        PlannedLine(r.checkpoints)
        assertFalse(r.unrealistic)
    }

    @Test
    fun flagsUnrealisticAndUnsafeRates() {
        val asOf = LocalDate.of(2026, 12, 10)
        val heavy = (0 until 10).map { WeightEntry(asOf.minusDays(it.toLong()), 86.5) }
        val r = rebaseline(heavy, asOf, Defaults.GOAL_DATE, Defaults.GOAL_KG)
        assertTrue(r.requiredKgPerWeek!! > 1.0)
        assertTrue(r.unrealistic)
        assertTrue(r.exceedsSafeMax)

        val moderate = (0 until 10).map { WeightEntry(asOf.minusDays(it.toLong()), 85.0) }
        val m = rebaseline(moderate, asOf, Defaults.GOAL_DATE, Defaults.GOAL_KG)
        assertTrue(m.requiredKgPerWeek!! in 0.7..1.0)
        assertTrue(m.unrealistic)
        assertFalse(m.exceedsSafeMax)
    }

    @Test
    fun usesLatestWeightWhenTooFewPoints() {
        val asOf = LocalDate.of(2026, 10, 10)
        val r = rebaseline(listOf(WeightEntry(asOf, 87.9)), asOf, Defaults.GOAL_DATE, Defaults.GOAL_KG)
        assertFalse(r.fromTrend)
        assertEquals(87.9, r.startKg, 1e-9)
    }

    @Test
    fun goalDatePassed() {
        val asOf = Defaults.GOAL_DATE.plusDays(1)
        val r = rebaseline(listOf(WeightEntry(asOf, 83.0)), asOf, Defaults.GOAL_DATE, Defaults.GOAL_KG)
        assertNull(r.requiredKgPerWeek)
        assertTrue(r.checkpoints.isEmpty())
    }

    @Test
    fun needsAWeight() {
        assertFailsWith<IllegalStateException> { rebaseline(emptyList(), Defaults.START, Defaults.GOAL_DATE, 82.0) }
    }

    @Test
    fun weeklyTable() {
        val start = LocalDate.of(2026, 10, 5) // a Monday
        val weights = (0 until 14).map { WeightEntry(start.plusDays(it.toLong()), if (it < 7) 88.0 else 87.5) }
        val runs = listOf(0, 1, 3, 8).map { Run(start.plusDays(it.toLong()), 3.0, 1260) } + Run(null, 2.0, 900)
        val weeks = weeklySummary(weights, runs, start, start.plusDays(13))
        assertEquals(2, weeks.size)
        assertEquals(88.0, weeks[0].avgKg!!, 1e-9)
        assertNull(weeks[0].changeKg)
        assertEquals(-0.5, weeks[1].changeKg!!, 1e-9)
        assertEquals(3, weeks[0].runs)
        assertEquals(9.0, weeks[0].km, 1e-9)
        assertEquals(4, weeks[0].restDays)
        assertEquals(6, weeks[1].restDays)
    }

    @Test
    fun runsBeforeThePlanCountInCalendarWeeks() {
        // Plan starts Thu 8 Oct; runs on Sun 4 Oct to Wed 7 Oct.
        val runs = (4..7).map { Run(LocalDate.of(2026, 10, it), 3.0, 1260) }
        val weeks = weeklySummary(listOf(WeightEntry(Defaults.START, 88.3)), runs, Defaults.START, LocalDate.of(2026, 10, 9))
        assertEquals(listOf(0, 1), weeks.map { it.index })
        assertEquals(LocalDate.of(2026, 9, 28), weeks[0].start)
        assertEquals(1, weeks[0].runs) // Sunday 4 Oct
        assertEquals(3, weeks[1].runs) // 5–7 Oct, same week as the first weigh-in
        assertEquals(9.0, weeks[1].km, 1e-9)
    }

    @Test
    fun oldRunsReachBackAtMostFourWeeks() {
        val runs = listOf(Run(LocalDate.of(2025, 1, 1), 5.0, 1800), Run(LocalDate.of(2026, 9, 20), 3.0, 1260))
        val weeks = weeklySummary(emptyList(), runs, Defaults.START, Defaults.START)
        assertEquals(-3, weeks.first().index)
        assertEquals(1, weeks.last().index)
        assertEquals(1, weeks.sumOf { it.runs })
    }

    @Test
    fun programWeekNumbers() {
        assertEquals(1, programWeekIndex(Defaults.START, LocalDate.of(2026, 10, 5)))
        assertEquals(0, programWeekIndex(Defaults.START, LocalDate.of(2026, 10, 4)))
        assertEquals(2, programWeekIndex(Defaults.START, LocalDate.of(2026, 10, 12)))
        assertEquals(14, programWeekIndex(Defaults.START, Defaults.GOAL_DATE))
    }
}
