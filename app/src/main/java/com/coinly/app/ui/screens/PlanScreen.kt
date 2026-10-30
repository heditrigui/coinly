package com.coinly.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinly.app.FinanceViewModel
import com.coinly.app.data.AppData
import com.coinly.app.data.Budget
import com.coinly.app.data.BudgetStatus
import com.coinly.app.data.Debt
import com.coinly.app.data.Goal
import com.coinly.app.data.Money
import com.coinly.app.data.MonthStats
import com.coinly.app.data.PersonBalance
import com.coinly.app.data.Recurring
import com.coinly.app.data.TxType
import com.coinly.app.data.balances
import com.coinly.app.data.debtTotals
import com.coinly.app.data.friendly
import com.coinly.app.data.knownPeople
import com.coinly.app.data.full
import com.coinly.app.data.ordinal
import com.coinly.app.data.pct
import com.coinly.app.ui.components.AmountDialog
import com.coinly.app.ui.components.AppCard
import com.coinly.app.ui.components.BudgetDialog
import com.coinly.app.ui.components.CategoryBadge
import com.coinly.app.ui.components.DebtDialog
import com.coinly.app.ui.components.GoalDialog
import com.coinly.app.ui.components.ProgressBar
import com.coinly.app.ui.components.RecurringDialog
import com.coinly.app.ui.components.SectionHeader
import com.coinly.app.ui.components.SettleDialog
import com.coinly.app.ui.theme.FinColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private sealed interface PlanDialog {
    data class EditBudget(val budget: Budget?) : PlanDialog
    data class EditGoal(val goal: Goal?) : PlanDialog
    data class Contribute(val goal: Goal) : PlanDialog
    data class EditRecurring(val recurring: Recurring?) : PlanDialog
    data class EditDebt(val debt: Debt?) : PlanDialog
    data class Settle(val balance: PersonBalance) : PlanDialog
}

@Composable
fun PlanScreen(vm: FinanceViewModel, data: AppData, current: MonthStats, notify: (String) -> Unit) {
    val cur = data.currency
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }
    val close = { dialog = null }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Plan", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Budgets, savings goals, the money that comes and goes every month, and who owes what.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Budgets
        item { SectionHeader("🎯 Monthly budgets", "+ Add") { dialog = PlanDialog.EditBudget(null) } }
        if (current.budgets.isEmpty()) {
            item { Hint("Set a monthly limit for a category and Coinly will warn you before you go over.") }
        } else {
            item { BudgetSummary(current.budgets, cur) }
            items(current.budgets, key = { "b" + it.budget.categoryId }) { b ->
                BudgetCard(b, cur) { dialog = PlanDialog.EditBudget(b.budget) }
            }
        }

        // Goals
        item { Column { Spacer(Modifier.height(8.dp)); SectionHeader("🐷 Savings goals", "+ Add") { dialog = PlanDialog.EditGoal(null) } } }
        if (data.goals.isEmpty()) {
            item { Hint("Saving for a trip, a car or a safety net? Create a goal and track your progress.") }
        } else {
            items(data.goals, key = { "g" + it.id }) { g ->
                GoalCard(g, cur, onEdit = { dialog = PlanDialog.EditGoal(g) }, onAdd = { dialog = PlanDialog.Contribute(g) })
            }
        }

        // Recurring
        item { Column { Spacer(Modifier.height(8.dp)); SectionHeader("🔁 Recurring", "+ Add") { dialog = PlanDialog.EditRecurring(null) } } }
        if (data.recurring.isEmpty()) {
            item { Hint("Add your salary, rent or subscriptions once — they're logged automatically every month.") }
        } else {
            item { RecurringSummary(data.recurring, cur) }
            items(data.recurring.sortedBy { it.dayOfMonth }, key = { "r" + it.id }) { r ->
                RecurringRow(r, cur) { dialog = PlanDialog.EditRecurring(r) }
            }
        }

        // Debts and shared expenses
        item { Column { Spacer(Modifier.height(8.dp)); SectionHeader("🤝 Debts & shared", "+ Add") { dialog = PlanDialog.EditDebt(null) } } }
        val people = balances(data)
        if (people.isEmpty()) {
            item { Hint("Lent money or split a bill? Keep track of who owes what, then settle up. Split a bill right from a new expense.") }
        } else {
            item { DebtSummary(data, cur) }
            items(people, key = { "p" + it.person.lowercase() }) { p ->
                PersonCard(p, cur, onSettle = { dialog = PlanDialog.Settle(p) }, onEdit = { dialog = PlanDialog.EditDebt(it) })
            }
        }
    }

    when (val d = dialog) {
        is PlanDialog.EditBudget -> BudgetDialog(
            initial = d.budget,
            taken = data.budgets.map { it.categoryId }.toSet(),
            currency = cur,
            onDismiss = close,
            onSave = { vm.saveBudget(it); close(); notify("Budget saved") },
            onDelete = d.budget?.let { b -> { vm.deleteBudget(b.categoryId); close() } },
        )
        is PlanDialog.EditGoal -> GoalDialog(
            initial = d.goal,
            currency = cur,
            onDismiss = close,
            onSave = { vm.saveGoal(it); close(); notify("${it.emoji} Goal saved") },
            onDelete = d.goal?.let { g -> { vm.deleteGoal(g.id); close() } },
        )
        is PlanDialog.Contribute -> AmountDialog(
            title = "Add to ${d.goal.emoji} ${d.goal.name}",
            subtitle = "This is logged as a Savings expense, so your balance shows money you can still spend.",
            currency = cur,
            onDismiss = close,
            onConfirm = { amount ->
                vm.contribute(d.goal, amount)
                close()
                val reached = d.goal.saved < d.goal.target && d.goal.saved + amount >= d.goal.target
                notify(if (reached) "🎉 Goal reached! Amazing work." else "Added ${Money.format(amount, cur)} to ${d.goal.name}")
            },
        )
        is PlanDialog.EditRecurring -> RecurringDialog(
            initial = d.recurring,
            currency = cur,
            onDismiss = close,
            onSave = { vm.saveRecurring(it); close(); notify("🔁 Recurring entry saved") },
            onDelete = d.recurring?.let { r -> { vm.deleteRecurring(r.id); close() } },
        )
        is PlanDialog.EditDebt -> DebtDialog(
            initial = d.debt,
            people = knownPeople(data),
            currency = cur,
            onDismiss = close,
            onSave = { vm.saveDebt(it); close(); notify("🤝 Saved") },
            onDelete = d.debt?.let { b -> { vm.deleteDebt(b.id); close() } },
        )
        is PlanDialog.Settle -> SettleDialog(
            person = d.balance.person,
            net = d.balance.net,
            currency = cur,
            onDismiss = close,
            onConfirm = { paid ->
                vm.settle(d.balance.person, paid)
                close()
                notify(if (paid == null) "✅ Settled up with ${d.balance.person}" else "Payment of ${Money.format(paid, cur)} recorded")
            },
        )
        null -> Unit
    }
}

@Composable
private fun Hint(text: String) {
    AppCard(padding = 16.dp) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BudgetSummary(budgets: List<BudgetStatus>, cur: String) {
    val limit = budgets.sumOf { it.budget.limit }
    val spent = budgets.sumOf { it.spent }
    val today = LocalDate.now()
    val monthProgress = today.dayOfMonth / today.lengthOfMonth().toFloat()
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Budgeted this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${Money.format(spent, cur)} of ${Money.format(limit, cur)}", style = MaterialTheme.typography.titleMedium)
            }
            Text(pct(if (limit > 0) spent / limit else 0.0), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(if (limit > 0) (spent / limit).toFloat() else 0f, warnColors = true, height = 10.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            "${pct(monthProgress.toDouble())} of the month has passed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetCard(b: BudgetStatus, cur: String, onClick: () -> Unit) {
    AppCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(b.category, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.category.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (b.remaining >= 0) "${Money.format(b.remaining, cur)} left" else "${Money.format(-b.remaining, cur)} over budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (b.remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant else FinColors.Expense,
                )
            }
            Text(
                "${Money.compact(b.spent, cur)} / ${Money.compact(b.budget.limit, cur)}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(b.ratio, warnColors = true)
    }
}

@Composable
private fun GoalCard(g: Goal, cur: String, onEdit: () -> Unit, onAdd: () -> Unit) {
    val ratio = if (g.target > 0) (g.saved / g.target).toFloat() else 0f
    val remaining = (g.target - g.saved).coerceAtLeast(0.0)
    AppCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.emoji, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium)
                Text("${Money.format(g.saved, cur)} of ${Money.format(g.target, cur)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(pct(ratio.toDouble().coerceAtMost(1.0)), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        ProgressBar(ratio, height = 10.dp)
        Spacer(Modifier.height(10.dp))
        val hint = when {
            remaining <= 0 -> "🎉 Goal reached!"
            g.deadline != null -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(g.deadline)).coerceAtLeast(1)
                "Save ${Money.format(remaining / months, cur)}/month to make it by ${g.deadline.full()}"
            }
            else -> "${Money.format(remaining, cur)} to go"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (remaining > 0) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onAdd, shape = RoundedCornerShape(12.dp)) { Text("+ Add money") }
            }
        }
    }
}

@Composable
private fun RecurringSummary(items: List<Recurring>, cur: String) {
    val inc = items.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val out = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    AppCard(padding = 16.dp) {
        Text("Every month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text("+${Money.format(inc, cur)}", color = FinColors.Income, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("−${Money.format(out, cur)}", color = FinColors.Expense, style = MaterialTheme.typography.titleMedium)
        }
        if (inc > 0) {
            Text(
                "Fixed costs take ${pct(out / inc)} of your recurring income, leaving ${Money.format(inc - out, cur)} free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecurringRow(r: Recurring, cur: String, onClick: () -> Unit) {
    val cat = com.coinly.app.data.Categories.of(r.categoryId)
    AppCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(cat, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.note.ifBlank { cat.label }, style = MaterialTheme.typography.titleSmall)
                val reminder = r.remindDaysBefore?.let { if (it == 1) " · 🔔 1 day before" else " · 🔔 $it days before" } ?: ""
                Text("Every month on the ${ordinal(r.dayOfMonth)}$reminder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val income = r.type == TxType.INCOME
            Text(
                (if (income) "+" else "−") + Money.format(r.amount, cur),
                color = if (income) FinColors.Income else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DebtSummary(data: AppData, cur: String) {
    val (owedToMe, iOwe) = debtTotals(data)
    AppCard(padding = 16.dp) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Owed to you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(owedToMe, cur), color = FinColors.Income, style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("You owe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(iOwe, cur), color = FinColors.Expense, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PersonCard(p: PersonBalance, cur: String, onSettle: () -> Unit, onEdit: (Debt) -> Unit) {
    AppCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧑", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.person, style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        p.net > 0.005 -> "Owes you ${Money.format(p.net, cur)}"
                        p.net < -0.005 -> "You owe ${Money.format(-p.net, cur)}"
                        else -> "All square"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        p.net > 0.005 -> FinColors.Income
                        p.net < -0.005 -> FinColors.Expense
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            FilledTonalButton(onClick = onSettle, shape = RoundedCornerShape(12.dp)) { Text("Settle up") }
        }
        Spacer(Modifier.height(8.dp))
        p.open.take(4).forEach { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(b) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${b.note.ifBlank { if (b.theyOwe) "Lent" else "Borrowed" }} · ${b.date.friendly()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    (if (b.theyOwe) "+" else "−") + Money.format(b.amount, cur),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (b.theyOwe) FinColors.Income else FinColors.Expense,
                )
            }
        }
        if (p.open.size > 4) {
            Text("+ ${p.open.size - 4} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
