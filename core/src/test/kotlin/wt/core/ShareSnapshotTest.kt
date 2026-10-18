package wt.core

import wt.core.io.ShareSnapshot
import wt.core.io.decodeShare
import wt.core.io.driveDownloadUrl
import wt.core.io.driveFileId
import wt.core.io.driveLink
import wt.core.io.encodeBackup
import wt.core.io.encodeShare
import wt.core.model.AppData
import wt.core.model.Defaults
import wt.core.model.PlanRecord
import wt.core.model.Run
import wt.core.model.WeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareSnapshotTest {
    private val sample = AppData(
        profile = Defaults.profile,
        weights = listOf(WeightEntry(Defaults.START, 88.3), WeightEntry(Defaults.START.plusDays(7), 87.9)),
        body = listOf(Defaults.startBody),
        runs = listOf(Run(Defaults.START, 3.0, 1260, kcal = 250.0, source = "strava", externalId = "123")),
        restDays = setOf(Defaults.START.plusDays(1)),
        plans = listOf(
            PlanRecord(1, "Original plan", Defaults.START, false, Defaults.planCheckpoints),
            PlanRecord(2, "Re-baseline", Defaults.START.plusDays(1), true, Defaults.planCheckpoints.drop(1)),
        ),
    )
    private val snapshot = ShareSnapshot("Midhun", "2026-10-18T09:00+09:00", sample)
    private val id = "1AbCdEfGhIjKlMnOpQrStUvWxYz012345"

    @Test
    fun roundTrip() {
        assertEquals(snapshot, decodeShare(encodeShare(snapshot)))
    }

    @Test
    fun rejectsOtherFiles() {
        assertFailsWith<IllegalArgumentException> { decodeShare(encodeBackup(sample, "x")) }
        assertFailsWith<IllegalArgumentException> { decodeShare("{}") }
        assertFailsWith<IllegalArgumentException> { decodeShare("not json") }
    }

    @Test
    fun newerVersionAsksToUpdate() {
        val newer = encodeShare(snapshot).replaceFirst("\"version\": 1", "\"version\": 2")
        val e = assertFailsWith<IllegalArgumentException> { decodeShare(newer) }
        assertTrue(e.message!!.contains("Update Downhill"), e.message)
    }

    @Test
    fun unusablePlanIsRefused() {
        val broken = sample.copy(plans = listOf(PlanRecord(1, "Original plan", Defaults.START, true, Defaults.planCheckpoints.take(1))))
        assertFailsWith<IllegalArgumentException> { decodeShare(encodeShare(snapshot.copy(data = broken))) }
    }

    @Test
    fun fileIdFromLinks() {
        listOf(
            "https://drive.google.com/file/d/$id/view?usp=sharing",
            "https://drive.google.com/open?id=$id",
            "https://drive.google.com/uc?id=$id&export=download",
            "Open this in Wt-tracker to follow my progress: https://drive.google.com/file/d/$id/view?usp=sharing",
            id,
            "  $id\n",
        ).forEach { assertEquals(id, driveFileId(it), it) }
    }

    @Test
    fun notALink() {
        listOf("https://example.com/file/d/$id", "hello", "", "abcdefghij").forEach { assertNull(driveFileId(it), it) }
    }

    @Test
    fun linksFromId() {
        assertEquals("https://drive.google.com/file/d/ID/view?usp=sharing", driveLink("ID"))
        assertEquals("https://drive.google.com/uc?export=download&id=ID", driveDownloadUrl("ID"))
    }

    @Test
    fun fixtureDecodes() {
        val text = javaClass.getResource("/share-sample.json")!!.readText()
        val s = decodeShare(text)
        assertEquals("Midhun", s.ownerName)
        assertTrue(s.data.weights.isNotEmpty())
    }
}
