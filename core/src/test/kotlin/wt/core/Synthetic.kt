package wt.core

import wt.core.model.WeightEntry
import java.time.LocalDate
import java.util.Random

/** Synthetic daily weights: a true linear trend plus Gaussian noise, with optional gaps and outliers. */
object Synthetic {
    val START: LocalDate = LocalDate.of(2026, 10, 8)

    fun trueKg(day: Int, startKg: Double, kgPerWeek: Double) = startKg - kgPerWeek / 7 * day

    fun series(
        days: Int,
        startKg: Double = 88.3,
        kgPerWeek: Double = 0.5,
        noiseSd: Double = 0.4,
        seed: Long = 1,
        logProbability: Double = 1.0,
        outlierDays: Set<Int> = emptySet(),
        outlierKg: Double = 2.0,
        start: LocalDate = START,
    ): List<WeightEntry> {
        val rnd = Random(seed)
        return (0 until days).mapNotNull { d ->
            val noise = rnd.nextGaussian() * noiseSd
            val logged = rnd.nextDouble() < logProbability || d == days - 1
            if (!logged) return@mapNotNull null
            val spike = if (d in outlierDays) outlierKg else 0.0
            WeightEntry(start.plusDays(d.toLong()), trueKg(d, startKg, kgPerWeek) + noise + spike)
        }
    }
}
