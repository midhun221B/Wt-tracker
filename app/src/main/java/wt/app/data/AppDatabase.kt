package wt.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import wt.core.model.Defaults
import wt.core.model.todayInTokyo

@Database(
    entities = [
        WeightEntity::class, BodyCompEntity::class, RunEntity::class, RestDayEntity::class,
        PlanEntity::class, CheckpointEntity::class, ProfileEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weights(): WeightDao
    abstract fun bodyComp(): BodyCompDao
    abstract fun runs(): RunDao
    abstract fun restDays(): RestDayDao
    abstract fun plans(): PlanDao
    abstract fun profile(): ProfileDao

    /** Fills a fresh database with the starting data. Does nothing once a profile exists. */
    suspend fun seedIfEmpty() = withTransaction {
        if (profile().get() != null) return@withTransaction
        profile().upsert(ProfileEntity.from(Defaults.profile))
        weights().upsert(Defaults.startWeight.let { WeightEntity(it.date, it.kg) })
        Defaults.startBody.let { bodyComp().upsert(BodyCompEntity(it.date, it.fatPct, it.visceral, it.muscleKg, it.skeletalPct, it.leanKg, it.bmrKcal)) }
        Defaults.sampleRuns.forEach { runs().upsert(RunEntity(date = it.date, km = it.km, durationSec = it.durationSec)) }
        plans().activateNewPlan(
            PlanEntity(createdAt = todayInTokyo(), active = true, label = "Original plan"),
            Defaults.planCheckpoints.map { CheckpointEntity(0, it.date, it.kg) },
        )
    }

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "wt-tracker.db")
                .build().also { instance = it }
        }
    }
}
