package wt.core

import wt.core.energy.energyBalance
import wt.core.energy.runNetKcal
import wt.core.model.Defaults
import wt.core.model.Run
import wt.core.trend.fitTrend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnergyTest {
    private val profile = Defaults.profile

    @Test
    fun actualDeficitFromSlope() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.5, noiseSd = 0.0)
        val fit = fitTrend(data, data.last().date)!!
        val e = energyBalance(fit, emptyList(), profile)
        assertEquals(550.0, e.actualDeficit, 1e-6) // 0.5 kg/week × 7700 / 7
        assertEquals(350.0, e.expectedDeficit, 1e-9)
    }

    @Test
    fun runsRaiseExpectedDeficit() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.5, noiseSd = 0.0)
        val fit = fitTrend(data, data.last().date)!!
        // 15 runs of 3 km in 21 days, no logged kcal → 0.9 × weight × km each.
        val runs = (0 until 15).map { Run(Synthetic.START.plusDays(it.toLong()), 3.0, 21 * 60) }
        val e = energyBalance(fit, runs, profile)
        val expectedRun = 15 * 0.9 * fit.valueAt(fit.origin) * 3.0 / 21
        assertEquals(expectedRun, e.runNetKcalPerDay, 1e-6)
        assertEquals(350 + expectedRun, e.expectedDeficit, 1e-6)
    }

    @Test
    fun runNetSubtractsRestingBurnFromLoggedKcal() {
        val run = Run(Synthetic.START, 3.0, 24 * 60, kcal = 260.0)
        assertEquals(260.0 - 1818.0 / 1440 * 24, runNetKcal(run, 88.0, 1818.0), 1e-9)
        assertEquals(0.9 * 88.0 * 3.0, runNetKcal(run.copy(kcal = null), 88.0, 1818.0), 1e-9)
    }

    @Test
    fun behindPlanSuggestsEatingLess() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.3, noiseSd = 0.0)
        val fit = fitTrend(data, data.last().date)!!
        val e = energyBalance(fit, emptyList(), profile)
        assertTrue(e.requiredKgPerWeek!! > 0.3)
        assertTrue(e.intakeChange < 0)
        assertEquals((e.requiredKgPerWeek!! - 0.3) / 7 * 7700, -e.intakeChange, 1e-6)
        assertFalse(e.intakeFloored)
    }

    @Test
    fun requiredRateIsCappedAtOneKgPerWeek() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.1, noiseSd = 0.0)
        val fit = fitTrend(data, data.last().date)!!
        val e = energyBalance(fit, emptyList(), profile, goalDate = fit.origin.plusDays(14))
        assertEquals(1.0, e.requiredKgPerWeek!!, 1e-9)
        assertTrue(e.requiredCapped)
    }

    @Test
    fun suggestedIntakeNeverBelowFloor() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.1, noiseSd = 0.0)
        val fit = fitTrend(data, data.last().date)!!
        val lowBmr = profile.copy(bmrKcal = 1350.0)
        val e = energyBalance(fit, emptyList(), lowBmr, goalDate = fit.origin.plusDays(14))
        assertEquals(Safety.MIN_INTAKE_KCAL, e.targetIntake, 1e-9)
        assertTrue(e.intakeFloored)
        assertEquals(e.targetIntake - e.estimatedIntake, e.intakeChange, 1e-9)
    }

    @Test
    fun plannedIntakeIsMaintenancePlusRunsMinusPlannedDeficit() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.5, noiseSd = 0.0)
        val fit = fitTrend(data, data.last().date)!!
        val e = energyBalance(fit, emptyList(), profile)
        assertEquals(e.maintenanceKcal - profile.plannedFoodDeficitKcal, e.plannedIntake, 1e-9)
        // Losing faster than the plan's deficit means eating less than the plan assumes.
        assertEquals(e.plannedIntake - (e.actualDeficit - e.expectedDeficit), e.estimatedIntake, 1e-9)
    }

    @Test
    fun goalDatePassedGivesNoRequirement() {
        val data = Synthetic.series(days = 21, kgPerWeek = 0.3, noiseSd = 0.0)
        val fit = fitTrend(data, data.last().date)!!
        val e = energyBalance(fit, emptyList(), profile, goalDate = fit.origin.minusDays(1))
        assertEquals(null, e.requiredKgPerWeek)
        assertEquals(0.0, e.intakeChange, 1e-9)
    }
}
