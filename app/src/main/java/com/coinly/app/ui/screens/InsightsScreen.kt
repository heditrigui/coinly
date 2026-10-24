package com.coinly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinly.app.data.CategorySpend
import com.coinly.app.data.Insight
import com.coinly.app.data.Money
import com.coinly.app.data.MonthStats
import com.coinly.app.data.pct
import com.coinly.app.ui.components.AppCard
import com.coinly.app.ui.components.CardTitle
import com.coinly.app.ui.components.CumulativeChart
import com.coinly.app.ui.components.EmptyState
import com.coinly.app.ui.components.LegendDot
import com.coinly.app.ui.components.MonthSwitcher
import com.coinly.app.ui.components.ProgressBar
import com.coinly.app.ui.components.ScoreGauge
import com.coinly.app.ui.components.SpendingCalendar
import com.coinly.app.ui.components.StatTile
import com.coinly.app.ui.components.scoreColor
import com.coinly.app.ui.components.scoreLabel
import com.coinly.app.ui.components.toneColor
import com.coinly.app.ui.theme.FinColors

@Composable
fun InsightsScreen(stats: MonthStats, currency: String, onPrev: () -> Unit, onNext: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Insights", style = MaterialTheme.typography.headlineMedium) }
        item { MonthSwitcher(stats.month, onPrev, onNext) }

        if (stats.txCount == 0) {
            item { EmptyState("📊", "No data for this month", "Add some transactions and Coinly will start spotting patterns for you.") }
            return@LazyColumn
        }

        item { ScoreCard(stats, currency) }

        if (stats.insights.isNotEmpty()) {
            item { Text("Smart tips", style = MaterialTheme.typography.titleLarge) }
            items(stats.insights) { InsightRow(it) }
        }

        item { PaceCard(stats, currency) }

        item {
            AppCard {
                CardTitle("Spending calendar", "Darker days = more spending")
                Spacer(Modifier.height(12.dp))
                SpendingCalendar(stats.month, stats.dailyExpense, currency)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Avg per day", Money.format(stats.avgDailyExpense, currency), Modifier.weight(1f), "📅")
                StatTile("No-spend days", "${stats.noSpendDays}", Modifier.weight(1f), "🧘")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Transactions", "${stats.txCount}", Modifier.weight(1f), "🧾")
                StatTile("Net this month", Money.format(stats.net, currency), Modifier.weight(1f), if (stats.net >= 0) "🟢" else "🔴")
            }
        }

        if (stats.byCategory.isNotEmpty()) {
            item { CategoryList("Spending by category", stats.byCategory, currency, FinColors.Expense) }
        }
        if (stats.incomeByCategory.isNotEmpty()) {
            item { CategoryList("Income sources", stats.incomeByCategory, currency, FinColors.Income) }
        }
    }
}

@Composable
private fun ScoreCard(stats: MonthStats, currency: String) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreGauge(stats.healthScore, Modifier.size(110.dp), stroke = 12.dp, textStyle = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Health score", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(scoreLabel(stats.healthScore), style = MaterialTheme.typography.headlineSmall, color = scoreColor(stats.healthScore))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Based on your savings rate, budgets, spending trend and no-spend days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("Income", Money.compact(stats.income, currency), FinColors.Income, Modifier.weight(1f))
            MiniMetric("Spent", Money.compact(stats.expense, currency), FinColors.Expense, Modifier.weight(1f))
            MiniMetric("Saved", if (stats.income > 0) pct(stats.savingsRate) else "–", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun InsightRow(insight: Insight) {
    val tone = toneColor(insight.tone)
    AppCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tone.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text(insight.emoji, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(insight.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(insight.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PaceCard(stats: MonthStats, currency: String) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { CardTitle("Spending pace", "Running total through the month") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(MaterialTheme.colorScheme.primary, "This month")
            LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), "Last month")
        }
        Spacer(Modifier.height(12.dp))
        CumulativeChart(stats.cumulative, stats.cumulativePrev, stats.month.lengthOfMonth(), Modifier.fillMaxWidth().height(170.dp))
        val day = stats.cumulative.size
        val now = stats.cumulative.lastOrNull() ?: 0.0
        val then = stats.cumulativePrev.getOrNull(day - 1)
        if (day > 0 && then != null && then > 0) {
            Spacer(Modifier.height(12.dp))
            val diff = now - then
            Text(
                if (diff <= 0) "👍 ${Money.format(-diff, currency)} less than at this point last month."
                else "👀 ${Money.format(diff, currency)} more than at this point last month.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CategoryList(title: String, items: List<CategorySpend>, currency: String, accent: Color) {
    AppCard {
        CardTitle(title)
        Spacer(Modifier.height(12.dp))
        items.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.category.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(c.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(Money.format(c.amount, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    ProgressBar(c.share, color = Color(c.category.color).takeIf { accent == FinColors.Expense } ?: accent, height = 6.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
