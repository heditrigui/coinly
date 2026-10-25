package com.coinly.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinly.app.data.Money
import com.coinly.app.data.friendly
import java.time.LocalDate
import com.coinly.app.ui.components.AppCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currency: String,
    onCurrency: (String) -> Unit,
    theme: String,
    onTheme: (String) -> Unit,
    budgetAlerts: Boolean,
    alertsBlocked: Boolean,
    onBudgetAlerts: (Boolean) -> Unit,
    onAllowNotifications: () -> Unit,
    lastBackup: LocalDate?,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    version: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDemo: () -> Unit,
    onClear: () -> Unit,
) {
    var confirm by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "📱 System", "light" to "☀️ Light", "dark" to "🌙 Dark").forEach { (key, label) ->
                        FilterChip(selected = theme == key, onClick = { onTheme(key) }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("System follows your phone's dark mode setting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AppCard {
                Text("Currency", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Money.currencies.forEach { c ->
                        FilterChip(selected = c == currency, onClick = { onCurrency(c) }, label = { Text(c) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Preview: ${Money.format(1234.5, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AppCard(padding = 6.dp) {
                SettingRow(
                    Icons.Outlined.NotificationsActive,
                    "Budget alerts",
                    when {
                        budgetAlerts && alertsBlocked -> "Notifications are off for Coinly. Tap to allow them."
                        else -> "Notify me when a budget reaches 80% and when it goes over"
                    },
                    trailing = { Switch(checked = budgetAlerts, onCheckedChange = onBudgetAlerts) },
                ) { if (budgetAlerts && alertsBlocked) onAllowNotifications() else onBudgetAlerts(!budgetAlerts) }
            }

            AppCard(padding = 6.dp) {
                SettingRow(
                    Icons.Outlined.Backup,
                    "Back up to a file",
                    "Keep it in Drive, email or a computer. " + (lastBackup?.let { "Last backup: ${it.friendly()}" } ?: "No backup yet"),
                    onClick = onBackup,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.SettingsBackupRestore, "Restore from a backup", "Bring back everything from a Coinly backup file", onClick = onRestore)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.IosShare, "Export to CSV", "Open your transactions in Excel or Sheets", onClick = onExport)
            }

            AppCard(padding = 6.dp) {
                SettingRow(Icons.Outlined.AutoAwesome, "Load demo data", "Replace everything with 6 months of samples") { confirm = "demo" }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.DeleteForever, "Delete all data", "Start fresh. This can't be undone.", tint = MaterialTheme.colorScheme.error) { confirm = "clear" }
            }

            AppCard {
                Text("🔒 Private by design", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Coinly works fully offline. No account, no ads, no tracking: your data stays on this phone, and backups go only where you save them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "Coinly $version",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (confirm) {
        "demo" -> ConfirmDialog("Load demo data?", "Your current transactions, budgets and goals will be replaced.", "Load", { confirm = null }) {
            confirm = null
            onDemo()
        }
        "clear" -> ConfirmDialog("Delete everything?", "All transactions, budgets, goals and recurring entries will be permanently removed.", "Delete", { confirm = null }) {
            confirm = null
            onClear()
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint ?: MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
