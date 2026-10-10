package wt.core

import wt.core.model.Checkpoint
import wt.core.plan.firstPlan
import wt.core.plan.steadyGoalDate
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstPlanTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun startsAtTodaysWeightWithMonthlyCheckpoints() {
        val p = firstPlan(today, 88.3, LocalDate.of(2027, 1, 7), 82.0)
        assertFalse(p.fromTrend)
        assertEquals(listOf(today, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 7)), p.checkpoints.map { it.date })
        assertEquals(Checkpoint(today, 88.3), p.checkpoints.first())
        assertEquals(86.1, p.checkpoints[1].kg, 0.05)
        assertEquals(84.0, p.checkpoints[2].kg, 0.05)
        assertEquals(0.50, p.requiredKgPerWeek!!, 0.01)
        assertFalse(p.unrealistic)
        assertFalse(p.exceedsSafeMax)
    }

    @Test
    fun flagsAGoalAboveTheSafetyLimit() {
        val p = firstPlan(today, 88.3, LocalDate.of(2026, 11, 7), 82.0)
        assertEquals(1.58, p.requiredKgPerWeek!!, 0.01)
        assertTrue(p.unrealistic)
        assertTrue(p.exceedsSafeMax)
    }

    @Test
    fun ambitiousBetweenPointSevenAndOne() {
        val p = firstPlan(today, 88.3, LocalDate.of(2026, 12, 3), 82.0)
        assertTrue(p.unrealistic)
        assertFalse(p.exceedsSafeMax)
    }

    @Test
    fun steadyDateIsHalfAKiloAWeek() {
        assertEquals(LocalDate.of(2027, 1, 7), steadyGoalDate(today, 88.3, 82.0))
        assertEquals(today.plusDays(7), steadyGoalDate(today, 82.1, 82.0))
    }
}
