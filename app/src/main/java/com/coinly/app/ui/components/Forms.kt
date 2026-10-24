package com.coinly.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinly.app.data.Budget
import com.coinly.app.data.Categories
import com.coinly.app.data.Category
import com.coinly.app.data.Goal
import com.coinly.app.data.IdGen
import com.coinly.app.data.Recurring
import com.coinly.app.data.TxType
import com.coinly.app.data.full
import com.coinly.app.data.ordinal
import com.coinly.app.data.parseAmount
import com.coinly.app.data.trimNum
import com.coinly.app.ui.theme.FinColors
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

private val amountPattern = Regex("^\\d{0,9}([.,]\\d{0,2})?$")

@Composable
fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    currency: String,
    modifier: Modifier = Modifier,
    label: String = "Amount",
    big: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (amountPattern.matches(it)) onChange(it) },
        label = { Text(label) },
        prefix = { Text("$currency ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    )
}

@Composable
fun TypeToggle(type: TxType, onChange: (TxType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income").forEach { (t, label) ->
            val sel = t == type
            val bg by animateColorAsState(
                if (sel) (if (t == TxType.EXPENSE) FinColors.Expense else FinColors.Income) else Color.Transparent,
                label = "toggle",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onChange(t) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(options: List<Category>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { c ->
            val sel = c.id == selected
            val color = Color(c.color)
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .clip(shape)
                    .background(if (sel) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, if (sel) color else Color.Transparent, shape)
                    .clickable { onSelect(c.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.emoji, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(c.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun DialogButtons(onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    Row {
        if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}

@Composable
fun BudgetDialog(
    initial: Budget?,
    taken: Set<String>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Budget) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val options = Categories.expense.filter { it.id !in taken || it.id == initial?.categoryId }
    var cat by remember { mutableStateOf(initial?.categoryId ?: options.firstOrNull()?.id ?: "other") }
    var amount by remember { mutableStateOf(initial?.limit?.let(::trimNum) ?: "") }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New monthly budget" else "Edit budget") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (initial == null) {
                    CategoryPicker(options, cat) { cat = it }
                } else {
                    Text("${Categories.of(cat).emoji} ${Categories.of(cat).label}", style = MaterialTheme.typography.titleMedium)
                }
                AmountField(amount, { amount = it }, currency, label = "Monthly limit")
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { onSave(Budget(cat, parsed!!)) }) { Text("Save") } },
        dismissButton = { DialogButtons(onDismiss, onDelete) },
    )
}

private val goalEmojis = listOf("🎯", "🏖️", "🚗", "🏠", "💻", "📱", "💍", "🎓", "🛡️", "✈️", "🎁", "🐶")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalDialog(
    initial: Goal?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🎯") }
    var target by remember { mutableStateOf(initial?.target?.let(::trimNum) ?: "") }
    var saved by remember { mutableStateOf(initial?.saved?.takeIf { it > 0 }?.let(::trimNum) ?: "") }
    var deadline by remember { mutableStateOf(initial?.deadline) }
    val targetValue = parseAmount(target)
    val today = LocalDate.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New savings goal" else "Edit goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    goalEmojis.forEach { e ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = e }
                                .padding(6.dp),
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                AmountField(target, { target = it }, currency, label = "Target")
                AmountField(saved, { saved = it }, currency, label = "Already saved (optional)")
                Text("Target date", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = deadline == null, onClick = { deadline = null }, label = { Text("None") })
                    listOf(3L to "3 mo", 6L to "6 mo", 12L to "1 yr", 24L to "2 yrs").forEach { (m, l) ->
                        val d = today.plusMonths(m)
                        FilterChip(selected = deadline != null && YearMonth.from(deadline) == YearMonth.from(d), onClick = { deadline = d }, label = { Text(l) })
                    }
                }
                deadline?.let { Text("By ${it.full()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && targetValue != null,
                onClick = {
                    onSave(
                        Goal(
                            id = initial?.id ?: IdGen.next(),
                            name = name.trim(),
                            emoji = emoji,
                            target = targetValue!!,
                            saved = parseAmount(saved) ?: 0.0,
                            deadline = deadline,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { DialogButtons(onDismiss, onDelete) },
    )
}

@Composable
fun AmountDialog(title: String, subtitle: String?, currency: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountField(amount, { amount = it }, currency)
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { onConfirm(parsed!!) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RecurringDialog(
    initial: Recurring?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Recurring) -> Unit,
    onDelete: (() -> Unit)?,
    presetType: TxType = TxType.EXPENSE,
    presetCategory: String? = null,
    title: String? = null,
) {
    var type by remember { mutableStateOf(initial?.type ?: presetType) }
    var cat by remember { mutableStateOf(initial?.categoryId ?: presetCategory ?: Categories.forType(type).first().id) }
    var amount by remember { mutableStateOf(initial?.amount?.let(::trimNum) ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var day by remember { mutableFloatStateOf((initial?.dayOfMonth ?: 1).toFloat()) }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: if (initial == null) "New recurring entry" else "Edit recurring entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TypeToggle(type) { t ->
                    type = t
                    if (Categories.of(cat).type != t) cat = Categories.forType(t).first().id
                }
                AmountField(amount, { amount = it }, currency)
                CategoryPicker(Categories.forType(type), cat) { cat = it }
                OutlinedTextField(note, { note = it }, label = { Text("Name (e.g. Rent, Salary)") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                Text("Every month on the ${ordinal(day.roundToInt())}", style = MaterialTheme.typography.labelLarge)
                Slider(value = day, onValueChange = { day = it }, valueRange = 1f..31f)
                Text(
                    "Coinly adds it automatically on that day. Short months use their last day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    onSave(
                        Recurring(
                            id = initial?.id ?: IdGen.next(),
                            amount = parsed!!,
                            type = type,
                            categoryId = cat,
                            note = note.trim().ifBlank { Categories.of(cat).label },
                            dayOfMonth = day.roundToInt(),
                            // New entries start with this month, so a salary day that already passed is booked now.
                            lastApplied = initial?.lastApplied ?: YearMonth.now().minusMonths(1).toString(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { DialogButtons(onDismiss, onDelete) },
    )
}
