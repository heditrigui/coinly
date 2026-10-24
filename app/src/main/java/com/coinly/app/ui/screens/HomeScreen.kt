package com.coinly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinly.app.data.AppData
import com.coinly.app.data.Money
import com.coinly.app.data.MonthStats
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.pct
import com.coinly.app.ui.components.AnimatedAmount
import com.coinly.app.ui.components.AppCard
import com.coinly.app.ui.components.CardTitle
import com.coinly.app.ui.components.DonutChart
import com.coinly.app.ui.components.IncomeExpenseBars
import com.coinly.app.ui.components.LegendDot
import com.coinly.app.ui.components.MonthSwitcher
import com.coinly.app.ui.components.ProgressBar
import com.coinly.app.ui.components.ScoreGauge
import com.coinly.app.ui.components.SectionHeader
import com.coinly.app.ui.components.Slice
import com.coinly.app.ui.components.TransactionRow
import com.coinly.app.ui.components.scoreColor
import com.coinly.app.ui.components.scoreLabel
import com.coinly.app.ui.theme.FinColors
import java.time.LocalTime

@Composable
fun HomeScreen(
    data: AppData,
    stats: MonthStats,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onAdd: (TxType) -> Unit,
    onEdit: (Transaction) -> Unit,
    onSetupSalary: () -> Unit,
    onLoadDemo: () -> Unit,
    onSeeAll: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    val cur = data.currency
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Greeting(isDark, onToggleTheme, onSettings) }
        item { MonthSwitcher(stats.month, onPrev, onNext) }
        item { HeroCard(stats, cur, onAdd) }

        if (data.transactions.isEmpty()) {
            item { WelcomeCard(onSetupSalary, onLoadDemo) }
        } else {
            item { HealthCard(stats, cur, onOpenInsights) }
            item { BreakdownCard(stats, cur) }
            item { CashFlowCard(stats) }
            if (stats.budgets.isNotEmpty()) item { BudgetsPreview(stats, cur, onOpenPlan) }
            item { SectionHeader("Recent activity", "See all", onSeeAll) }
            if (stats.recent.isEmpty()) {
                item {
                    Text(
                        "Nothing logged this month yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(stats.recent, key = { it.id }) { tx -> TransactionRow(tx, cur) { onEdit(tx) } }
        }
    }
}

@Composable
private fun Greeting(isDark: Boolean, onToggleTheme: () -> Unit, onSettings: () -> Unit) {
    val hour = LocalTime.now().hour
    val hello = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$hello 👋", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Your money", style = MaterialTheme.typography.headlineMedium)
        }
        IconButton(onClick = onToggleTheme) {
            Icon(if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, if (isDark) "Switch to light mode" else "Switch to dark mode")
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
    }
}

@Composable
private fun HeroCard(stats: MonthStats, cur: String, onAdd: (TxType) -> Unit) {
    val white = Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(FinColors.HeroStart, FinColors.HeroEnd)))
            .padding(20.dp),
    ) {
        Text("Total balance", color = white.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        AnimatedAmount(stats.balanceAllTime, cur, MaterialTheme.typography.displaySmall, white)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat("↑ Income", Money.format(stats.income, cur), Modifier.weight(1f))
            HeroStat("↓ Spent", Money.format(stats.expense, cur), Modifier.weight(1f))
        }
        stats.safeToSpendPerDay?.let { safe ->
            Spacer(Modifier.height(12.dp))
            Text(
                "💡 You can spend about ${Money.format(safe, cur)} a day for the next ${stats.daysLeft} days.",
                color = white,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroButton("＋ Income", Modifier.weight(1f)) { onAdd(TxType.INCOME) }
            HeroButton("－ Expense", Modifier.weight(1f)) { onAdd(TxType.EXPENSE) }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(12.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = FinColors.HeroEnd,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun WelcomeCard(onSetupSalary: () -> Unit, onLoadDemo: () -> Unit) {
    AppCard {
        Text("👋 Welcome to Coinly", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Start by adding your salary — Coinly will log it automatically every month. Then add what you spend as you go.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSetupSalary, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("💼 Set up my monthly salary") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLoadDemo, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("✨ Explore with demo data") }
    }
}

@Composable
private fun HealthCard(stats: MonthStats, cur: String, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreGauge(stats.healthScore, Modifier.size(84.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Financial health", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(scoreLabel(stats.healthScore), style = MaterialTheme.typography.titleLarge, color = scoreColor(stats.healthScore))
                Spacer(Modifier.height(2.dp))
                val parts = buildList {
                    if (stats.income > 0) add("Saving ${pct(stats.savingsRate.coerceAtLeast(0.0))}")
                    stats.projectedExpense?.let { add("On pace for ${Money.compact(it, cur)}") }
                }
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Tap for insights →", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BreakdownCard(stats: MonthStats, cur: String) {
    AppCard {
        CardTitle("Where your money went")
        Spacer(Modifier.height(16.dp))
        if (stats.byCategory.isEmpty()) {
            Text("No expenses this month 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        val top = stats.byCategory.take(5)
        val rest = stats.byCategory.drop(5).sumOf { it.amount }
        val slices = top.map { Slice(Color(it.category.color), it.amount.toFloat()) } +
            (if (rest > 0) listOf(Slice(MaterialTheme.colorScheme.outline, rest.toFloat())) else emptyList())
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, Modifier.size(132.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.compact(stats.expense, cur), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                top.forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it.category.emoji, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(it.category.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(pct(it.share.toDouble()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (rest > 0) Text("+ ${stats.byCategory.size - 5} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CashFlowCard(stats: MonthStats) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { CardTitle("Cash flow", "Last 6 months") }
            LegendDot(FinColors.Income, "In")
            Spacer(Modifier.width(12.dp))
            LegendDot(FinColors.Expense, "Out")
        }
        Spacer(Modifier.height(16.dp))
        IncomeExpenseBars(stats.history, stats.month)
    }
}

@Composable
private fun BudgetsPreview(stats: MonthStats, cur: String, onOpenPlan: () -> Unit) {
    AppCard(onClick = onOpenPlan) {
        CardTitle("Budgets", "This month")
        Spacer(Modifier.height(12.dp))
        stats.budgets.take(3).forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${b.category.emoji} ${b.category.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${Money.compact(b.spent, cur)} / ${Money.compact(b.budget.limit, cur)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(b.ratio, warnColors = true)
            Spacer(Modifier.height(12.dp))
        }
    }
}
