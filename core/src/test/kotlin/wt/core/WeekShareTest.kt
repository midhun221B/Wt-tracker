package wt.core

import wt.core.dashboard.Dashboard
import wt.core.dashboard.buildDashboard
import wt.core.model.Checkpoint
import wt.core.model.Defaults
import wt.core.model.Run
import wt.core.model.WeightEntry
import wt.core.summary.DayKind
import wt.core.summary.weekShare
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeekShareTest {
    private fun d(day: Int, month: Int = 10, year: Int = 2026) = LocalDate.of(year, month, day)

    // Weekly weigh-ins from Thursday 8 Oct; last week (26 Oct–1 Nov) has five 3 km runs, a rest day and a missed Sunday.
    private val weights = listOf(
        WeightEntry(d(8), 88.6), WeightEntry(d(12), 88.4), WeightEntry(d(19), 87.6),
        WeightEntry(d(26), 87.9), WeightEntry(d(2, 11), 87.5),
    )
    private val runs = listOf(26, 27, 29, 30, 31).map { Run(d(it), 3.0, 1200) }

    private fun dashboard(
        w: List<WeightEntry> = weights,
        r: List<Run> = runs,
        asOf: LocalDate = d(2, 11),
        checkpoints: List<Checkpoint> = Defaults.planCheckpoints,
    ): Dashboard = buildDashboard(w, r, setOf(d(28)), emptyList(), checkpoints, Defaults.profile, asOf)

    @Test
    fun weekFiveCard() {
        val dash = dashboard()
        val s = assertNotNull(weekShare(dash, d(2, 11)))
        assertEquals(5, s.week)
        assertEquals(14, s.totalWeeks)
        assertEquals(9, s.weeksToGo)
        assertEquals(87.5, s.kg, 1e-9)
        assertEquals(-0.4, s.changeKg!!, 1e-9)
        assertEquals(d(26), s.previousDate)
        assertEquals(dash.plan.at(d(2, 11)), s.planMarkerKg!!, 1e-9)
        assertTrue(s.stripIsLastWeek)
        assertEquals(d(26), s.strip.first().date)
        assertEquals(
            listOf(DayKind.Run, DayKind.Run, DayKind.Rest, DayKind.Run, DayKind.Run, DayKind.Run, DayKind.Missed),
            s.strip.map { it.kind },
        )
        assertEquals(5, s.runs)
        assertEquals(15.0, s.km, 1e-9)
        assertEquals("Week 5 weigh-in: 87.5 kg (−0.4). Last week 5 runs, 15.0 km.", s.caption)
        assertEquals(d(2, 11), s.currentPost.date)
        assertEquals(14, s.posts.size)
        assertEquals(dash.plan.checkpoints, s.trail)
    }

    @Test
    fun firstWeighIn() {
        val s = assertNotNull(weekShare(dashboard(listOf(WeightEntry(d(8), 88.6)), emptyList(), d(8)), d(8)))
        assertNull(s.changeKg)
        assertNull(s.previousDate)
        assertNull(s.planMarkerKg)
        assertFalse(s.stripIsLastWeek)
        assertEquals(d(5), s.strip.first().date)
        assertEquals(
            List(3) { DayKind.BeforeStart } + DayKind.Open + List(3) { DayKind.Future },
            s.strip.map { it.kind },
        )
        assertEquals("Week 1 weigh-in: 88.6 kg. This week no runs.", s.caption)
    }

    @Test
    fun beforeTheTrendWait() {
        val s = assertNotNull(weekShare(dashboard(weights.take(2), emptyList(), d(12)), d(12)))
        assertNull(s.planMarkerKg)
        assertEquals(-0.2, s.changeKg!!, 1e-9)
        assertTrue(s.stripIsLastWeek)
    }

    @Test
    fun oneRunCaption() {
        val s = assertNotNull(weekShare(dashboard(r = listOf(Run(d(27), 4.2, 1500))), d(2, 11)))
        assertTrue(s.caption.endsWith("Last week 1 run, 4.2 km."), s.caption)
    }

    @Test
    fun olderWeighInUsesThatDay() {
        val s = assertNotNull(weekShare(dashboard(), d(26)))
        assertEquals(87.9, s.kg, 1e-9)
        assertEquals(0.3, s.changeKg!!, 1e-9)
        assertEquals(d(19), s.previousDate)
        assertEquals(4, s.week)
        assertEquals(d(19), s.strip.first().date)
        assertTrue(s.caption.contains("(+0.3)"), s.caption)
    }

    @Test
    fun rangeIncludesWeightsAboveStart() {
        val w = weights.map { if (it.date == d(19)) WeightEntry(d(19), 89.4) else it }
        val s = assertNotNull(weekShare(dashboard(w), d(2, 11)))
        assertTrue(s.kgRange.endInclusive >= 89.4)
        assertTrue(s.kgRange.start <= 82.0)
    }

    @Test
    fun rangeIncludesTheSharedWeighInAfterARebaseline() {
        // Re-baselined on 2 Nov (start 86.0), then the 19 Oct weigh-in (87.6) is shared: it predates the plan start.
        val points = listOf(Checkpoint(d(2, 11), 86.0), Checkpoint(d(7, 1, 2027), 82.0))
        val s = assertNotNull(weekShare(dashboard(checkpoints = points), d(19)))
        assertTrue(87.6 in s.kgRange, "${s.kgRange}")
    }

    @Test
    fun afterGoalDateHasNoWeeksToGo() {
        val day = d(11, 1, 2027)
        val s = assertNotNull(weekShare(dashboard(weights + WeightEntry(day, 84.0), asOf = day), day))
        assertEquals(0, s.weeksToGo)
    }

    @Test
    fun captionIgnoresDecimalCommaLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val s = assertNotNull(weekShare(dashboard(), d(2, 11)))
            assertTrue(s.caption.contains("87.5") && s.caption.contains("15.0"), s.caption)
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun extraWeighInComparesWithThePreviousOne() {
        val s = assertNotNull(weekShare(dashboard(weights + WeightEntry(d(30), 87.7)), d(2, 11)))
        assertEquals(-0.2, s.changeKg!!, 1e-9)
        assertEquals(d(30), s.previousDate)
    }

    @Test
    fun noWeightThatDay() {
        assertNull(weekShare(dashboard(), d(1, 11)))
    }

    @Test
    fun rebaselinedPlanTrail() {
        val points = listOf(Checkpoint(d(8), 88.3), Checkpoint(d(26), 87.0), Checkpoint(d(7, 1, 2027), 82.0))
        val s = assertNotNull(weekShare(dashboard(checkpoints = points), d(2, 11)))
        assertEquals(points, s.trail)
        assertEquals(5, s.week)
    }
}
