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
import wt.core.model.Checkpoint

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

    /**
     * Saves first-run setup in one go: the profile, today's weight and the first plan. A fresh install has no
     * profile, and the app shows setup until this runs (or a backup is restored).
     */
    suspend fun startPlan(profile: ProfileEntity, today: WeightEntity, checkpoints: List<Checkpoint>) = withTransaction {
        profile().upsert(profile)
        weights().upsert(today)
        plans().activateNewPlan(
            PlanEntity(createdAt = today.date, active = true, label = "Original plan"),
            checkpoints.map { CheckpointEntity(0, it.date, it.kg) },
        )
    }

    /**
     * Removes the five undated sample runs older installs were seeded with. Runs that were dated or edited
     * no longer match and stay. Safe to call on every start.
     */
    suspend fun removeSampleRuns() = Defaults.sampleRuns.forEach { runs().deleteUndatedManual(it.km, it.durationSec) }

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
