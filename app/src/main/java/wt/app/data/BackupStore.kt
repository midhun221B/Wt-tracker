package wt.app.data

import androidx.room.withTransaction
import wt.core.model.AppData
import wt.core.model.PlanRecord
import wt.core.model.WeighInSchedule

/** Reads everything in the database as core models (for export and backup). */
suspend fun AppDatabase.loadAll(): AppData {
    val profile = profile().get() ?: error("Database not initialised")
    val checkpoints = plans().allCheckpoints().groupBy { it.planId }
    return AppData(
        profile = profile.toModel(),
        weights = weights().all().map { it.toModel() },
        body = bodyComp().all().map { it.toModel() },
        runs = runs().all().map { it.toModel() },
        restDays = restDays().all().map { it.date }.toSet(),
        plans = plans().allPlans().map { p ->
            PlanRecord(p.id, p.label, p.createdAt, p.active, checkpoints[p.id].orEmpty().map { it.toModel() })
        },
        schedule = WeighInSchedule(profile.weighInDay, profile.reminderEnabled, profile.reminderHour, profile.reminderMinute),
    )
}

/** Deletes everything, the profile too, so the app is back at first-run setup (used by "Stop following"). */
suspend fun AppDatabase.clearAll() = withTransaction {
    weights().deleteAll()
    bodyComp().deleteAll()
    runs().deleteAll()
    restDays().deleteAll()
    plans().deleteAll()
    profile().deleteAll()
}

/**
 * Replaces all stored data with [data] in one transaction. The weigh-in day and reminder come from the backup; an
 * older backup without them keeps the phone's own.
 */
suspend fun AppDatabase.replaceAll(data: AppData) = withTransaction {
    val reminder = profile().get()
    weights().deleteAll()
    bodyComp().deleteAll()
    runs().deleteAll()
    restDays().deleteAll()
    plans().deleteAll()

    profile().upsert(
        ProfileEntity.from(data.profile).let {
            val s = data.schedule
            when {
                s != null -> it.copy(
                    reminderHour = s.reminderHour, reminderMinute = s.reminderMinute,
                    reminderEnabled = s.reminderEnabled, weighInDay = s.weighInDay,
                )
                reminder != null -> it.copy(
                    reminderHour = reminder.reminderHour, reminderMinute = reminder.reminderMinute,
                    reminderEnabled = reminder.reminderEnabled, weighInDay = reminder.weighInDay,
                )
                else -> it
            }
        },
    )
    weights().insertAll(data.weights.map { WeightEntity(it.date, it.kg, it.sleepHours, it.hunger, it.snacks, it.note) })
    bodyComp().insertAll(data.body.map { BodyCompEntity(it.date, it.fatPct, it.visceral, it.muscleKg, it.skeletalPct, it.leanKg, it.bmrKcal) })
    runs().insertIgnoringDuplicates(data.runs.map { RunEntity(date = it.date, km = it.km, durationSec = it.durationSec, kcal = it.kcal, source = it.source, externalId = it.externalId) })
    data.restDays.forEach { restDays().insert(RestDayEntity(it)) }
    // Insert inactive plans first so the active one ends up with the highest id.
    data.plans.sortedBy { it.active }.forEach { p ->
        val id = plans().insertPlan(PlanEntity(createdAt = p.createdAt, active = p.active, label = p.label))
        plans().insertCheckpoints(p.checkpoints.map { CheckpointEntity(id, it.date, it.kg) })
    }
}
