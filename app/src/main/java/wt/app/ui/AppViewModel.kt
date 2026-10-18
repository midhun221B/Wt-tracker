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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
import wt.app.data.clearAll
import wt.app.share.Mode
import wt.app.share.SharePrefs
import wt.app.share.fetchFollow
import wt.app.share.followSource
import wt.core.io.FetchResult
import wt.core.io.FollowError
import wt.core.io.ShareSnapshot
import kotlinx.coroutines.flow.StateFlow
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

    /** A snackbar message; with [undo], the snackbar offers "Undo". */
    class Message(val text: String, val undo: (() -> Unit)? = null)

    private val messageChannel = Channel<Message>(Channel.BUFFERED)
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

    /** Null while loading, and before first-run setup is done. */
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

    /** True on a fresh install (no profile yet): the app shows first-run setup. Null while loading. */
    val needsSetup: StateFlow<Boolean?> = db.profile().observe().map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val share = SharePrefs(app)

    init {
        viewModelScope.launch {
            db.removeSampleRuns()
            val p = db.profile().get() ?: return@launch
            // A phone following my partner never reminds anyone to weigh in.
            if (share.mode == Mode.Follow) Reminder.schedule(getApplication(), false, 0, 0)
            else Reminder.schedule(getApplication(), p.reminderEnabled, p.reminderHour, p.reminderMinute)
        }
    }

    /** Follow mode: whose data this phone shows, how fresh it is and what went wrong last. */
    data class FollowState(
        val following: Boolean = false,
        val ownerName: String? = null,
        val updatedAt: String? = null,
        val error: FollowError? = null,
        val busy: Boolean = false,
    )

    private fun followFromPrefs() = FollowState(
        following = share.mode == Mode.Follow,
        ownerName = share.ownerName.takeIf { share.mode == Mode.Follow },
        updatedAt = share.lastUpdatedAt,
        error = share.followError,
    )

    private val _follow = MutableStateFlow(followFromPrefs())
    val follow: StateFlow<FollowState> = _follow
    private var lastFollowFetch = 0L

    /**
     * First-run "Follow a partner": downloads the shared file and, only if it reads, makes this phone a read-only
     * copy of my partner's data. A bad link or no connection leaves the phone as it was.
     */
    fun follow(text: String) = viewModelScope.launch {
        val url = followSource(text)
        if (url == null) {
            _follow.value = _follow.value.copy(error = FollowError.NotALink)
            return@launch
        }
        _follow.value = _follow.value.copy(busy = true, error = null)
        when (val r = fetchFollow(url)) {
            is FetchResult.Ok -> {
                share.mode = Mode.Follow
                share.followUrl = url
                applySnapshot(r.snapshot)
                Reminder.schedule(getApplication(), false, 0, 0)
            }
            is FetchResult.Failed -> _follow.value = _follow.value.copy(busy = false, error = r.error)
        }
    }

    /** Follow mode: fetches the latest copy (on start, on resume, on Refresh), at most every 30 s unless [force]. */
    fun refreshFollow(force: Boolean = false) {
        if (share.mode != Mode.Follow || _follow.value.busy) return
        val url = share.followUrl ?: return
        if (!force && System.currentTimeMillis() - lastFollowFetch < 30_000) return
        _follow.value = _follow.value.copy(busy = true)
        viewModelScope.launch {
            when (val r = fetchFollow(url)) {
                is FetchResult.Ok -> applySnapshot(r.snapshot)
                is FetchResult.Failed -> {
                    // Keep showing the last good copy.
                    share.followError = r.error
                    _follow.value = followFromPrefs()
                }
            }
        }
    }

    private suspend fun applySnapshot(s: ShareSnapshot) {
        db.replaceAll(s.data)
        share.ownerName = s.ownerName
        share.lastUpdatedAt = s.updatedAt
        share.followError = null
        lastFollowFetch = System.currentTimeMillis()
        _follow.value = followFromPrefs()
    }

    /** "Stop following": removes my partner's data and returns to first-run setup. */
    fun stopFollowing() = viewModelScope.launch {
        db.clearAll()
        share.clear()
        _follow.value = followFromPrefs()
    }

    /** Saves first-run setup: profile, today's weight and the first plan; then sets the reminder. */
    fun finishSetup(profile: ProfileEntity, todayKg: Double, checkpoints: List<Checkpoint>) = launchWithMessage(null) {
        db.startPlan(profile, WeightEntity(today.value, todayKg), checkpoints)
        Reminder.schedule(getApplication(), profile.reminderEnabled, profile.reminderHour, profile.reminderMinute)
    }

    /** Call on resume so "today" rolls over after midnight in Tokyo. */
    fun refreshToday() {
        today.value = todayInTokyo()
    }

    private fun launchWithMessage(done: String?, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            done?.let { messageChannel.send(Message(it)) }
        } catch (e: Exception) {
            messageChannel.send(Message(e.message ?: "Something went wrong"))
        }
    }

    /** The day whose weigh-in was just saved on Today; it shows the "Weigh-in saved" card instead of a snackbar. */
    val weighInSaved = MutableStateFlow<LocalDate?>(null)

    fun saveWeight(entry: WeightEntity) = launchWithMessage(null) {
        db.weights().upsert(entry)
        weighInSaved.value = entry.date
    }

    fun deleteWeight(date: LocalDate) = launchWithMessage(null) {
        val old = db.weights().get(date) ?: return@launchWithMessage
        db.weights().delete(date)
        messageChannel.send(Message("Deleted %.1f kg for %s".format(old.kg, dayMonth(date))) { launchWithMessage(null) { db.weights().upsert(old) } })
    }

    fun setRestDay(date: LocalDate, rest: Boolean) = launchWithMessage(null) {
        if (rest) db.restDays().insert(RestDayEntity(date)) else db.restDays().delete(date)
    }

    /** True for a few seconds after a new run is saved (form or screenshot); the scaffold shows the banner. */
    val runLogged = MutableStateFlow(false)

    /** New runs show the "Run logged" banner instead of a snackbar; edits get the snackbar. */
    fun saveRun(run: RunEntity) = launchWithMessage(if (run.id == 0L) null else "Run saved") {
        db.runs().upsert(run)
        if (run.id == 0L) runLogged.value = true
    }

    fun deleteRun(run: RunEntity) = launchWithMessage(null) {
        db.runs().delete(run)
        messageChannel.send(Message("Run deleted") { launchWithMessage(null) { db.runs().upsert(run) } })
    }

    fun saveBody(entry: BodyCompEntity) = launchWithMessage("Measurement saved") { db.bodyComp().upsert(entry) }

    fun deleteBody(date: LocalDate) = launchWithMessage(null) {
        val old = db.bodyComp().all().firstOrNull { it.date == date } ?: return@launchWithMessage
        db.bodyComp().delete(date)
        messageChannel.send(Message("Measurement deleted") { launchWithMessage(null) { db.bodyComp().upsert(old) } })
    }

    fun saveCheckpoints(points: List<Checkpoint>) = launchWithMessage("Plan updated") {
        require(points.size >= 2) { "A plan needs at least two checkpoints" }
        require(points.map { it.date }.toSet().size == points.size) { "Checkpoint dates must be different" }
        db.plans().replaceActiveCheckpoints(points.map { CheckpointEntity(0, it.date, it.kg) })
    }

    fun applyRebaseline(points: List<Checkpoint>) = launchWithMessage("New plan from today's weight") {
        db.plans().activateNewPlan(
            PlanEntity(createdAt = today.value, active = true, label = "Re-baseline ${dayMonth(today.value)}"),
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
        db.profile().get()?.let { Reminder.schedule(getApplication(), it.reminderEnabled, it.reminderHour, it.reminderMinute) }
    }

    fun importStrava(uri: Uri) = launchWithMessage(null) {
        val text = readText(uri)
        importReport.value = db.importStrava(text)
    }

    fun readScreenshot(uri: Uri) = launchWithMessage(null) {
        val rows = ocrRows(recognizeLines(getApplication(), uri))
        val reading = withContext(Dispatchers.Default) { readScreenshot(rows, db.profile().get()?.heightCm) }
        if (reading == null) messageChannel.send(Message("Couldn't find a run or body measurement in this image"))
        screenshot.value = reading
    }

    /** Saves a run read from a screenshot; a run already stored for that day with about the same distance is replaced. */
    fun saveScreenshotRun(run: RunEntity) = launchWithMessage(null) {
        screenshot.value = null
        db.withTransaction {
            val existing = db.runs().all().map { StoredRun(it.id, it.toModel()) }
            val match = planStravaImport(existing, listOf(run.toModel())).updates.firstOrNull()
            db.runs().upsert(run.copy(id = match?.id ?: 0))
        }
        runLogged.value = true
    }

    /** Saves a body measurement read from a screenshot and, when given, the weight for the same day. */
    fun saveScreenshotBody(entry: BodyCompEntity, weightKg: Double?) = launchWithMessage(
        if (weightKg != null) "Saved measurement and %.1f kg for %s".format(weightKg, dayMonth(entry.date)) else "Measurement saved",
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
