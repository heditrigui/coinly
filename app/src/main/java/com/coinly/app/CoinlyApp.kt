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
import com.coinly.app.data.CsvMapping
import com.coinly.app.data.JsonCodec
import com.coinly.app.data.Money
import com.coinly.app.data.Statement
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.full
import com.coinly.app.data.guessMapping
import com.coinly.app.data.parseStatement
import com.coinly.app.data.pct
import com.coinly.app.ui.components.RecurringDialog
import com.coinly.app.ui.screens.HistoryScreen
import com.coinly.app.ui.screens.HomeScreen
import com.coinly.app.ui.screens.ImportScreen
import com.coinly.app.ui.screens.fallbackMapping
import com.coinly.app.ui.screens.InsightsScreen
import com.coinly.app.ui.screens.PlanScreen
import com.coinly.app.ui.screens.SettingsScreen
import com.coinly.app.ui.screens.TransactionSheet
import com.coinly.app.ui.screens.YearReportScreen
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

/** A bank statement being imported, with the columns to read it by. */
private class ImportState(val statement: Statement, val mapping: CsvMapping, val remembered: Boolean)

@Composable
fun CoinlyApp(vm: FinanceViewModel, lock: LockControl) {
    val data by vm.data.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val current by vm.currentStats.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showYear by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf<ImportState?>(null) }
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
    // Bill reminders need it too.
    val wantsReminders = data.recurring.any { it.remindDaysBefore != null }
    LaunchedEffect(wantsReminders) {
        if (wantsReminders && !prefs.getBoolean("askedNotifications", false)) requestNotifications()
    }

    // The widget's + button.
    val quickAdd by vm.quickAdd.collectAsStateWithLifecycle()
    LaunchedEffect(quickAdd) {
        if (quickAdd) {
            importing = null
            showYear = false
            showSettings = false
            editor = EditorState(null, TxType.EXPENSE)
            vm.quickAddHandled()
        }
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

    val openStatement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            parseStatement(text)
        }
            .onSuccess { st ->
                if (st.rows.isEmpty()) {
                    notify("No rows found in that file.")
                } else {
                    val saved = data.importMappings[st.key]?.takeIf { m -> listOfNotNull(m.dateCol, m.descCol, m.amountCol, m.inCol).all { it < st.columnCount } }
                    importing = ImportState(st, saved ?: guessMapping(st) ?: fallbackMapping(st), saved != null)
                }
            }
            .onFailure { notify("Couldn't read that file.") }
    }

    val imp = importing
    if (imp != null) {
        BackHandler { importing = null }
        ImportScreen(
            statement = imp.statement,
            initialMapping = imp.mapping,
            remembered = imp.remembered,
            data = data,
            onBack = { importing = null },
            onImport = { txs, mapping ->
                vm.importTransactions(txs, imp.statement.key, mapping)
                importing = null
                showSettings = false
                tab = Tab.HISTORY
                notify(if (txs.size == 1) "📥 Imported 1 transaction" else "📥 Imported ${txs.size} transactions")
            },
        )
    } else if (showYear) {
        BackHandler { showYear = false }
        YearReportScreen(data) { showYear = false }
    } else if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            currency = data.currency,
            onCurrency = vm::setCurrency,
            theme = data.theme,
            onTheme = vm::setTheme,
            budgetAlerts = data.budgetAlerts,
            alertsBlocked = !canNotify,
            appLock = data.appLock,
            lockAvailable = remember { lock.available() },
            onAppLock = { on ->
                when {
                    on && !lock.available() -> notify("Set a screen lock in your phone's settings first.")
                    on -> lock.confirm("Turn on app lock") { vm.setAppLock(true); notify("🔒 App lock is on") }
                    else -> lock.confirm("Turn off app lock") { vm.setAppLock(false); notify("App lock is off") }
                }
            },
            onImport = { openStatement.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream")) },
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
            onExport = { shareCsv(context, vm.exportCsv(), vm.exportDebtsCsv()) },
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
                        Tab.INSIGHTS -> InsightsScreen(stats, data.currency, vm::prevMonth, vm::nextMonth) { showYear = true }
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
            onSaveSplit = { bill, person, myShare, iPaid ->
                val alerts = vm.saveSplit(bill, person, myShare, iPaid)
                editor = null
                val owed = if (iPaid) "$person owes you ${Money.format(bill.amount - myShare, data.currency)}" else "you owe $person ${Money.format(myShare, data.currency)}"
                val saved = "${bill.category.emoji} Split saved · $owed"
                notify(alerts.firstOrNull()?.let { "$saved · ${alertText(it)}" } ?: saved)
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
                    "It has ${count(backup.transactions.size, "transaction")}, ${count(backup.budgets.size, "budget")}, ${count(backup.goals.size, "goal")} and ${count(backup.debts.size, "debt")}.$made " +
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

/** Shares the transactions CSV, plus a second file with debts when there are any. */
private fun shareCsv(context: Context, csv: String, debtsCsv: String?) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    fun uriFor(name: String, text: String) = File(dir, name).let { f ->
        f.writeText(text)
        FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }
    val uris = arrayListOf(uriFor("coinly-transactions.csv", csv))
    if (debtsCsv != null) uris += uriFor("coinly-debts.csv", debtsCsv)
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    send.type = "text/csv"
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Export transactions"))
}
