package wt.app

import wt.app.chart.kgStep
import wt.app.notify.Reminder
import wt.app.ui.downText
import wt.app.ui.duration
import wt.app.ui.toGoText
import wt.app.ui.fieldText
import wt.app.ui.parseDecimal
import wt.app.ui.parseDuration
import wt.app.ui.planLabel
import wt.app.ui.reminderTitle
import wt.app.ui.weekdayName
import wt.app.ui.ageText
import wt.app.ui.signed
import wt.app.ui.roughKcal
import wt.app.ui.weekRange
import wt.core.model.TOKYO
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {
    @Test
    fun downAndToGo() {
        assertEquals("3.1 kg down", downText(3.1))
        assertEquals("0.3 kg up", downText(-0.3))
        assertEquals("0.0 kg down", downText(-0.02))
        assertEquals("3.2 kg to go", toGoText(3.2))
        assertEquals("Goal reached", toGoText(0.0))
        assertEquals("Goal reached", toGoText(0.04))
    }

    @Test
    fun reminderTitles() {
        assertEquals("Reminder at 07:30", reminderTitle(7, 30, enabled = true))
        assertEquals("Reminder at 21:05", reminderTitle(21, 5, enabled = true))
        assertEquals("Reminder", reminderTitle(7, 30, enabled = false))
    }

    @Test
    fun weekdayNames() {
        assertEquals("Monday", weekdayName(1))
        assertEquals("Sunday", weekdayName(7))
    }

    @Test
    fun roughKcalRoundsToFifty() {
        assertEquals("2,350", roughKcal(2358.0))
        assertEquals("2,100", roughKcal(2076.0))
        assertEquals("1,800", roughKcal(1800.0))
    }

    @Test
    fun weekRanges() {
        assertEquals("12 – 18 Oct", weekRange(java.time.LocalDate.of(2026, 10, 12)))
        assertEquals("28 Sep – 4 Oct", weekRange(java.time.LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun durations() {
        assertEquals(20 * 60 + 16, parseDuration("20:16"))
        assertEquals(3930, parseDuration("1:05:30"))
        assertEquals(21 * 60, parseDuration("21"))
        assertNull(parseDuration("20:75"))
        assertNull(parseDuration("abc"))
        assertNull(parseDuration("0"))
        assertEquals("20:16", duration(1216))
        assertEquals("1:05:30", duration(3930))
    }

    @Test
    fun decimals() {
        assertEquals(88.3, parseDecimal("88,3"))
        assertEquals(88.3, parseDecimal(" 88.3 "))
        assertNull(parseDecimal(""))
        assertEquals("88", fieldText(88.0))
        assertEquals("88.3", fieldText(88.3))
        assertEquals("", fieldText(null))
    }

    @Test
    fun reminderDelay() {
        val morning = ZonedDateTime.of(2026, 10, 8, 6, 0, 0, 0, TOKYO)
        assertEquals(Duration.ofMinutes(90), Reminder.delayUntil(morning, 7, 30))
        val afterwards = morning.withHour(8)
        assertEquals(Duration.ofHours(23).plusMinutes(30), Reminder.delayUntil(afterwards, 7, 30))
        assertEquals(Duration.ofDays(1), Reminder.delayUntil(morning.withHour(7).withMinute(30), 7, 30))
    }

    @Test
    fun chartStepKeepsKgLabelsApart() {
        assertEquals(1.0, kgStep(8.0))
        assertEquals(2.0, kgStep(11.0))
        assertEquals(10.0, kgStep(50.0))
    }

    @Test
    fun planLabelsUseShortDates() {
        assertEquals("Re-baseline 17 Oct", planLabel("Re-baseline 2026-10-17"))
        assertEquals("Original plan", planLabel("Original plan"))
    }

    @Test
    fun signedUsesARealMinus() {
        assertEquals("−0.6", signed(-0.6))
        assertEquals("+0.3", signed(0.3))
        assertEquals("0.0", signed(0.0))
        assertEquals("0.0", signed(-0.04)) // rounds to zero: no sign
    }

    @Test
    fun ageText() {
        val now = java.time.ZonedDateTime.parse("2026-11-05T10:00+09:00")
        assertEquals("just now", ageText("2026-11-05T10:00+09:00", now))
        assertEquals("5 min ago", ageText("2026-11-05T09:55+09:00", now))
        assertEquals("2 h ago", ageText("2026-11-05T07:50+09:00", now))
        assertEquals("3 days ago", ageText("2026-11-02T10:00+09:00", now))
    }
}
