package wt.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import wt.app.MainActivity
import wt.app.R
import wt.app.data.AppDatabase
import wt.core.model.TOKYO
import wt.core.model.todayInTokyo
import wt.core.summary.weighInStatus
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Weekly weigh-in reminder. A daily job runs at the chosen Tokyo time and notifies only while this week's
 * weigh-in is due (see `weighInStatus`), so changing the day needs no rescheduling.
 */
object Reminder {
    private const val CHANNEL_ID = "weigh_in"
    private const val WORK_NAME = "daily-weigh-in"

    fun schedule(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(ZonedDateTime.now(TOKYO), hour, minute).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    /** Time from [now] until the next [hour]:[minute] (tomorrow if it has already passed today). */
    fun delayUntil(now: ZonedDateTime, hour: Int, minute: Int): Duration {
        var next = now.with(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context) {
        if (!hasPermission(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Weigh-in reminder", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Weigh-in day")
            .setContentText("Log this morning's weight.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val today = todayInTokyo()
        val weighInDay = db.profile().get()?.weighInDay ?: 1
        // Due = no weight or scale measurement yet this Monday–Sunday week and the weigh-in day has come (so a missed day keeps reminding).
        val logged = db.weights().all().map { it.date } + db.bodyComp().all().map { it.date }
        if (weighInStatus(logged, today, weighInDay).due) Reminder.show(applicationContext)
        return Result.success()
    }
}
