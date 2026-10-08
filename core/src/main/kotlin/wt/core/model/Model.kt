package wt.core.model

import java.time.LocalDate
import java.time.ZoneId

val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

fun todayInTokyo(): LocalDate = LocalDate.now(TOKYO)

data class WeightEntry(
    val date: LocalDate,
    val kg: Double,
    val sleepHours: Double? = null,
    val hunger: Int? = null, // 1 (not hungry) .. 5 (very hungry)
    val snacks: String? = null,
    val note: String? = null,
)

data class BodyComp(
    val date: LocalDate,
    val fatPct: Double,
    val visceral: Double,
    val muscleKg: Double,
    val skeletalPct: Double? = null,
    val leanKg: Double? = null,
    val bmrKcal: Double? = null,
)

/** A run. [date] is null for seed runs whose date the user still has to set. */
data class Run(
    val date: LocalDate?,
    val km: Double,
    val durationSec: Int,
    val kcal: Double? = null,
    val source: String = "manual",
    val externalId: String? = null,
) {
    val paceSecPerKm: Double get() = durationSec / km
}

data class Checkpoint(val date: LocalDate, val kg: Double)

data class Profile(
    val sex: String,
    val birthYear: Int,
    val heightCm: Double,
    val bmrKcal: Double,
    val activityFactor: Double = 1.4,
    val goalKg: Double,
    val goalDate: LocalDate,
    val plannedFoodDeficitKcal: Double = 350.0,
)

/** Formats seconds as m:ss (pace per km or run duration under an hour). */
fun formatMinSec(totalSec: Double): String {
    val s = Math.round(totalSec)
    return "%d:%02d".format(s / 60, s % 60)
}

data class PlanRecord(
    val id: Long,
    val label: String,
    val createdAt: LocalDate,
    val active: Boolean,
    val checkpoints: List<Checkpoint>,
)

/** Everything the app stores; used for backup/restore and export. */
data class AppData(
    val profile: Profile,
    val weights: List<WeightEntry>,
    val body: List<BodyComp>,
    val runs: List<Run>,
    val restDays: Set<LocalDate>,
    val plans: List<PlanRecord>,
)
