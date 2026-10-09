package wt.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wt.app.R
import wt.app.notify.Reminder
import wt.core.io.BodyReading
import wt.core.io.RunReading

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    LOG("Today", R.drawable.ic_tab_today),
    DASHBOARD("Trend", R.drawable.ic_tab_trend),
    RUNS("Runs", R.drawable.ic_tab_runs),
    BODY("Body", R.drawable.ic_tab_body),
    PLAN("Plan", R.drawable.ic_tab_plan),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.LOG) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var notificationsAllowed by remember { mutableStateOf(Reminder.hasPermission(context)) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    var pendingExport by remember { mutableStateOf<ExportKind?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val kind = pendingExport
        if (uri != null && kind != null) vm.export(kind, uri)
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(ExportKind.BACKUP_JSON, uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restore(uri)
    }
    val stravaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importStrava(uri)
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.readScreenshot(uri)
    }
    val pickScreenshot = { imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val pickStravaCsv = { stravaLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "text/plain", "*/*")) }
    val screenshot by vm.screenshot.collectAsStateWithLifecycle()
    state?.let { s ->
        when (val r = screenshot) {
            is RunReading -> RunDialog(
                initial = null, defaultDate = s.today, prefill = r,
                onDismiss = { vm.screenshot.value = null }, onSave = vm::saveScreenshotRun,
            )
            is BodyReading -> BodyDialog(
                initial = null, today = s.today, previous = null, prefill = r,
                onDismiss = { vm.screenshot.value = null }, onSave = vm::saveScreenshotBody,
            )
            null -> {}
        }
    }
    val importReport by vm.importReport.collectAsStateWithLifecycle()
    importReport?.let { report ->
        FormDialog(
            title = "Strava import",
            onDismiss = { vm.importReport.value = null },
            confirmLabel = "OK",
            onConfirm = { vm.importReport.value = null },
            dismissLabel = null,
        ) { Text(report) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }

    Scaffold(
        containerColor = Palette.Background,
        topBar = {
            val s = state
            val (subtitle, title) = when {
                settingsOpen -> "Reminder, export and backup" to "Settings"
                s == null -> "" to tab.label
                tab == Tab.DASHBOARD -> s.dashboard.programWeek().let { (w, n) -> longDay(s.today) to "Week $w of $n" }
                else -> longDay(s.today) to tab.label
            }
            TopAppBar(
                title = {
                    Column {
                        if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        Text(title, style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    if (settingsOpen) IconButton(onClick = { settingsOpen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!settingsOpen) {
                        FilledIconButton(
                            onClick = { settingsOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Card, contentColor = Palette.Text),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Icon(Icons.Default.Settings, "Settings") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Background, titleContentColor = Palette.Text),
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = Palette.CardHigh)
                NavigationBar(containerColor = Palette.Background) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = !settingsOpen && tab == t,
                            onClick = { tab = t; settingsOpen = false },
                            icon = { Icon(painterResource(t.icon), null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Accent,
                                selectedTextColor = Palette.Accent,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = Palette.Muted,
                                unselectedTextColor = Palette.Muted,
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = state
        val modifier = Modifier.padding(padding)
        if (s == null) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (settingsOpen) {
            SettingsScreen(
                profile = s.profile,
                notificationsAllowed = notificationsAllowed,
                onReminder = { on, h, m ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vm.setReminder(on, h, m)
                },
                onWeighInDay = vm::setWeighInDay,
                onExport = { kind ->
                    if (kind == ExportKind.BACKUP_JSON) {
                        jsonLauncher.launch("wt-tracker-backup-${s.today}.json")
                    } else {
                        pendingExport = kind
                        csvLauncher.launch(kind.fileName.replace(".csv", "-${s.today}.csv"))
                    }
                },
                onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = modifier,
            )
            return@Scaffold
        }
        when (tab) {
            Tab.LOG -> LogScreen(s, vm::saveWeight, vm::deleteWeight, vm::setRestDay, vm::saveRun, modifier, onScreenshot = pickScreenshot, onWeighInDay = vm::setWeighInDay)
            Tab.DASHBOARD -> DashboardScreen(s.dashboard, modifier)
            Tab.RUNS -> RunsScreen(
                s.runs, s.today, vm::saveRun, vm::deleteRun,
                onImportStrava = pickStravaCsv,
                modifier = modifier,
                weeks = s.dashboard.weekly,
                week1 = s.dashboard.week1,
                onScreenshot = pickScreenshot,
            )
            Tab.BODY -> BodyScreen(s.body, s.today, vm::saveBody, vm::deleteBody, modifier, onScreenshot = pickScreenshot, weighInDay = s.profile.weighInDay)
            Tab.PLAN -> PlanScreen(s, vm::saveCheckpoints, vm::applyRebaseline, vm::saveProfile, modifier)
        }
    }
}
