package wt.core

import wt.core.io.BodyReading
import wt.core.io.OcrLine
import wt.core.io.RunReading
import wt.core.io.ocrRows
import wt.core.io.readScreenshot
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Text as on-device recognition returns it for the owner's real screenshots (2026-10-08). */
class ScreenshotTest {
    // Strava share image: each value under its label, with the route line drawn across some of them.
    private val stravaShare = listOf(
        "STRAVA", "Distance", "3.45 km", "Pace", "7:02 /km", "Time", "24m 19s",
        "Elev Gain", "0 m", "Cal", "377 Cal", "Max Elev", "3 m",
    )

    // Japanese body-scale app ("測定データ"), label on the left, value on the right.
    private val bodyScale = listOf(
        "19:23", "測定データ", "< 2026/10/08 >", "体重 血圧", "最新 週 月 年",
        "BMI 30.6", "体脂肪率 29.2 %", "体年齢 46 歳", "基礎代謝量 1818.0 kcal", "皮下脂肪率 20.8 %",
        "内臓脂肪 16.0", "除脂肪体重 62.5 kg", "筋肉量 60.1 kg", "筋肉率 68.1 %", "推定骨量 2.4 kg",
        "骨格筋率 37.0 %", "体水分率 51.9 %", "タンパク質 16.2 %", "測定データ カレンダー 設定 その他",
    )

    @Test
    fun stravaShareImage() {
        val run = assertIs<RunReading>(readScreenshot(stravaShare))
        assertEquals(3.45, run.km)
        assertEquals(24 * 60 + 19, run.durationSec)
        assertEquals(422, run.paceSecPerKm)
        assertEquals(377.0, run.kcal)
        assertTrue(run.notes.isEmpty(), run.notes.toString())
    }

    @Test
    fun stravaTimeHiddenByRouteIsWorkedOutFromPace() {
        val run = assertIs<RunReading>(readScreenshot(stravaShare.map { if (it == "24m 19s") "24m 1" else it }))
        assertEquals(3.45, run.km)
        assertEquals(Math.round(3.45 * 422).toInt(), run.durationSec)
        assertTrue(run.notes.any { "pace" in it })
    }

    @Test
    fun elevationIsNotMistakenForTime() {
        val run = assertIs<RunReading>(readScreenshot(listOf("STRAVA", "Distance", "3.45 km", "Elev Gain", "0 m", "Max Elev", "3 m")))
        assertNull(run.durationSec)
        assertTrue(run.notes.contains("Time not found."))
    }

    @Test
    fun stravaActivityScreenWithClockTimeAndLongRun() {
        val run = assertIs<RunReading>(readScreenshot(listOf("Distance", "12.03 km", "Moving Time", "1:15:40", "Pace", "6:17 /km", "Calories", "1,024")))
        assertEquals(12.03, run.km)
        assertEquals(3600 + 15 * 60 + 40, run.durationSec)
        assertEquals(1024.0, run.kcal)
    }

    @Test
    fun mismatchIsFlagged() {
        val run = assertIs<RunReading>(readScreenshot(stravaShare.map { if (it == "3.45 km") "8.45 km" else it }))
        assertTrue(run.notes.any { "don't agree" in it })
    }

    @Test
    fun bodyScaleScreen() {
        val body = assertIs<BodyReading>(readScreenshot(bodyScale, heightCm = 170.0))
        assertEquals(LocalDate.of(2026, 10, 8), body.date)
        assertEquals(29.2, body.fatPct)
        assertEquals(16.0, body.visceral)
        assertEquals(60.1, body.muscleKg) // not 筋肉率 68.1
        assertEquals(37.0, body.skeletalPct)
        assertEquals(62.5, body.leanKg)
        assertEquals(1818.0, body.bmrKcal)
        // No weight row: 62.5 / (1 − 0.292) = 88.28 → 88.3, matching the logged 88.3 kg.
        assertEquals(88.3, body.weightKg)
        assertTrue(body.weightEstimated)
    }

    @Test
    fun weightRowWinsOverEstimate() {
        val body = assertIs<BodyReading>(readScreenshot(bodyScale + "体重 88.4 kg"))
        assertEquals(88.4, body.weightKg)
        assertFalse(body.weightEstimated)
    }

    @Test
    fun valueOnTheNextRowIsUsed() {
        val body = assertIs<BodyReading>(readScreenshot(listOf("体脂肪率", "29.2 %", "内臓脂肪", "16.0")))
        assertEquals(29.2, body.fatPct)
        assertEquals(16.0, body.visceral)
        assertNull(body.weightKg)
    }

    @Test
    fun unrelatedTextIsNotRecognised() {
        assertNull(readScreenshot(listOf("Hello", "Meeting at 10:30", "Total 3 m")))
    }

    @Test
    fun linesAreGroupedIntoRowsLeftToRight() {
        val rows = ocrRows(
            listOf(
                OcrLine("29.2 %", 760, 552, 870, 596),
                OcrLine("体脂肪率", 34, 550, 165, 598),
                OcrLine("BMI", 34, 442, 96, 486),
                OcrLine("30.6", 800, 444, 866, 484),
                OcrLine("16.0", 800, 994, 866, 1032),
                OcrLine("内臓脂肪", 34, 990, 165, 1036),
            ),
        )
        assertEquals(listOf("BMI 30.6", "体脂肪率 29.2 %", "内臓脂肪 16.0"), rows)
    }
}
