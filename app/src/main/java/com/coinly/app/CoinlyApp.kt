package com.coinly.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinly.app.data.AppData
import com.coinly.app.data.BudgetAlert
import com.coinly.app.data.JsonCodec
import com.coinly.app.data.Money
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.full
import com.coinly.app.data.pct
import com.coinly.app.ui.components.RecurringDialog
import com.coinly.app.ui.screens.HistoryScreen
import com.coinly.app.ui.screens.HomeScreen
import com.coinly.app.ui.screens.InsightsScreen
import com.coinly.app.ui.screens.PlanScreen
import com.coinly.app.ui.screens.SettingsScreen
import com.coinly.app.ui.screens.TransactionSheet
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Dashboard),
    HISTORY("History", Icons.AutoMirrored.Outlined.ReceiptLong),
    INSIGHTS("Insights", Icons.Outlined.Insights),
    PLAN("Plan", Icons.Outlined.Savings),
}

private data class EditorState(val tx: Transaction?, val type: TxType)

@Composable
fun CoinlyApp(vm: FinanceViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val current by vm.currentStats.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var salarySetup by remember { mutableStateOf(false) }

    val isDark = data.isDark(isSystemInDarkTheme())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun notify(message: String, undo: (() -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    // Budget alerts need the notification permission on Android 13+. Ask once, when the first budget exists.
    var canNotify by remember { mutableStateOf(vm.notifier.canNotify()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = vm.notifier.canNotify() }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = vm.notifier.canNotify() }
    val prefs = remember { context.getSharedPreferences("coinly", Context.MODE_PRIVATE) }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !canNotify) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(data.budgets.isNotEmpty()) {
        if (data.budgets.isNotEmpty() && data.budgetAlerts && !prefs.getBoolean("askedNotifications", false)) requestNotifications()
    }

    var pendingRestore by remember { mutableStateOf<AppData?>(null) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(vm.backupJson().toByteArray()) }
        }.isSuccess
        if (ok) vm.markBackedUp()
        notify(if (ok) "💾 Backup saved" else "Couldn't save the backup there. Try another place.")
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            JsonCodec.decodeBackup(text)
        }
            .onSuccess { pendingRestore = it }
            .onFailure { notify(if (it is IllegalArgumentException) it.message ?: "Not a Coinly backup." else "Couldn't read that file.") }
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            currency = data.currency,
            onCurrency = vm::setCurrency,
            theme = data.theme,
            onTheme = vm::setTheme,
            budgetAlerts = data.budgetAlerts,
            alertsBlocked = !canNotify,
            onBudgetAlerts = { on ->
                vm.setBudgetAlerts(on)
                if (on) requestNotifications()
            },
            onAllowNotifications = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            lastBackup = data.lastBackup,
            onBackup = { saveBackup.launch("coinly-backup-${LocalDate.now()}.json") },
            onRestore = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" },
            onBack = { showSettings = false },
            onExport = { shareCsv(context, vm.exportCsv()) },
            onDemo = {
                vm.loadDemo()
                showSettings = false
                tab = Tab.HOME
                notify("✨ Demo data loaded")
            },
            onClear = {
                vm.clearAll()
                showSettings = false
                tab = Tab.HOME
            },
        )
    } else {
        BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == Tab.HOME || tab == Tab.HISTORY) {
                    ExtendedFloatingActionButton(
                        onClick = { editor = EditorState(null, TxType.EXPENSE) },
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("Add") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                Crossfade(targetState = tab, label = "tab") { t ->
                    when (t) {
                        Tab.HOME -> HomeScreen(
                            data = data,
                            stats = stats,
                            onPrev = vm::prevMonth,
                            onNext = vm::nextMonth,
                            onSettings = { showSettings = true },
                            isDark = isDark,
                            onToggleTheme = { vm.setTheme(if (isDark) "light" else "dark") },
                            onAdd = { type -> editor = EditorState(null, type) },
                            onEdit = { tx -> editor = EditorState(tx, tx.type) },
                            onSetupSalary = { salarySetup = true },
                            onLoadDemo = {
                                vm.loadDemo()
                                notify("✨ Demo data loaded — explore away!")
                            },
                            onSeeAll = { tab = Tab.HISTORY },
                            onOpenInsights = { tab = Tab.INSIGHTS },
                            onOpenPlan = { tab = Tab.PLAN },
                        )
                        Tab.HISTORY -> HistoryScreen(data) { tx -> editor = EditorState(tx, tx.type) }
                        Tab.INSIGHTS -> InsightsScreen(stats, data.currency, vm::prevMonth, vm::nextMonth)
                        Tab.PLAN -> PlanScreen(vm, data, current) { notify(it) }
                    }
                }
            }
        }
    }

    editor?.let { e ->
        TransactionSheet(
            initial = e.tx,
            startType = e.type,
            data = data,
            onDismiss = { editor = null },
            onSave = { t ->
                val alerts = vm.upsertTransaction(t)
                editor = null
                val sign = if (t.type == TxType.INCOME) "+" else "−"
                val saved = if (e.tx == null) "${t.category.emoji} Saved $sign${Money.format(t.amount, data.currency)}" else "Transaction updated"
                notify(alerts.firstOrNull()?.let { "$saved · ${alertText(it)}" } ?: saved)
            },
            onDelete = { t ->
                vm.deleteTransaction(t.id)
                editor = null
                notify("Transaction deleted") { vm.upsertTransaction(t) }
            },
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                val made = backup.lastBackup?.let { " It was made on ${it.full()}." } ?: ""
                Text(
                    "It has ${count(backup.transactions.size, "transaction")}, ${count(backup.budgets.size, "budget")} and ${count(backup.goals.size, "goal")}.$made " +
                        "Everything currently in Coinly will be replaced.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.restore(backup)
                    pendingRestore = null
                    showSettings = false
                    tab = Tab.HOME
                    notify("✅ Backup restored")
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }

    if (salarySetup) {
        RecurringDialog(
            initial = null,
            currency = data.currency,
            onDismiss = { salarySetup = false },
            onSave = {
                vm.saveRecurring(it)
                salarySetup = false
                notify("💼 Salary set — Coinly will add it every month")
            },
            onDelete = null,
            presetType = TxType.INCOME,
            presetCategory = "salary",
            title = "Your monthly salary",
        )
    }
}

private fun count(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

private fun alertText(a: BudgetAlert): String =
    if (a.level >= 2) "${a.status.category.label} is over budget" else "${a.status.category.label} budget ${pct(a.status.ratio.toDouble())} used"

private fun shareCsv(context: Context, csv: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "coinly-transactions.csv")
    file.writeText(csv)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export transactions"))
}
