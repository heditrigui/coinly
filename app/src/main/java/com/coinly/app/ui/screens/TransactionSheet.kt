package com.coinly.app.ui.screens

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
import com.coinly.app.data.Categories
import com.coinly.app.data.IdGen
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.friendly
import com.coinly.app.data.parseAmount
import com.coinly.app.data.trimNum
import com.coinly.app.ui.components.AmountField
import com.coinly.app.ui.components.CategoryPicker
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
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(initial?.type ?: startType) }
    var amount by remember { mutableStateOf(initial?.amount?.let(::trimNum) ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: Categories.forType(type).first().id) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var showPicker by remember { mutableStateOf(false) }
    val parsed = parseAmount(amount)
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
                if (Categories.of(categoryId).type != t) categoryId = Categories.forType(t).first().id
            }
            AmountField(amount, { amount = it }, currency, big = true, focusRequester = focus)

            Text("Category", style = MaterialTheme.typography.labelLarge)
            CategoryPicker(Categories.forType(type), categoryId) { categoryId = it }

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

            Button(
                onClick = { onSave(Transaction(initial?.id ?: IdGen.next(), parsed!!, type, categoryId, note.trim(), date)) },
                enabled = parsed != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == TxType.EXPENSE) FinColors.Expense else FinColors.Income,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(if (initial == null) "Save ${if (type == TxType.EXPENSE) "expense" else "income"}" else "Save changes")
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
