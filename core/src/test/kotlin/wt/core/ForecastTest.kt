package wt.core

import wt.core.model.Checkpoint
import wt.core.model.Defaults
import wt.core.model.WeightEntry
import wt.core.plan.PlannedLine
import wt.core.trend.etaFor
import wt.core.trend.fitTrend
import wt.core.trend.forecast
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecastTest {
    private val plan = PlannedLine(Defaults.planCheckpoints)
    private val planRate = (88.3 - 82.0) / 91 * 7 // ≈ 0.485 kg/week

    @Test
    fun plannedLinePassesThroughCheckpoints() {
        for (c in Defaults.planCheckpoints) assertEquals(c.kg, plan.at(c.date), 1e-9)
        assertEquals(88.3, plan.at(LocalDate.of(2026, 1, 1)), 1e-9)
        assertEquals(82.0, plan.at(LocalDate.of(2027, 6, 1)), 1e-9)
        // Halfway between 11-08 (86.2) and 12-08 (84.0) is 11-23 → 85.1.
        assertEquals(85.1, plan.at(LocalDate.of(2026, 11, 23)), 1e-9)
    }

    @Test
    fun plannedLineDateReaching() {
        assertEquals(LocalDate.of(2026, 11, 8), plan.dateReaching(86.2))
        assertEquals(Defaults.GOAL_DATE, plan.dateReaching(82.0))
        assertNull(plan.dateReaching(80.0))
    }

    @Test
    fun onPlanTrendHitsGoalNearGoalDate() {
        val data = Synthetic.series(days = 28, kgPerWeek = planRate, noiseSd = 0.3, seed = 11)
        val f = forecast(fitTrend(data, data.last().date)!!, plan)
        val gapDays = assertNotNull(f.gapDays)
        assertTrue(abs(gapDays) <= 14, "gap $gapDays days")
        assertEquals(0.0, f.gapKgAtGoal, 0.6)
        assertTrue(f.bandAtGoal.first < 82.0 && 82.0 < f.bandAtGoal.second)
    }

    @Test
    fun slowLossIsBehindPlan() {
        val data = Synthetic.series(days = 28, kgPerWeek = 0.25, noiseSd = 0.3, seed = 12)
        val f = forecast(fitTrend(data, data.last().date)!!, plan)
        assertTrue(f.predictedAtGoal > 83.0, "predicted ${f.predictedAtGoal}")
        assertTrue(f.gapKgAtGoal > 1.0)
        assertTrue(f.gapDays!! > 30)
        assertTrue(f.gapKgToday > 0)
    }

    @Test
    fun etaRangeIsOrdered() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.5, noiseSd = 0.4, seed = 13)
        val f = forecast(fitTrend(data, data.last().date)!!, plan)
        val eta = f.eta!!
        assertTrue(f.etaEarly!! <= eta)
        assertTrue(f.etaLate == null || eta <= f.etaLate)
    }

    @Test
    fun plateauNeverReachesGoal() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.0, noiseSd = 0.0, seed = 14)
        val f = forecast(fitTrend(data, data.last().date)!!, plan)
        assertNull(f.eta)
        assertNull(f.gapDays)
        assertEquals(88.3, f.predictedAtGoal, 1e-9)
    }

    @Test
    fun alreadyAtGoal() {
        val asOf = LocalDate.of(2026, 12, 20)
        val data = (0 until 10).map { WeightEntry(asOf.minusDays(it.toLong()), 81.5) }
        val f = forecast(fitTrend(data, asOf)!!, plan)
        assertEquals(asOf, f.eta)
        assertTrue(f.gapDays!! < 0)
    }

    @Test
    fun etaForMath() {
        val d = LocalDate.of(2026, 10, 8)
        assertEquals(d.plusDays(70), etaFor(87.0, -0.5 / 7, 82.0, d))
        assertNull(etaFor(87.0, 0.01, 82.0, d))
        assertNull(etaFor(87.0, -0.001, 82.0, d)) // more than 3 years away
    }

    @Test
    fun editedPlanIsUsed() {
        val easier = PlannedLine(listOf(Checkpoint(Defaults.START, 88.3), Checkpoint(LocalDate.of(2027, 3, 1), 82.0)))
        val data = Synthetic.series(days = 21, kgPerWeek = 0.3, noiseSd = 0.2, seed = 15)
        val f = forecast(fitTrend(data, data.last().date)!!, easier)
        assertEquals(LocalDate.of(2027, 3, 1), f.goalDate)
        assertEquals(ChronoUnit.DAYS.between(f.goalDate, f.eta), f.gapDays)
    }
}
