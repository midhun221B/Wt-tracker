package wt.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wt.app.notify.Reminder

enum class Tab(val label: String, val icon: ImageVector) {
    LOG("Today", Icons.Default.Edit),
    DASHBOARD("Dashboard", Icons.Default.Home),
    RUNS("Runs", Icons.Default.PlayArrow),
    BODY("Body", Icons.Default.Person),
    PLAN("Plan", Icons.Default.DateRange),
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
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (settingsOpen) "Settings" else tab.label) },
                navigationIcon = {
                    if (settingsOpen) IconButton(onClick = { settingsOpen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!settingsOpen) IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = !settingsOpen && tab == t,
                        onClick = { tab = t; settingsOpen = false },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
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
            Tab.LOG -> LogScreen(s, vm::saveWeight, vm::deleteWeight, vm::setRestDay, vm::saveRun, modifier)
            Tab.DASHBOARD -> DashboardScreen(s.dashboard, modifier)
            Tab.RUNS -> RunsScreen(s.runs, s.today, vm::saveRun, vm::deleteRun, modifier)
            Tab.BODY -> BodyScreen(s.body, s.today, vm::saveBody, vm::deleteBody, modifier)
            Tab.PLAN -> PlanScreen(s, vm::saveCheckpoints, vm::applyRebaseline, vm::saveProfile, modifier)
        }
    }
}
