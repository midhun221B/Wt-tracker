package wt.app

import wt.app.notify.Reminder
import wt.app.ui.duration
import wt.app.ui.fieldText
import wt.app.ui.parseDecimal
import wt.app.ui.parseDuration
import wt.core.model.TOKYO
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {
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
}
