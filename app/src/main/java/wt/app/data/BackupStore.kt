package wt.app.data

import androidx.room.withTransaction
import wt.core.model.AppData
import wt.core.model.PlanRecord

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
    )
}

/** Replaces all stored data with [data]; keeps the reminder settings and weigh-in day. Runs in one transaction. */
suspend fun AppDatabase.replaceAll(data: AppData) = withTransaction {
    val reminder = profile().get()
    weights().deleteAll()
    bodyComp().deleteAll()
    runs().deleteAll()
    restDays().deleteAll()
    plans().deleteAll()

    profile().upsert(
        ProfileEntity.from(data.profile).let {
            if (reminder == null) it
            else it.copy(
                reminderHour = reminder.reminderHour, reminderMinute = reminder.reminderMinute,
                reminderEnabled = reminder.reminderEnabled, weighInDay = reminder.weighInDay,
            )
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
