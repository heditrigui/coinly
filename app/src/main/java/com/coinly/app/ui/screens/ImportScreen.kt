package com.coinly.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinly.app.data.AppData
import com.coinly.app.data.Categories
import com.coinly.app.data.CsvMapping
import com.coinly.app.data.IdGen
import com.coinly.app.data.Money
import com.coinly.app.data.Statement
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.datePatterns
import com.coinly.app.data.detectDatePattern
import com.coinly.app.data.friendly
import com.coinly.app.data.full
import com.coinly.app.data.keywords
import com.coinly.app.data.parseBankDate
import com.coinly.app.data.prepareImport
import com.coinly.app.data.readStatement
import com.coinly.app.ui.components.AppCard
import com.coinly.app.ui.components.CategoryBadge
import com.coinly.app.ui.components.CategoryPicker
import com.coinly.app.ui.theme.FinColors

/** A starting point when Coinly can't guess the columns: the first three, read as date, description, amount. */
fun fallbackMapping(st: Statement): CsvMapping {
    val last = (st.columnCount - 1).coerceAtLeast(0)
    return CsvMapping(0, minOf(1, last), minOf(2, last), datePattern = detectDatePattern(st.column(0)) ?: datePatterns.first())
}

/**
 * Bank statement import: check (or pick) which columns hold the date, description and amount,
 * review the lines with their suggested categories, and import the ones that are new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    statement: Statement,
    initialMapping: CsvMapping,
    remembered: Boolean,
    data: AppData,
    onBack: () -> Unit,
    onImport: (List<Transaction>, CsvMapping) -> Unit,
) {
    val cur = data.currency
    var mapping by remember { mutableStateOf(initialMapping) }
    var editing by remember { mutableStateOf(!remembered) }
    val lines = remember(mapping) { readStatement(statement, mapping) }
    val candidates = remember(lines) { prepareImport(lines, data) }
    // The user's changes, by line: a different category, or ticked/unticked.
    val categories = remember(candidates) { mutableStateMapOf<Int, String>() }
    val ticked = remember(candidates) { mutableStateMapOf<Int, Boolean>() }
    var picking by remember { mutableStateOf<Int?>(null) }

    fun included(i: Int) = ticked[i] ?: !candidates[i].duplicate
    fun categoryOf(i: Int) = categories[i] ?: candidates[i].categoryId
    val count = candidates.indices.count { included(it) }
    val duplicates = candidates.count { it.duplicate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import statement") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val txs = candidates.indices.filter { included(it) }.map { i ->
                        val l = candidates[i].line
                        Transaction(IdGen.next(), l.amount, l.type, categoryOf(i), l.description, l.date)
                    }
                    onImport(txs, mapping)
                },
                enabled = count > 0,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .height(54.dp),
            ) { Text(if (count == 1) "Import 1 transaction" else "Import $count transactions") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (editing) {
                    ColumnsEditor(statement, mapping, onChange = { mapping = it }, onDone = { editing = false })
                } else {
                    ColumnsSummary(statement, mapping, remembered) { editing = true }
                }
            }
            item {
                val skipped = statement.rows.size - lines.size
                val parts = buildList {
                    add(if (candidates.size == 1) "1 line read" else "${candidates.size} lines read")
                    if (duplicates > 0) add("$duplicates already in Coinly")
                    if (skipped > 0) add("$skipped skipped (no date or amount)")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (candidates.isEmpty()) {
                item {
                    AppCard(padding = 16.dp) {
                        Text(
                            "No lines could be read with these columns. Check which columns hold the date and the amount, and the date format.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(candidates.indices.toList(), key = { it }) { i ->
                val c = candidates[i]
                val l = c.line
                val cat = Categories.of(categoryOf(i))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { ticked[i] = !included(i) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = included(i), onCheckedChange = { ticked[i] = it })
                    Column(Modifier.clickable { picking = i }) { CategoryBadge(cat, 38.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (c.duplicate) "${l.date.friendly()} · Already in Coinly" else "${l.date.friendly()} · ${cat.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val income = l.type == TxType.INCOME
                    Text(
                        (if (income) "+" else "−") + Money.format(l.amount, cur),
                        fontWeight = FontWeight.SemiBold,
                        color = if (income) FinColors.Income else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    picking?.let { i ->
        val l = candidates[i].line
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(l.description.ifBlank { "Category" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CategoryPicker(Categories.forType(l.type), categoryOf(i)) { picked ->
                        // Lines with the same description (ignoring card numbers and dates) get the same category.
                        val key = keywords(l.description)
                        candidates.forEachIndexed { j, other ->
                            if (j == i || (other.line.type == l.type && key.isNotEmpty() && keywords(other.line.description) == key)) categories[j] = picked
                        }
                        picking = null
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun ColumnsSummary(st: Statement, m: CsvMapping, remembered: Boolean, onChange: () -> Unit) {
    AppCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (remembered) "Same columns as last time" else "Columns", style = MaterialTheme.typography.titleSmall)
                val amount = if (m.inCol != null) "${st.columnName(m.amountCol)} (out) / ${st.columnName(m.inCol)} (in)" else st.columnName(m.amountCol)
                Text(
                    "Date: ${st.columnName(m.dateCol)} · Description: ${st.columnName(m.descCol)} · Amount: $amount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnsEditor(st: Statement, m: CsvMapping, onChange: (CsvMapping) -> Unit, onDone: () -> Unit) {
    // Header-less files show a sample value, so "Column 3" means something.
    fun label(i: Int) = if (st.header != null) st.columnName(i) else st.columnName(i) + (st.rows.firstOrNull()?.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { " · ${it.take(14)}" } ?: "")
    val columns = (0 until st.columnCount).toList()

    AppCard(padding = 16.dp) {
        Text("Which column is which?", style = MaterialTheme.typography.titleSmall)
        Text(
            "Coinly remembers this for the next file from the same bank.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Text("Date", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            columns.forEach { c ->
                FilterChip(selected = m.dateCol == c, onClick = {
                    onChange(m.copy(dateCol = c, datePattern = detectDatePattern(st.column(c)) ?: m.datePattern))
                }, label = { Text(label(c)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Description", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            columns.forEach { c -> FilterChip(selected = m.descCol == c, onClick = { onChange(m.copy(descCol = c)) }, label = { Text(label(c)) }) }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (m.inCol != null) "Money out" else "Amount", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            columns.forEach { c -> FilterChip(selected = m.amountCol == c, onClick = { onChange(m.copy(amountCol = c)) }, label = { Text(label(c)) }) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Money in (if your bank uses separate columns)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = m.inCol == null, onClick = { onChange(m.copy(inCol = null)) }, label = { Text("None") })
            columns.filter { it != m.amountCol }.forEach { c -> FilterChip(selected = m.inCol == c, onClick = { onChange(m.copy(inCol = c)) }, label = { Text(label(c)) }) }
        }
        if (m.inCol == null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Expenses are positive numbers", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Turn on if your bank writes spending without a minus sign.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = m.expensesPositive, onCheckedChange = { onChange(m.copy(expensesPositive = it)) })
            }
        }

        // Date layouts that read this column, best first, each with an example so day/month order is easy to check.
        val dates = st.column(m.dateCol)
        val sample = dates.firstOrNull { it.isNotBlank() }
        val options = datePatterns
            .map { p -> p to dates.count { parseBankDate(it, p) != null } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }
        if (sample != null && options.size > 1) {
            Spacer(Modifier.height(8.dp))
            Text("\"$sample\" means", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { p ->
                    val example = parseBankDate(sample, p)?.full() ?: p
                    FilterChip(selected = m.datePattern == p, onClick = { onChange(m.copy(datePattern = p)) }, label = { Text(example) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text("Done") }
    }
}
