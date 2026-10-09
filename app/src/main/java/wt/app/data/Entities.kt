package wt.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import wt.core.model.BodyComp
import wt.core.model.Checkpoint
import wt.core.model.Profile
import wt.core.model.Run
import wt.core.model.WeightEntry
import java.time.LocalDate

/** Dates are stored as ISO strings (yyyy-MM-dd), which sort correctly as text. */
class Converters {
    @TypeConverter fun fromDate(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun toDate(s: String?): LocalDate? = s?.let(LocalDate::parse)
}

@Entity(tableName = "weight")
data class WeightEntity(
    @PrimaryKey val date: LocalDate,
    val kg: Double,
    val sleepHours: Double? = null,
    val hunger: Int? = null,
    val snacks: String? = null,
    val note: String? = null,
) {
    fun toModel() = WeightEntry(date, kg, sleepHours, hunger, snacks, note)
}

@Entity(tableName = "body_comp")
data class BodyCompEntity(
    @PrimaryKey val date: LocalDate,
    val fatPct: Double,
    val visceral: Double,
    val muscleKg: Double,
    val skeletalPct: Double? = null,
    val leanKg: Double? = null,
    val bmrKcal: Double? = null,
) {
    fun toModel() = BodyComp(date, fatPct, visceral, muscleKg, skeletalPct, leanKg, bmrKcal)
}

@Entity(tableName = "run", indices = [Index(value = ["externalId"], unique = true)])
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for seed runs the user has not dated yet. */
    val date: LocalDate?,
    val km: Double,
    val durationSec: Int,
    val kcal: Double? = null,
    val source: String = "manual",
    /** Strava Activity ID; unique so re-imports skip duplicates. */
    val externalId: String? = null,
) {
    fun toModel() = Run(date, km, durationSec, kcal, source, externalId)
}

@Entity(tableName = "rest_day")
data class RestDayEntity(@PrimaryKey val date: LocalDate)

@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: LocalDate,
    val active: Boolean,
    val label: String,
)

@Entity(
    tableName = "plan_checkpoint",
    primaryKeys = ["planId", "date"],
    foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
)
data class CheckpointEntity(val planId: Long, val date: LocalDate, val kg: Double) {
    fun toModel() = Checkpoint(date, kg)
}

/** Single-row table (id = 1). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val sex: String,
    val birthYear: Int,
    val heightCm: Double,
    val bmrKcal: Double,
    val activityFactor: Double,
    val goalKg: Double,
    val goalDate: LocalDate,
    val plannedFoodDeficitKcal: Double,
    val reminderHour: Int = 7,
    val reminderMinute: Int = 30,
    @ColumnInfo(defaultValue = "1") val reminderEnabled: Boolean = true,
    /** Weekly weigh-in day as an ISO weekday (1 = Monday … 7 = Sunday). */
    @ColumnInfo(defaultValue = "1") val weighInDay: Int = 1,
) {
    fun toModel() = Profile(sex, birthYear, heightCm, bmrKcal, activityFactor, goalKg, goalDate, plannedFoodDeficitKcal)

    companion object {
        fun from(p: Profile) = ProfileEntity(
            sex = p.sex, birthYear = p.birthYear, heightCm = p.heightCm, bmrKcal = p.bmrKcal,
            activityFactor = p.activityFactor, goalKg = p.goalKg, goalDate = p.goalDate,
            plannedFoodDeficitKcal = p.plannedFoodDeficitKcal,
        )
    }
}
