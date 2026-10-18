package wt.app.share

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wt.app.data.AppDatabase
import wt.app.data.loadAll
import wt.core.io.DriveClient
import wt.core.io.DriveException
import wt.core.io.ShareSnapshot
import wt.core.io.encodeShare
import wt.core.model.TOKYO
import java.io.IOException
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Uploads my latest data to the shared Drive file a minute after a change. */
object ShareScheduler {
    const val WORK = "share-upload"

    /** One upload, a minute out (so quick edits go up together), once there's a connection. */
    fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ShareWorker>()
        .setInitialDelay(60, TimeUnit.SECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    /** Call after any change to my data; does nothing unless I share it. */
    fun changed(context: Context) {
        val prefs = SharePrefs(context)
        if (prefs.mode != Mode.Owner || prefs.fileId == null) return
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

/** What my partner's app reads: all my data, my name and the time now (Tokyo). */
suspend fun shareSnapshot(context: Context, name: String): ShareSnapshot =
    ShareSnapshot(name, ZonedDateTime.now(TOKYO).withNano(0).toString(), AppDatabase.get(context).loadAll())

class ShareWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = SharePrefs(applicationContext)
        val id = prefs.fileId
        if (prefs.mode != Mode.Owner || id == null) return Result.success()
        val token = when (val s = DriveAuth.silent(applicationContext)) {
            is DriveAuth.Silent.Token -> s.token
            DriveAuth.Silent.NeedsSignIn -> {
                prefs.error = ShareError.NeedsSignIn
                return Result.failure()
            }
            DriveAuth.Silent.Unavailable -> {
                prefs.error = ShareError.Offline
                return Result.retry()
            }
        }
        return try {
            val json = encodeShare(shareSnapshot(applicationContext, prefs.ownerName.orEmpty()))
            withContext(Dispatchers.IO) { DriveClient(token).update(id, json) }
            prefs.lastUploadMillis = System.currentTimeMillis()
            prefs.error = null
            Result.success()
        } catch (e: DriveException) {
            when (e.code) {
                401 -> { prefs.error = ShareError.NeedsSignIn; Result.failure() }
                404 -> { prefs.error = ShareError.FileRemoved; Result.failure() }
                else -> { prefs.error = ShareError.Offline; Result.retry() }
            }
        } catch (e: IOException) {
            prefs.error = ShareError.Offline
            Result.retry()
        }
    }
}
