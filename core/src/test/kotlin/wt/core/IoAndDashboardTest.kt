package wt.core

import wt.core.alerts.AlertKind
import wt.core.dashboard.buildDashboard
import wt.core.io.bodyCsv
import wt.core.io.csvField
import wt.core.io.decodeBackup
import wt.core.io.encodeBackup
import wt.core.io.runsCsv
import wt.core.io.weightsCsv
import wt.core.model.AppData
import wt.core.model.Defaults
import wt.core.model.PlanRecord
import wt.core.model.Run
import wt.core.model.WeightEntry
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IoAndDashboardTest {
    private val sample = AppData(
        profile = Defaults.profile,
        weights = listOf(
            WeightEntry(Defaults.START, 88.3, sleepHours = 6.5, hunger = 3, snacks = "chips, \"big\" bag", note = "line1\nline2"),
            WeightEntry(Defaults.START.plusDays(1), 88.0),
        ),
        body = listOf(Defaults.startBody),
        runs = Defaults.sampleRuns + Run(Defaults.START, 3.0, 1260, kcal = 250.0, source = "strava", externalId = "123"),
        restDays = setOf(Defaults.START.plusDays(1)),
        plans = listOf(
            PlanRecord(1, "Original plan", Defaults.START, false, Defaults.planCheckpoints),
            PlanRecord(2, "Re-baseline", Defaults.START.plusDays(1), true, Defaults.planCheckpoints.drop(1)),
        ),
    )

    @Test
    fun backupRoundTrip() {
        val text = encodeBackup(sample, "2026-10-09T08:00+09:00")
        assertEquals(sample, decodeBackup(text))
        assertTrue(text.contains("\"format\": \"wt-tracker-backup\""))
    }

    @Test
    fun backupRejectsOtherFiles() {
        assertFailsWith<IllegalArgumentException> { decodeBackup("{}") }
        assertFailsWith<IllegalArgumentException> { decodeBackup("not json") }
        val wrong = encodeBackup(sample, "x").replace("wt-tracker-backup", "something-else")
        assertFailsWith<IllegalArgumentException> { decodeBackup(wrong) }
        val newer = encodeBackup(sample, "x").replace("\"version\": 1", "\"version\": 99")
        assertFailsWith<IllegalArgumentException> { decodeBackup(newer) }
    }

    @Test
    fun csvEscaping() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
        assertEquals("", csvField(null))
    }

    @Test
    fun csvExports() {
        val w = weightsCsv(sample.weights, sample.restDays).lines()
        assertEquals("date,weight_kg,sleep_h,hunger_1_5,snacks,note,rest_day", w[0])
        assertTrue(w[1].startsWith("2026-10-08,88.3,6.5,3,\"chips, \"\"big\"\" bag\",\"line1"))
        assertTrue(w.any { it == "2026-10-09,88.0,,,,,yes" })

        val r = runsCsv(sample.runs).lines()
        assertEquals("2026-10-08,3.0,21:00,1260,7:00,250.0,strava", r[1])
        assertEquals("2.81,20:16,1216,7:13", r[2].split(',').drop(1).take(4).joinToString(","))
        assertTrue(r[2].startsWith(",")) // undated seed runs sort last with an empty date

        assertEquals("2026-10-08,29.2,16.0,60.1,37.0,62.5,1818.0", bodyCsv(sample.body).lines()[1])
    }

    @Test
    fun dashboardWithOnlyStartWeight() {
        val d = buildDashboard(
            listOf(Defaults.startWeight), Defaults.sampleRuns, emptySet(), listOf(Defaults.startBody),
            Defaults.planCheckpoints, Defaults.profile, Defaults.START,
        )
        assertNull(d.fit)
        assertNull(d.forecast)
        assertNull(d.energy)
        assertEquals(88.3, d.latestWeight!!.kg, 1e-9)
        assertEquals(1, d.weekly.size)
        assertTrue(d.alerts.none { it.kind == AlertKind.LOG_REMINDER })
        assertEquals(6.3 / 91 * 7, assertNotNull(d.rebaselinePreview()).requiredKgPerWeek!!, 1e-9)
    }

    @Test
    fun dashboardWithAMonthOfData() {
        val weights = Synthetic.series(days = 30, kgPerWeek = 0.5, noiseSd = 0.3, seed = 31)
        val asOf = weights.last().date
        val runs = (0 until 30 step 2).map { Run(Defaults.START.plusDays(it.toLong()), 3.0, 1260) }
        val d = buildDashboard(weights, runs, emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, asOf)
        val f = assertNotNull(d.forecast)
        assertEquals(Defaults.GOAL_DATE, f.goalDate)
        assertNotNull(d.energy)
        assertEquals(5, d.weekly.size)
        assertEquals(5, d.weekly.first().index) // newest week first
        assertEquals(weights.size, d.movingAverage.size)
    }

    @Test
    fun dashboardIgnoresFutureWeights() {
        val weights = listOf(WeightEntry(Defaults.START, 88.3), WeightEntry(LocalDate.of(2030, 1, 1), 70.0))
        val d = buildDashboard(weights, emptyList(), emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, Defaults.START)
        assertEquals(1, d.weights.size)
    }

    @Test
    fun todayVsPlanUsesTheTrendGap() {
        val weights = Synthetic.series(30)
        val asOf = weights.last().date
        val d = buildDashboard(weights, emptyList(), emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, asOf)
        val p = assertNotNull(d.todayVsPlan())
        assertTrue(p.fromTrend)
        assertEquals(d.forecast!!.trendToday, p.kg, 1e-9)
        assertEquals(d.forecast!!.gapKgToday, p.gapKg, 1e-9)
    }

    @Test
    fun todayVsPlanBeforeATrendAndBeforeAnyWeighIn() {
        val one = buildDashboard(listOf(WeightEntry(Defaults.START, 88.0)), emptyList(), emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, Defaults.START)
        val p = assertNotNull(one.todayVsPlan())
        assertEquals(88.0, p.kg)
        assertEquals(88.0 - Defaults.START_KG, p.gapKg, 1e-9)
        val none = buildDashboard(emptyList(), emptyList(), emptySet(), emptyList(), Defaults.planCheckpoints, Defaults.profile, Defaults.START)
        assertNull(none.todayVsPlan())
    }
}
