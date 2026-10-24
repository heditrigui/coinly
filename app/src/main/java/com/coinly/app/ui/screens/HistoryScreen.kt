package com.coinly.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinly.app.data.AppData
import com.coinly.app.data.Money
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.friendly
import com.coinly.app.ui.components.EmptyState
import com.coinly.app.ui.components.TransactionRow
import com.coinly.app.ui.theme.FinColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(data: AppData, onEdit: (Transaction) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<TxType?>(null) }
    val cur = data.currency

    val filtered = remember(data.transactions, query, filter) {
        val q = query.trim()
        data.transactions
            .filter { t ->
                (filter == null || t.type == filter) &&
                    (q.isEmpty() || t.note.contains(q, ignoreCase = true) || t.category.label.contains(q, ignoreCase = true))
            }
            .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
    }
    val grouped = remember(filtered) { filtered.groupBy { it.date } }
    val totalIn = filtered.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val totalOut = filtered.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("History", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search notes or categories") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                FilterChip(selected = filter == TxType.EXPENSE, onClick = { filter = TxType.EXPENSE }, label = { Text("Expenses") })
                FilterChip(selected = filter == TxType.INCOME, onClick = { filter = TxType.INCOME }, label = { Text("Income") })
            }
            if (filtered.isNotEmpty()) {
                Text(
                    "${filtered.size} entries · in ${Money.format(totalIn, cur)} · out ${Money.format(totalOut, cur)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyState(
                "🔍",
                if (data.transactions.isEmpty()) "No transactions yet" else "Nothing matches",
                if (data.transactions.isEmpty()) "Tap Add to log your first income or expense." else "Try a different search or filter.",
            )
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp)) {
            grouped.forEach { (date, list) ->
                stickyHeader(key = "h$date") {
                    val net = list.sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = 12.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(date.friendly(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            (if (net >= 0) "+" else "") + Money.format(net, cur),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (net >= 0) FinColors.Income else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(list, key = { it.id }) { tx -> TransactionRow(tx, cur) { onEdit(tx) } }
            }
        }
    }
}
