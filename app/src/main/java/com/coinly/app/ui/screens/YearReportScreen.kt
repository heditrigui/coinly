package com.coinly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinly.app.data.AppData
import com.coinly.app.data.Money
import com.coinly.app.data.YearReport
import com.coinly.app.data.computeYearReport
import com.coinly.app.data.shortLabel
import com.coinly.app.data.pct
import com.coinly.app.data.reportYears
import com.coinly.app.data.short
import com.coinly.app.ui.components.AppCard
import com.coinly.app.ui.components.CardTitle
import com.coinly.app.ui.components.EmptyState
import com.coinly.app.ui.components.IncomeExpenseBars
import com.coinly.app.ui.components.LegendDot
import com.coinly.app.ui.components.StatTile
import com.coinly.app.ui.theme.FinColors
import java.time.LocalDate
import kotlin.math.abs

/** "Your year in money": totals, top categories, best and worst months, and how it compares with last year. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearReportScreen(data: AppData, onBack: () -> Unit) {
    val today = remember { LocalDate.now() }
    val years = remember(data.transactions) { reportYears(data, today) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    val report = remember(data, year) { computeYearReport(data, year, today) }
    val cur = data.currency

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Year in money") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val i = years.indexOf(year)
                    IconButton(onClick = { year = years[i + 1] }, enabled = i in 0 until years.size - 1) { Icon(Icons.Outlined.ChevronLeft, "Previous year") }
                    Text(
                        if (report.isCurrentYear) "$year so far" else "$year",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { year = years[i - 1] }, enabled = i > 0) { Icon(Icons.Outlined.ChevronRight, "Next year") }
                }
            }

            if (report.txCount == 0) {
                item { EmptyState("📅", "Nothing in $year", "Transactions from this year will show up here, with your totals and highlights.") }
                return@LazyColumn
            }

            item { YearHero(report, cur) }
            report.previous?.let { item { Comparison(report, cur) } }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { CardTitle("Month by month") }
                        LegendDot(FinColors.Income, "In")
                        Spacer(Modifier.width(12.dp))
                        LegendDot(FinColors.Expense, "Out")
                    }
                    Spacer(Modifier.height(16.dp))
                    IncomeExpenseBars(report.months, selected = null)
                }
            }
            if (report.topCategories.isNotEmpty()) {
                item { CategoryList("Where it went", report.topCategories.take(6), cur, FinColors.Expense) }
            }
            item { Highlights(report, cur) }
        }
    }
}

@Composable
private fun YearHero(r: YearReport, cur: String) {
    val white = Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(FinColors.HeroStart, FinColors.HeroEnd)))
            .padding(20.dp),
    ) {
        Text(if (r.totals.net >= 0) "You kept" else "You spent more than you earned by", color = white.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
        Text(Money.format(abs(r.totals.net), cur), color = white, style = MaterialTheme.typography.displaySmall, maxLines = 1)
        if (r.totals.income > 0) {
            Text("That's ${pct(r.totals.savingsRate.coerceAtLeast(0.0))} of what came in.", color = white, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroTile("↑ Income", Money.compact(r.totals.income, cur), Modifier.weight(1f))
            HeroTile("↓ Spent", Money.compact(r.totals.expense, cur), Modifier.weight(1f))
            HeroTile("Per month", Money.compact(r.avgMonthlyExpense, cur), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(12.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun Comparison(r: YearReport, cur: String) {
    val prev = r.previous ?: return
    AppCard {
        CardTitle(
            "Compared with ${r.year - 1}",
            if (r.isCurrentYear) "Same period: Jan 1 – ${r.previousUntil!!.short()}" else "The whole year",
        )
        Spacer(Modifier.height(12.dp))
        ChangeRow("Income", r.totals.income, prev.income, cur, higherIsGood = true)
        ChangeRow("Spending", r.totals.expense, prev.expense, cur, higherIsGood = false)
        ChangeRow("Kept", r.totals.net, prev.net, cur, higherIsGood = true)
        if (r.changes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Biggest changes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            r.changes.forEach { c ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.category.emoji} ${c.category.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        (if (c.diff > 0) "+" else "−") + Money.format(abs(c.diff), cur),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (c.diff > 0) FinColors.Expense else FinColors.Income,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(label: String, now: Double, before: Double, cur: String, higherIsGood: Boolean) {
    val diff = now - before
    val good = if (higherIsGood) diff >= 0 else diff <= 0
    val change = when {
        abs(diff) < 0.005 -> "same"
        before > 0 -> (if (diff > 0) "▲ " else "▼ ") + pct(abs(diff) / before)
        else -> (if (diff > 0) "▲ " else "▼ ") + Money.compact(abs(diff), cur)
    }
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "${Money.compact(before, cur)} → ${Money.compact(now, cur)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(change, style = MaterialTheme.typography.labelLarge, color = if (good) FinColors.Income else FinColors.Expense)
    }
}

@Composable
private fun Highlights(r: YearReport, cur: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Highlights", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Biggest month", r.biggestSpendingMonth?.let { "${it.month.shortLabel()} · ${Money.compact(it.expense, cur)}" } ?: "–", Modifier.weight(1f), "🔥")
            StatTile("Best month", r.bestSavingMonth?.let { "${it.month.shortLabel()} · +${Money.compact(it.income - it.expense, cur)}" } ?: "–", Modifier.weight(1f), "🌱")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Largest expense", r.biggestExpense?.let { Money.format(it.amount, cur) } ?: "–", Modifier.weight(1f), "🧾")
            StatTile("No-spend days", "${r.noSpendDays}", Modifier.weight(1f), "🧘")
        }
        r.biggestExpense?.let {
            Text(
                "Largest expense: ${it.title} on ${it.date.short()}. ${r.txCount} transactions this year.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
