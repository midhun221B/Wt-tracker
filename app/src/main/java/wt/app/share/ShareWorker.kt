package wt.app.share

import android.content.Context
import androidx.work.OneTimeWorkRequest

/** Uploads my latest data to the shared Drive file a minute after a change. */
object ShareScheduler {
    const val WORK = "share-upload"

    fun request(): OneTimeWorkRequest = TODO()

    fun changed(context: Context): Unit = TODO()
}
