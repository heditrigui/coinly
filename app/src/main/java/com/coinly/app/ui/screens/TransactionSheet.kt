package com.coinly.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.coinly.app.data.AppData
import com.coinly.app.data.BudgetPreview
import com.coinly.app.data.Categories
import com.coinly.app.data.IdGen
import com.coinly.app.data.Money
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.alertLevel
import com.coinly.app.data.budgetPreview
import com.coinly.app.data.frequentEntries
import com.coinly.app.data.friendly
import com.coinly.app.data.knownPeople
import com.coinly.app.data.parseAmount
import com.coinly.app.data.pct
import com.coinly.app.data.trimNum
import com.coinly.app.ui.components.AmountField
import com.coinly.app.ui.components.CategoryPicker
import com.coinly.app.ui.components.PersonField
import com.coinly.app.ui.components.ProgressBar
import com.coinly.app.ui.components.TypeToggle
import com.coinly.app.ui.theme.FinColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(
    initial: Transaction?,
    startType: TxType,
    data: AppData,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    /** The whole bill, who it was shared with, the user's share, and whether the user paid it. */
    onSaveSplit: (Transaction, String, Double, Boolean) -> Unit = { _, _, _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(initial?.type ?: startType) }
    var amount by remember { mutableStateOf(initial?.amount?.let(::trimNum) ?: "") }
    fun defaultCategory(t: TxType) = data.lastCategory[t]?.takeIf { Categories.of(it).type == t } ?: Categories.forType(t).first().id
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: defaultCategory(type)) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var showPicker by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    var person by remember { mutableStateOf("") }
    var iPaid by remember { mutableStateOf(true) }
    // Half of the bill until the user types their own share.
    var shareText by remember { mutableStateOf<String?>(null) }
    val parsed = parseAmount(amount)
    val canSplit = initial == null && type == TxType.EXPENSE
    val splitting = canSplit && split
    val myShare = if (shareText != null) shareText!!.trim().replace(',', '.').toDoubleOrNull() else parsed?.let { Math.round(it * 50) / 100.0 }
    val splitValid = !splitting || (person.isNotBlank() && myShare != null && parsed != null && myShare <= parsed + 0.005)
    val currency = data.currency
    val quick = remember(type) { if (initial == null) frequentEntries(data, type, LocalDate.now()) else emptyList() }
    val focus = remember { FocusRequester() }
    val today = LocalDate.now()

    LaunchedEffect(Unit) {
        if (initial == null) {
            delay(300)
            runCatching { focus.requestFocus() }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                when {
                    initial != null -> "Edit transaction"
                    type == TxType.EXPENSE -> "New expense"
                    else -> "New income"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            TypeToggle(type) { t ->
                type = t
                if (Categories.of(categoryId).type != t) categoryId = defaultCategory(t)
            }

            if (quick.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { q ->
                        FilterChip(
                            selected = categoryId == q.categoryId && note.trim() == q.note && parsed == q.amount,
                            onClick = {
                                categoryId = q.categoryId
                                note = q.note
                                amount = trimNum(q.amount)
                            },
                            label = { Text("${q.category.emoji} ${q.note.ifBlank { q.category.label }} · ${Money.format(q.amount, currency)}") },
                        )
                    }
                }
            }

            AmountField(amount, { amount = it }, currency, big = true, focusRequester = focus)

            Text("Category", style = MaterialTheme.typography.labelLarge)
            CategoryPicker(Categories.forType(type), categoryId) { categoryId = it }
            if (type == TxType.EXPENSE) {
                budgetPreview(data, categoryId, date, (if (splitting) myShare else parsed) ?: 0.0, initial?.id)?.let { BudgetLine(it, currency) }
            }

            Text("Date", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = date == today, onClick = { date = today }, label = { Text("Today") })
                FilterChip(selected = date == today.minusDays(1), onClick = { date = today.minusDays(1) }, label = { Text("Yesterday") })
                val other = date != today && date != today.minusDays(1)
                FilterChip(
                    selected = other,
                    onClick = { showPicker = true },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
                    label = { Text(if (other) date.friendly() else "Pick") },
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text(if (type == TxType.EXPENSE) "e.g. Lunch with Sam" else "e.g. October salary") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (canSplit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Split with someone", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Only your share counts as spending. The rest goes to Debts in Plan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = split, onCheckedChange = { split = it })
                }
                if (split) {
                    PersonField(person, { person = it }, remember { knownPeople(data) }, label = "Split with")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = iPaid, onClick = { iPaid = true }, label = { Text("I paid") })
                        FilterChip(selected = !iPaid, onClick = { iPaid = false }, label = { Text("${person.trim().ifBlank { "They" }} paid") })
                        FilterChip(selected = shareText == null, onClick = { shareText = null }, label = { Text("Half each") })
                    }
                    AmountField(shareText ?: myShare?.let(::trimNum) ?: "", { shareText = it }, currency, label = "My share")
                    if (parsed != null && myShare != null && myShare <= parsed + 0.005) {
                        val name = person.trim().ifBlank { "They" }
                        Text(
                            if (iPaid) "$name will owe you ${Money.format(parsed - myShare, currency)}." else "You'll owe $name ${Money.format(myShare, currency)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val tx = Transaction(initial?.id ?: IdGen.next(), parsed!!, type, categoryId, note.trim(), date)
                    if (splitting) onSaveSplit(tx, person.trim(), myShare!!.coerceAtMost(tx.amount), iPaid) else onSave(tx)
                },
                enabled = parsed != null && splitValid,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == TxType.EXPENSE) FinColors.Expense else FinColors.Income,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    when {
                        initial != null -> "Save changes"
                        splitting -> "Save my share" + (myShare?.let { " · ${Money.format(it, currency)}" } ?: "")
                        else -> "Save ${if (type == TxType.EXPENSE) "expense" else "income"}"
                    },
                )
            }
            if (initial != null) {
                TextButton(onClick = { onDelete(initial) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete transaction", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state)
        }
    }
}

/** The category's monthly budget, with what this entry would do to it. */
@Composable
private fun BudgetLine(p: BudgetPreview, currency: String) {
    val level = alertLevel(p.spentAfter, p.limit)
    val color = when (level) {
        2 -> FinColors.Expense
        1 -> FinColors.Warn
        else -> FinColors.Income
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ProgressBar(p.ratioAfter.toFloat(), color = color)
        val fmt = { v: Double -> Money.format(v, currency) }
        val text = when {
            p.spentAfter == p.spentBefore -> "Budget: ${fmt(p.spentBefore)} of ${fmt(p.limit)} used, ${fmt(p.limit - p.spentBefore)} left"
            level == 2 -> "This puts ${p.category.label} ${fmt(-p.leftAfter)} over its ${fmt(p.limit)} budget"
            else -> "After this: ${pct(p.ratioAfter)} of the ${fmt(p.limit)} budget, ${fmt(p.leftAfter)} left"
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (level > 0) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
