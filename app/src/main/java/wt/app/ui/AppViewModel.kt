package wt.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wt.app.data.AppDatabase
import wt.app.data.BodyCompEntity
import wt.app.data.CheckpointEntity
import wt.app.data.PlanEntity
import wt.app.data.ProfileEntity
import wt.app.data.RestDayEntity
import wt.app.data.RunEntity
import wt.app.data.WeightEntity
import wt.app.data.importStrava
import wt.app.data.loadAll
import wt.app.data.recognizeLines
import wt.app.data.replaceAll
import wt.app.notify.Reminder
import wt.core.dashboard.Dashboard
import wt.core.dashboard.buildDashboard
import wt.core.io.bodyCsv
import wt.core.io.decodeBackup
import wt.core.io.encodeBackup
import wt.core.io.ScreenshotReading
import wt.core.io.StoredRun
import wt.core.io.ocrRows
import wt.core.io.planStravaImport
import wt.core.io.readScreenshot
import wt.core.io.runsCsv
import wt.core.io.weightsCsv
import wt.core.model.Checkpoint
import wt.core.model.TOKYO
import wt.core.model.todayInTokyo
import java.time.LocalDate
import java.time.ZonedDateTime

/** Everything the screens need, rebuilt whenever stored data changes. */
data class UiState(
    val today: LocalDate,
    val dashboard: Dashboard,
    val weights: List<WeightEntity>,
    val runs: List<RunEntity>,
    val restDays: Set<LocalDate>,
    val body: List<BodyCompEntity>,
    val checkpoints: List<Checkpoint>,
    val plans: List<PlanEntity>,
    val profile: ProfileEntity,
)

enum class ExportKind(val fileName: String, val mime: String) {
    WEIGHTS_CSV("weights.csv", "text/csv"),
    RUNS_CSV("runs.csv", "text/csv"),
    BODY_CSV("body.csv", "text/csv"),
    BACKUP_JSON("wt-tracker-backup.json", "application/json"),
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val today = MutableStateFlow(todayInTokyo())

    /** Report of the last Strava import, shown in a dialog until dismissed. */
    val importReport = MutableStateFlow<String?>(null)

    /** What the last screenshot contained, shown in a confirm dialog until saved or dismissed. */
    val screenshot = MutableStateFlow<ScreenshotReading?>(null)

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the snackbar. */
    val messages = messageChannel.receiveAsFlow()

    private data class Stored(
        val weights: List<WeightEntity>,
        val runs: List<RunEntity>,
        val rest: List<RestDayEntity>,
        val body: List<BodyCompEntity>,
        val checkpoints: List<CheckpointEntity>,
    )

    private val stored = combine(
        db.weights().observeAll(), db.runs().observeAll(), db.restDays().observeAll(),
        db.bodyComp().observeAll(), db.plans().observeActiveCheckpoints(),
    ) { w, r, rest, b, c -> Stored(w, r, rest, b, c) }

    /** Null until the database is seeded. */
    val state: StateFlow<UiState?> = combine(stored, db.profile().observe(), db.plans().observePlans(), today) { s, profile, plans, day ->
        if (profile == null || s.checkpoints.size < 2) return@combine null
        val checkpoints = s.checkpoints.map { it.toModel() }
        val restDays = s.rest.map { it.date }.toSet()
        UiState(
            today = day,
            dashboard = buildDashboard(
                weights = s.weights.map { it.toModel() },
                runs = s.runs.map { it.toModel() },
                restDays = restDays,
                body = s.body.map { it.toModel() },
                checkpoints = checkpoints,
                profile = profile.toModel(),
                asOf = day,
            ),
            weights = s.weights,
            runs = s.runs,
            restDays = restDays,
            body = s.body,
            checkpoints = checkpoints,
            plans = plans,
            profile = profile,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            db.seedIfEmpty()
            db.removeSampleRuns()
            val p = db.profile().get() ?: return@launch
            Reminder.schedule(getApplication(), p.reminderEnabled, p.reminderHour, p.reminderMinute)
        }
    }

    /** Call on resume so "today" rolls over after midnight in Tokyo. */
    fun refreshToday() {
        today.value = todayInTokyo()
    }

    private fun launchWithMessage(done: String?, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            done?.let { messageChannel.send(it) }
        } catch (e: Exception) {
            messageChannel.send(e.message ?: "Something went wrong")
        }
    }

    fun saveWeight(entry: WeightEntity) = launchWithMessage("Saved ${entry.kg} kg for ${entry.date}") { db.weights().upsert(entry) }

    fun deleteWeight(date: LocalDate) = launchWithMessage("Deleted weight for $date") { db.weights().delete(date) }

    fun setRestDay(date: LocalDate, rest: Boolean) = launchWithMessage(null) {
        if (rest) db.restDays().insert(RestDayEntity(date)) else db.restDays().delete(date)
    }

    fun saveRun(run: RunEntity) = launchWithMessage("Run saved") { db.runs().upsert(run) }

    fun deleteRun(run: RunEntity) = launchWithMessage("Run deleted") { db.runs().delete(run) }

    fun saveBody(entry: BodyCompEntity) = launchWithMessage("Measurement saved") { db.bodyComp().upsert(entry) }

    fun deleteBody(date: LocalDate) = launchWithMessage("Measurement deleted") { db.bodyComp().delete(date) }

    fun saveCheckpoints(points: List<Checkpoint>) = launchWithMessage("Plan updated") {
        require(points.size >= 2) { "A plan needs at least two checkpoints" }
        require(points.map { it.date }.toSet().size == points.size) { "Checkpoint dates must be different" }
        db.plans().replaceActiveCheckpoints(points.map { CheckpointEntity(0, it.date, it.kg) })
    }

    fun applyRebaseline(points: List<Checkpoint>) = launchWithMessage("New plan from today's weight") {
        db.plans().activateNewPlan(
            PlanEntity(createdAt = today.value, active = true, label = "Re-baseline ${today.value}"),
            points.map { CheckpointEntity(0, it.date, it.kg) },
        )
    }

    fun saveProfile(profile: ProfileEntity) = launchWithMessage("Settings saved") { db.profile().upsert(profile) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = launchWithMessage(
        if (enabled) "Reminder set for %02d:%02d".format(hour, minute) else "Reminder off",
    ) {
        val p = db.profile().get() ?: return@launchWithMessage
        db.profile().upsert(p.copy(reminderEnabled = enabled, reminderHour = hour, reminderMinute = minute))
        Reminder.schedule(getApplication(), enabled, hour, minute)
    }

    fun setWeighInDay(day: Int) = launchWithMessage("Weigh-in day set") {
        val p = db.profile().get() ?: return@launchWithMessage
        db.profile().upsert(p.copy(weighInDay = day))
    }

    fun export(kind: ExportKind, uri: Uri) = launchWithMessage("Exported ${kind.fileName}") {
        val data = db.loadAll()
        val text = when (kind) {
            ExportKind.WEIGHTS_CSV -> weightsCsv(data.weights, data.restDays)
            ExportKind.RUNS_CSV -> runsCsv(data.runs)
            ExportKind.BODY_CSV -> bodyCsv(data.body)
            ExportKind.BACKUP_JSON -> encodeBackup(data, ZonedDateTime.now(TOKYO).withNano(0).toString())
        }
        writeText(uri, text)
    }

    fun restore(uri: Uri) = launchWithMessage("Backup restored") {
        db.replaceAll(decodeBackup(readText(uri)))
    }

    fun importStrava(uri: Uri) = launchWithMessage(null) {
        val text = readText(uri)
        importReport.value = db.importStrava(text)
    }

    fun readScreenshot(uri: Uri) = launchWithMessage(null) {
        val rows = ocrRows(recognizeLines(getApplication(), uri))
        val reading = withContext(Dispatchers.Default) { readScreenshot(rows, db.profile().get()?.heightCm) }
        if (reading == null) messageChannel.send("Couldn't find a run or body measurement in this image")
        screenshot.value = reading
    }

    /** Saves a run read from a screenshot; a run already stored for that day with about the same distance is replaced. */
    fun saveScreenshotRun(run: RunEntity) = launchWithMessage("Run saved") {
        screenshot.value = null
        db.withTransaction {
            val existing = db.runs().all().map { StoredRun(it.id, it.toModel()) }
            val match = planStravaImport(existing, listOf(run.toModel())).updates.firstOrNull()
            db.runs().upsert(run.copy(id = match?.id ?: 0))
        }
    }

    /** Saves a body measurement read from a screenshot and, when given, the weight for the same day. */
    fun saveScreenshotBody(entry: BodyCompEntity, weightKg: Double?) = launchWithMessage(
        if (weightKg != null) "Saved measurement and ${weightKg} kg for ${entry.date}" else "Measurement saved",
    ) {
        screenshot.value = null
        db.withTransaction {
            db.bodyComp().upsert(entry)
            if (weightKg != null) {
                // Keep sleep, hunger and notes already logged for that day.
                db.weights().upsert(db.weights().get(entry.date)?.copy(kg = weightKg) ?: WeightEntity(entry.date, weightKg))
            }
        }
    }

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    } ?: error("Couldn't open the file")

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
        out.use { it.write(text.toByteArray()) }
    }
}
