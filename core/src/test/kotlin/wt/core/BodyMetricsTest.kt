package wt.core

import wt.core.model.BodyComp
import wt.core.model.WeightEntry
import wt.core.summary.BodyMetric
import wt.core.summary.bodyMetrics
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyMetricsTest {
    private fun d(day: Int, month: Int = 10) = LocalDate.of(2026, month, day)

    private val weights = listOf(
        WeightEntry(d(8), 88.6), WeightEntry(d(12), 88.4), WeightEntry(d(14), 88.0), WeightEntry(d(19), 87.6),
        WeightEntry(d(26), 87.9), WeightEntry(d(2, 11), 87.5),
    )
    private val body = listOf(
        BodyComp(d(22), 28.8, 15.5, 60.0), BodyComp(d(8), 29.2, 16.0, 60.1), BodyComp(d(5, 11), 28.3, 15.0, 59.9),
    )
    private val all = bodyMetrics(weights, body, heightCm = 170.0)
    private fun series(m: BodyMetric) = all.first { it.metric == m }

    @Test
    fun weightIsOnePointPerWeekTheWeeksLast() {
        val w = series(BodyMetric.Weight)
        // 12 and 14 Oct share a Monday–Sunday week: 14 Oct (88.0) stands for it.
        assertEquals(listOf(d(8), d(14), d(19), d(26), d(2, 11)), w.points.map { it.first })
        assertEquals(88.6, w.first, 1e-9)
        assertEquals(87.5, w.last, 1e-9)
        assertEquals(87.5, w.min, 1e-9)
        assertEquals(88.6, w.max, 1e-9)
        assertEquals((88.6 + 88.0 + 87.6 + 87.9 + 87.5) / 5, w.avg, 1e-9)
        assertEquals(-1.1, w.change, 1e-9)
        assertEquals(true, w.improving)
    }

    @Test
    fun bmiFollowsTheWeeklyWeightAndHeight() {
        val bmi = series(BodyMetric.Bmi)
        assertEquals(5, bmi.points.size)
        assertEquals(87.5 / (1.7 * 1.7), bmi.last, 1e-9)
    }

    @Test
    fun scaleMeasuresAreSortedByDate() {
        val fat = series(BodyMetric.BodyFat)
        assertEquals(listOf(d(8), d(22), d(5, 11)), fat.points.map { it.first })
        assertEquals(-0.9, fat.change, 1e-9)
        assertEquals(15.0, series(BodyMetric.Visceral).last, 1e-9)
    }

    @Test
    fun losingMuscleIsNotImproving() {
        val muscle = series(BodyMetric.Muscle)
        assertEquals(-0.2, muscle.change, 1e-9)
        assertEquals(false, muscle.improving)
    }

    @Test
    fun orderIsWeightBmiFatVisceralMuscle() {
        assertEquals(BodyMetric.entries.toList(), all.map { it.metric })
    }

    @Test
    fun measuresWithoutDataAreLeftOut() {
        val onlyWeight = bodyMetrics(weights.take(1), emptyList(), heightCm = 170.0)
        assertEquals(listOf(BodyMetric.Weight, BodyMetric.Bmi), onlyWeight.map { it.metric })
        assertNull(onlyWeight.first().improving) // one point: no direction yet
        assertTrue(bodyMetrics(emptyList(), emptyList(), 170.0).isEmpty())
    }

    @Test
    fun noBmiWithoutAHeight() {
        assertTrue(bodyMetrics(weights, body, heightCm = 0.0).none { it.metric == BodyMetric.Bmi })
    }
}

class ChartScaleTest {
    @Test
    fun smallChangesKeepAMinimumRange() {
        // 1.1 kg of weight change must not fill the whole chart: the range is at least 3 kg, centred on the data.
        val r = wt.core.summary.chartRange(87.5, 88.6, BodyMetric.Weight)
        assertEquals(3.0, r.endInclusive - r.start, 1e-9)
        assertEquals((87.5 + 88.6) / 2, (r.start + r.endInclusive) / 2, 1e-9)
    }

    @Test
    fun largeChangesUseTheirOwnRange() {
        val r = wt.core.summary.chartRange(82.0, 88.6, BodyMetric.Weight)
        assertEquals(82.0, r.start, 1e-9)
        assertEquals(88.6, r.endInclusive, 1e-9)
    }

    @Test
    fun everyMeasureHasAMinimumRange() {
        BodyMetric.entries.forEach { assertTrue(it.minChartSpan > 0, it.name) }
    }

    @Test
    fun slopesAreFlatAtTurningPoints() {
        // 88.6, 88.0, 87.6, 87.9, 87.5: 87.6 is a low point, so the curve must not dip below it.
        val s = wt.core.summary.monotoneSlopes(listOf(88.6, 88.0, 87.6, 87.9, 87.5))
        assertEquals(0.0, s[2], 1e-9)
        assertEquals(0.0, s[3], 1e-9)
    }

    @Test
    fun slopesFollowASteadyFall() {
        val s = wt.core.summary.monotoneSlopes(listOf(3.0, 2.0, 1.0))
        assertEquals(listOf(-1.0, -1.0, -1.0), s)
    }

    @Test
    fun oneOrTwoPointsAreStraight() {
        assertEquals(listOf(0.0), wt.core.summary.monotoneSlopes(listOf(5.0)))
        assertEquals(listOf(-2.0, -2.0), wt.core.summary.monotoneSlopes(listOf(5.0, 3.0)))
    }
}
