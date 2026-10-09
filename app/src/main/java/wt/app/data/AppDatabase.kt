package wt.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import wt.core.model.Defaults
import wt.core.model.todayInTokyo

@Database(
    entities = [
        WeightEntity::class, BodyCompEntity::class, RunEntity::class, RestDayEntity::class,
        PlanEntity::class, CheckpointEntity::class, ProfileEntity::class,
    ],
    version = 3,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }

        /** v2: on/off switch for the daily weigh-in reminder. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** v3: weekly weigh-in day (ISO weekday, Monday by default). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN weighInDay INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
