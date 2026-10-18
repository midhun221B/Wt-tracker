package wt.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wt.app.share.Mode
import wt.app.share.SharePrefs
import wt.app.share.ShareScheduler
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun uploadWaitsAMinuteForAConnection() {
        // Several quick edits replace one pending upload a minute out, instead of uploading each.
        val r = ShareScheduler.request()
        assertEquals(60_000L, r.workSpec.initialDelay)
        assertEquals(NetworkType.CONNECTED, r.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun nothingIsQueuedWhileSharingIsOff() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        SharePrefs(context).apply { mode = Mode.Owner; fileId = null }
        ShareScheduler.changed(context)
        assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork(ShareScheduler.WORK).get().isEmpty())
    }

    @Test
    fun aChangeQueuesOneUploadWhenSharing() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        SharePrefs(context).apply { mode = Mode.Owner; fileId = "abc"; ownerName = "Midhun" }
        ShareScheduler.changed(context)
        ShareScheduler.changed(context)
        assertEquals(1, WorkManager.getInstance(context).getWorkInfosForUniqueWork(ShareScheduler.WORK).get().count { !it.state.isFinished })
    }
}
