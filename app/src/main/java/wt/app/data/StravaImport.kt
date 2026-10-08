package wt.app.data

import androidx.room.withTransaction
import wt.core.io.StoredRun
import wt.core.io.parseStravaActivities
import wt.core.io.planStravaImport

/** Imports runs from Strava's activities.csv in one transaction and returns a short report. */
suspend fun AppDatabase.importStrava(csv: String): String {
    val parsed = parseStravaActivities(csv)
    val (plan, inserted) = withTransaction {
        val existing = runs().all().map { StoredRun(it.id, it.toModel()) }
        val plan = planStravaImport(existing, parsed.runs)
        plan.updates.forEach { u ->
            runs().upsert(
                RunEntity(u.id, u.run.date, u.run.km, u.run.durationSec, u.run.kcal, u.run.source, u.run.externalId),
            )
        }
        val ids = runs().insertIgnoringDuplicates(
            plan.inserts.map { RunEntity(date = it.date, km = it.km, durationSec = it.durationSec, kcal = it.kcal, source = it.source, externalId = it.externalId) },
        )
        plan to ids.count { it != -1L }
    }
    return buildString {
        appendLine("${parsed.totalActivities} activities in the file, ${parsed.runs.size} runs.")
        appendLine("• $inserted new runs added")
        if (plan.updates.isNotEmpty()) appendLine("• ${plan.updates.size} matched runs already in the app (updated with Strava data)")
        if (plan.alreadyImported > 0) appendLine("• ${plan.alreadyImported} already imported before")
        if (parsed.skippedTypes.isNotEmpty()) {
            appendLine("• skipped: " + parsed.skippedTypes.entries.joinToString { "${it.value} ${it.key}" })
        }
        if (parsed.errors.isNotEmpty()) {
            appendLine("• ${parsed.errors.size} rows could not be read:")
            parsed.errors.take(5).forEach { appendLine("   $it") }
            if (parsed.errors.size > 5) appendLine("   …")
        }
    }.trimEnd()
}
