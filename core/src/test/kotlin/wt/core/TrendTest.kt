package wt.core

import wt.core.model.WeightEntry
import wt.core.trend.fitTrend
import wt.core.trend.movingAverage
import wt.core.trend.t80
import wt.core.trend.theilSen
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendTest {
    private val d0 = Synthetic.START

    @Test
    fun movingAverageUsesTrailingSevenDays() {
        val entries = (0 until 10).map { WeightEntry(d0.plusDays(it.toLong()), 80.0 + it) }
        val ma = movingAverage(entries)
        assertEquals(80.0, ma[0].value, 1e-9)
        assertEquals(83.0, ma[6].value, 1e-9) // mean of 80..86
        assertEquals(86.0, ma[9].value, 1e-9) // mean of 83..89
    }

    @Test
    fun movingAverageHandlesGaps() {
        val entries = listOf(0, 1, 5, 8).map { WeightEntry(d0.plusDays(it.toLong()), 80.0 + it) }
        val ma = movingAverage(entries)
        assertEquals((80.0 + 81 + 85) / 3, ma[2].value, 1e-9)
        assertEquals((85.0 + 88) / 2, ma[3].value, 1e-9) // day 8 window is days 2..8
    }

    @Test
    fun theilSenFitsExactLine() {
        val xs = doubleArrayOf(0.0, 1.0, 2.0, 3.0)
        val (slope, intercept) = theilSen(xs, DoubleArray(4) { 5 - 0.5 * it })
        assertEquals(-0.5, slope, 1e-12)
        assertEquals(5.0, intercept, 1e-12)
    }

    @Test
    fun recoversSlopeFromNoisyData() {
        for (seed in 1L..20L) {
            val data = Synthetic.series(days = 28, kgPerWeek = 0.5, noiseSd = 0.4, seed = seed)
            val fit = assertNotNull(fitTrend(data, data.last().date))
            assertFalse(fit.lowConfidence)
            assertEquals(-0.5, fit.kgPerWeek, 0.3, "seed $seed")
            assertEquals(Synthetic.trueKg(27, 88.3, 0.5), fit.valueAt(data.last().date), 0.35, "seed $seed")
        }
    }

    @Test
    fun robustToWaterWeightSpikes() {
        // Three +2 kg spikes in the last week would drag an ordinary least-squares fit badly.
        val data = Synthetic.series(days = 21, kgPerWeek = 0.5, noiseSd = 0.2, seed = 7, outlierDays = setOf(15, 18, 20))
        val fit = assertNotNull(fitTrend(data, data.last().date))
        assertEquals(-0.5, fit.kgPerWeek, 0.25)
        assertEquals(Synthetic.trueKg(20, 88.3, 0.5), fit.valueAt(data.last().date), 0.4)
    }

    @Test
    fun handlesMissingDays() {
        val data = Synthetic.series(days = 35, kgPerWeek = 0.6, noiseSd = 0.3, seed = 3, logProbability = 0.5)
        val fit = assertNotNull(fitTrend(data, data.last().date))
        assertEquals(-0.6, fit.kgPerWeek, 0.3)
    }

    @Test
    fun fewPointsAreLowConfidenceOrNull() {
        val data = Synthetic.series(days = 5, seed = 2)
        val fit = assertNotNull(fitTrend(data, data.last().date))
        assertTrue(fit.lowConfidence)
        assertNull(fitTrend(data.take(2), data[1].date))
    }

    @Test
    fun sparseRecentWindowFallsBackToAllData() {
        // 30 daily logs, then only 2 in the last 21 days.
        val early = Synthetic.series(days = 30, seed = 4)
        val late = listOf(early.last().copy(date = early.last().date.plusDays(20)))
        val asOf = late.last().date
        val fit = assertNotNull(fitTrend(early + late, asOf))
        assertTrue(fit.lowConfidence)
        assertEquals(early.first().date, fit.windowStart)
    }

    @Test
    fun bandCoversTruthAtRoughlyEightyPercent() {
        val horizon = 60L
        var covered = 0
        val runs = 300
        for (seed in 1..runs) {
            val data = Synthetic.series(days = 21, kgPerWeek = 0.5, noiseSd = 0.4, seed = seed.toLong())
            val asOf = data.last().date
            val fit = fitTrend(data, asOf)!!
            val (lo, hi) = fit.bandAt(asOf.plusDays(horizon))
            val truth = Synthetic.trueKg(20 + horizon.toInt(), 88.3, 0.5)
            if (truth in lo..hi) covered++
        }
        val rate = covered.toDouble() / runs
        println("Band coverage at +$horizon days: %.1f %% (target ≈ 80 %%)".format(rate * 100))
        assertTrue(rate in 0.65..0.95, "coverage $rate")
    }

    @Test
    fun bandWidensWithHorizon() {
        val data = Synthetic.series(days = 21, seed = 5)
        val fit = fitTrend(data, data.last().date)!!
        val near = fit.bandAt(data.last().date).let { it.second - it.first }
        val far = fit.bandAt(data.last().date.plusDays(90)).let { it.second - it.first }
        assertTrue(far > 3 * near)
    }

    @Test
    fun sigmaHasAFloorForPerfectData() {
        val data = Synthetic.series(days = 14, noiseSd = 0.0)
        val fit = fitTrend(data, data.last().date)!!
        assertEquals(0.1, fit.sigma, 1e-12)
        assertTrue(abs(fit.kgPerWeek + 0.5) < 1e-9)
    }

    @Test
    fun tQuantiles() {
        assertEquals(1.476, t80(5), 1e-9)
        assertEquals(1.356, t80(11), 1e-9) // rounds up to the next table entry (conservative)
        assertEquals(1.2816, t80(100), 1e-9)
    }
}
