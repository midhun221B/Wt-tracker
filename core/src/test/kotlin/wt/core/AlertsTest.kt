package wt.core

import wt.core.alerts.AlertKind
import wt.core.alerts.evaluateAlerts
import wt.core.alerts.runStreak
import wt.core.model.Run
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertsTest {
    private fun kinds(alerts: List<wt.core.alerts.Alert>) = alerts.map { it.kind }.toSet()

    @Test
    fun slowLossForTwoWeeks() {
        val data = Synthetic.series(days = 35, kgPerWeek = 0.15, noiseSd = 0.2, seed = 21)
        val alerts = evaluateAlerts(data, emptyList(), emptySet(), data.last().date)
        assertTrue(AlertKind.SLOW_LOSS in kinds(alerts))
        assertTrue(alerts.first { it.kind == AlertKind.SLOW_LOSS }.message.contains("150 kcal/day"))
    }

    @Test
    fun noSlowAlertWhenOnTrack() {
        val data = Synthetic.series(days = 35, kgPerWeek = 0.5, noiseSd = 0.2, seed = 22)
        val alerts = evaluateAlerts(data, emptyList(), emptySet(), data.last().date)
        assertFalse(AlertKind.SLOW_LOSS in kinds(alerts))
        assertFalse(AlertKind.FAST_LOSS in kinds(alerts))
    }

    @Test
    fun slowAlertWithoutEnoughHistoryIsSuppressed() {
        val data = Synthetic.series(days = 10, kgPerWeek = 0.0, noiseSd = 0.2, seed = 23)
        assertFalse(AlertKind.SLOW_LOSS in kinds(evaluateAlerts(data, emptyList(), emptySet(), data.last().date)))
    }

    @Test
    fun slowAlertRespectsIntakeFloor() {
        val data = Synthetic.series(days = 35, kgPerWeek = 0.1, noiseSd = 0.2, seed = 24)
        val alerts = evaluateAlerts(data, emptyList(), emptySet(), data.last().date, estimatedIntake = 1850.0)
        val msg = alerts.first { it.kind == AlertKind.SLOW_LOSS }.message
        assertTrue(msg.contains("50 kcal/day"), msg)
        val atFloor = evaluateAlerts(data, emptyList(), emptySet(), data.last().date, estimatedIntake = 1800.0)
        assertTrue(atFloor.first { it.kind == AlertKind.SLOW_LOSS }.message.contains("don't cut further"))
    }

    @Test
    fun fastLossWarnsAboutMuscle() {
        val data = Synthetic.series(days = 21, kgPerWeek = 1.2, noiseSd = 0.2, seed = 25)
        val alerts = evaluateAlerts(data, emptyList(), emptySet(), data.last().date)
        assertTrue(AlertKind.FAST_LOSS in kinds(alerts))
    }

    @Test
    fun restAfterMoreThanFiveRunDays() {
        val today = LocalDate.of(2026, 10, 20)
        fun runsFor(days: Int) = (0 until days).map { Run(today.minusDays(it.toLong()), 3.0, 1260) }
        assertEquals(5, runStreak(runsFor(5), emptySet(), today))
        assertFalse(AlertKind.REST_NEEDED in kinds(evaluateAlerts(emptyList(), runsFor(5), emptySet(), today)))
        assertTrue(AlertKind.REST_NEEDED in kinds(evaluateAlerts(emptyList(), runsFor(6), emptySet(), today)))
        // No run yet today: the streak ending yesterday still counts.
        assertEquals(6, runStreak(runsFor(7).drop(1), emptySet(), today))
        // A marked rest day breaks the streak.
        assertEquals(2, runStreak(runsFor(6), setOf(today.minusDays(2)), today))
    }

    @Test
    fun reminderWhenNotLogged() {
        val today = LocalDate.of(2026, 10, 20)
        val data = Synthetic.series(days = 10)
        val last = data.last().date
        assertFalse(AlertKind.LOG_REMINDER in kinds(evaluateAlerts(data, emptyList(), emptySet(), last.plusDays(2))))
        assertTrue(AlertKind.LOG_REMINDER in kinds(evaluateAlerts(data, emptyList(), emptySet(), last.plusDays(3))))
        assertTrue(AlertKind.LOG_REMINDER in kinds(evaluateAlerts(emptyList(), emptyList(), emptySet(), today)))
    }
}
