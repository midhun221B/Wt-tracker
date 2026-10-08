package wt.core.io

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wt.core.model.AppData
import wt.core.model.BodyComp
import wt.core.model.Checkpoint
import wt.core.model.PlanRecord
import wt.core.model.Profile
import wt.core.model.Run
import wt.core.model.WeightEntry
import java.time.LocalDate

/** JSON backup of all app data. Dates are ISO strings so the file is easy to read and edit. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String,
    val profile: ProfileDto,
    val weights: List<WeightDto>,
    val body: List<BodyDto>,
    val runs: List<RunDto>,
    val restDays: List<String>,
    val plans: List<PlanDto>,
) {
    companion object {
        const val FORMAT = "wt-tracker-backup"
        const val VERSION = 1
    }
}

@Serializable
data class ProfileDto(
    val sex: String, val birthYear: Int, val heightCm: Double, val bmrKcal: Double,
    val activityFactor: Double, val goalKg: Double, val goalDate: String, val plannedFoodDeficitKcal: Double,
)

@Serializable
data class WeightDto(
    val date: String, val kg: Double, val sleepHours: Double? = null, val hunger: Int? = null,
    val snacks: String? = null, val note: String? = null,
)

@Serializable
data class BodyDto(
    val date: String, val fatPct: Double, val visceral: Double, val muscleKg: Double,
    val skeletalPct: Double? = null, val leanKg: Double? = null, val bmrKcal: Double? = null,
)

@Serializable
data class RunDto(
    val date: String? = null, val km: Double, val durationSec: Int, val kcal: Double? = null,
    val source: String = "manual", val externalId: String? = null,
)

@Serializable
data class CheckpointDto(val date: String, val kg: Double)

@Serializable
data class PlanDto(val label: String, val createdAt: String, val active: Boolean, val checkpoints: List<CheckpointDto>)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

fun encodeBackup(data: AppData, exportedAt: String): String = json.encodeToString(
    BackupFile.serializer(),
    BackupFile(
        exportedAt = exportedAt,
        profile = data.profile.run {
            ProfileDto(sex, birthYear, heightCm, bmrKcal, activityFactor, goalKg, goalDate.toString(), plannedFoodDeficitKcal)
        },
        weights = data.weights.map { WeightDto(it.date.toString(), it.kg, it.sleepHours, it.hunger, it.snacks, it.note) },
        body = data.body.map { BodyDto(it.date.toString(), it.fatPct, it.visceral, it.muscleKg, it.skeletalPct, it.leanKg, it.bmrKcal) },
        runs = data.runs.map { RunDto(it.date?.toString(), it.km, it.durationSec, it.kcal, it.source, it.externalId) },
        restDays = data.restDays.sorted().map { it.toString() },
        plans = data.plans.map { p ->
            PlanDto(p.label, p.createdAt.toString(), p.active, p.checkpoints.map { CheckpointDto(it.date.toString(), it.kg) })
        },
    ),
)

/** Parses a backup; throws IllegalArgumentException with a readable message if the file is not valid. */
fun decodeBackup(text: String): AppData {
    val file = try {
        json.decodeFromString(BackupFile.serializer(), text)
    } catch (e: Exception) {
        throw IllegalArgumentException("Not a valid Wt Tracker backup: ${e.message}", e)
    }
    require(file.format == BackupFile.FORMAT) { "Not a Wt Tracker backup (format '${file.format}')" }
    require(file.version <= BackupFile.VERSION) { "Backup version ${file.version} is newer than this app supports" }
    require(file.plans.count { it.active } == 1) { "Backup must contain exactly one active plan" }

    fun date(s: String): LocalDate = try {
        LocalDate.parse(s)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid date '$s' in backup", e)
    }

    return AppData(
        profile = file.profile.run {
            Profile(sex, birthYear, heightCm, bmrKcal, activityFactor, goalKg, date(goalDate), plannedFoodDeficitKcal)
        },
        weights = file.weights.map { WeightEntry(date(it.date), it.kg, it.sleepHours, it.hunger, it.snacks, it.note) },
        body = file.body.map { BodyComp(date(it.date), it.fatPct, it.visceral, it.muscleKg, it.skeletalPct, it.leanKg, it.bmrKcal) },
        runs = file.runs.map { Run(it.date?.let(::date), it.km, it.durationSec, it.kcal, it.source, it.externalId) },
        restDays = file.restDays.map(::date).toSet(),
        plans = file.plans.mapIndexed { i, p ->
            PlanRecord(i + 1L, p.label, date(p.createdAt), p.active, p.checkpoints.map { Checkpoint(date(it.date), it.kg) })
        },
    )
}
