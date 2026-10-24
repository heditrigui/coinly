package com.coinly.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

data class CategorySpend(val category: Category, val amount: Double, val share: Float)

data class MonthBar(val month: YearMonth, val income: Double, val expense: Double)

data class BudgetStatus(val budget: Budget, val category: Category, val spent: Double) {
    val ratio: Float get() = if (budget.limit > 0) (spent / budget.limit).toFloat() else 0f
    val remaining: Double get() = budget.limit - spent
}

enum class Tone { GOOD, WARN, BAD, NEUTRAL }

data class Insight(val emoji: String, val title: String, val body: String, val tone: Tone)

data class MonthStats(
    val month: YearMonth,
    val isCurrentMonth: Boolean,
    val income: Double,
    val expense: Double,
    val balanceAllTime: Double,
    val byCategory: List<CategorySpend>,
    val incomeByCategory: List<CategorySpend>,
    val history: List<MonthBar>,
    /** Running total of spending per day, up to today for the current month. */
    val cumulative: List<Double>,
    val cumulativePrev: List<Double>,
    val dailyExpense: List<Double>,
    val budgets: List<BudgetStatus>,
    val safeToSpendPerDay: Double?,
    val daysLeft: Int,
    val projectedExpense: Double?,
    val avgDailyExpense: Double,
    val biggestExpense: Transaction?,
    val noSpendDays: Int,
    val noSpendStreak: Int,
    /** 0..100, or -1 when the month has no data. */
    val healthScore: Int,
    val insights: List<Insight>,
    val recent: List<Transaction>,
    val txCount: Int,
) {
    val net: Double get() = income - expense
    val savingsRate: Double get() = if (income > 0) net / income else 0.0
}

private fun runningSum(values: List<Double>): List<Double> {
    var s = 0.0
    return values.map { s += it; s }
}

private fun List<Transaction>.total() = sumOf { it.amount }
private fun List<Transaction>.expenses() = filter { it.type == TxType.EXPENSE }
private fun List<Transaction>.incomes() = filter { it.type == TxType.INCOME }

fun computeStats(d: AppData, month: YearMonth, today: LocalDate): MonthStats {
    fun fmt(v: Double) = Money.format(v, d.currency)

    val thisMonth = YearMonth.from(today)
    val isCurrent = month == thisMonth
    val byMonth = d.transactions.groupBy { YearMonth.from(it.date) }
    val inMonth = byMonth[month].orEmpty()
    val prevMonth = month.minusMonths(1)
    val inPrev = byMonth[prevMonth].orEmpty()

    val expenses = inMonth.expenses()
    val income = inMonth.incomes().total()
    val expense = expenses.total()
    val prevExpenses = inPrev.expenses()
    val prevExpense = prevExpenses.total()
    val balance = d.transactions.sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount }

    fun shares(list: List<Transaction>, total: Double) = list.groupBy { it.categoryId }
        .map { (id, l) -> val s = l.total(); CategorySpend(Categories.of(id), s, if (total > 0) (s / total).toFloat() else 0f) }
        .sortedByDescending { it.amount }

    val byCategory = shares(expenses, expense)
    val incomeByCategory = shares(inMonth.incomes(), income)

    val history = (5 downTo 0).map { i ->
        val m = month.minusMonths(i.toLong())
        val l = byMonth[m].orEmpty()
        MonthBar(m, l.incomes().total(), l.expenses().total())
    }

    val days = month.lengthOfMonth()
    val elapsed = when {
        isCurrent -> today.dayOfMonth
        month.isAfter(thisMonth) -> 0
        else -> days
    }
    val daily = DoubleArray(days).also { arr -> expenses.forEach { arr[it.date.dayOfMonth - 1] += it.amount } }
    val prevDaily = DoubleArray(prevMonth.lengthOfMonth()).also { arr -> prevExpenses.forEach { arr[it.date.dayOfMonth - 1] += it.amount } }
    val cumulative = runningSum(daily.take(elapsed))
    val cumulativePrev = runningSum(prevDaily.take(days))

    val noSpendDays = (0 until elapsed).count { daily[it] == 0.0 }
    val expenseDates = d.transactions.expenses().map { it.date }.toHashSet()
    val firstDate = d.transactions.minOfOrNull { it.date }
    var streak = 0
    if (firstDate != null) {
        var day = today
        while (!day.isBefore(firstDate) && day !in expenseDates && streak < 3650) {
            streak++
            day = day.minusDays(1)
        }
    }

    val daysLeft = if (isCurrent) days - today.dayOfMonth + 1 else 0
    val safe = if (isCurrent && income > 0) (income - expense).coerceAtLeast(0.0) / daysLeft else null
    val projected = if (isCurrent && today.dayOfMonth >= 3) expense / today.dayOfMonth * days else null
    val avgDaily = if (elapsed > 0) expense / elapsed else 0.0
    val biggest = expenses.maxByOrNull { it.amount }

    val spentByCat = expenses.groupBy { it.categoryId }.mapValues { (_, l) -> l.total() }
    val budgets = d.budgets
        .map { BudgetStatus(it, Categories.of(it.categoryId), spentByCat[it.categoryId] ?: 0.0) }
        .sortedByDescending { it.ratio }

    val savingsRate = if (income > 0) (income - expense) / income else 0.0
    val score = if (inMonth.isEmpty()) -1 else {
        val savingsPts = savingsRate.coerceIn(0.0, 0.3) / 0.3 * 40
        val budgetPts = if (budgets.isEmpty()) 15.0 else budgets.count { it.spent <= it.budget.limit }.toDouble() / budgets.size * 30
        val compare = projected ?: expense
        val trendPts = when {
            prevExpense <= 0 -> 10.0
            compare <= prevExpense * 0.9 -> 20.0
            compare <= prevExpense -> 15.0
            compare <= prevExpense * 1.15 -> 8.0
            else -> 0.0
        }
        val noSpendPts = noSpendDays.coerceAtMost(8) / 8.0 * 10
        (savingsPts + budgetPts + trendPts + noSpendPts).roundToInt().coerceIn(0, 100)
    }

    val insights = buildList {
        if (income > 0) {
            when {
                savingsRate >= 0.2 -> add(Insight("🎯", "You're saving ${pct(savingsRate)} of your income", "That beats the classic 20% rule. Keep it up!", Tone.GOOD))
                savingsRate >= 0 -> {
                    val tip = byCategory.firstOrNull()?.let { " Cutting ${it.category.label} by 10% would free up ${fmt(it.amount * 0.1)}." } ?: ""
                    add(Insight("🌱", "Savings rate: ${pct(savingsRate)}", "Aim for 20%.$tip", Tone.NEUTRAL))
                }
                else -> add(Insight("🚨", "You're spending more than you earn", "Expenses are ${fmt(expense - income)} above income this month.", Tone.BAD))
            }
        }
        if (projected != null && income > 0 && projected > income && expense <= income) {
            add(Insight("📉", "On pace to overspend", "At this rate you'll spend about ${fmt(projected)} by the end of the month.", Tone.WARN))
        }
        budgets.filter { it.spent > it.budget.limit }.forEach {
            add(Insight(it.category.emoji, "${it.category.label} is over budget", "Spent ${fmt(it.spent)} of ${fmt(it.budget.limit)} — ${fmt(-it.remaining)} over.", Tone.BAD))
        }
        budgets.filter { it.ratio in 0.8f..1f }.forEach {
            add(Insight("⚠️", "${it.category.label} budget almost gone", "${pct(it.ratio.toDouble())} used, ${fmt(it.remaining)} left.", Tone.WARN))
        }
        byCategory.firstOrNull()?.let {
            add(Insight(it.category.emoji, "${it.category.label} is your top expense", "${fmt(it.amount)} — ${pct(it.share.toDouble())} of everything you spent.", Tone.NEUTRAL))
        }

        // Compare categories against the same stretch of last month, so a half-finished month is fair.
        val comparablePrev = if (isCurrent) prevExpenses.filter { it.date.dayOfMonth <= today.dayOfMonth } else prevExpenses
        val prevByCat = comparablePrev.groupBy { it.categoryId }.mapValues { (_, l) -> l.total() }
        // Only flag changes that matter in money terms, not 500% jumps on a few coins.
        val minDiff = maxOf(25.0, expense * 0.05)
        val changes = (spentByCat.keys + prevByCat.keys).mapNotNull { id ->
            val v = spentByCat[id] ?: 0.0
            val p = prevByCat[id] ?: 0.0
            if (p < 10 || kotlin.math.abs(v - p) < minDiff) null else Triple(id, (v - p) / p, v - p)
        }
        changes.maxByOrNull { it.third }?.takeIf { it.second > 0.2 }?.let { (id, c, diff) ->
            val cat = Categories.of(id)
            val title = if (c >= 2) "${cat.label} spending tripled" else "${cat.label} up ${pct(c)}"
            add(Insight("📈", title, "${fmt(diff)} more than the same period last month.", Tone.WARN))
        }
        changes.minByOrNull { it.third }?.takeIf { it.second < -0.2 }?.let { (id, c, diff) ->
            val cat = Categories.of(id)
            add(Insight("👏", "${cat.label} down ${pct(-c)}", "You saved ${fmt(-diff)} vs the same period last month.", Tone.GOOD))
        }

        if (isCurrent && streak >= 2) add(Insight("🔥", "$streak-day no-spend streak", "Every day without spending is a small win.", Tone.GOOD))

        var weekendDays = 0; var weekdays = 0; var weekendSum = 0.0; var weekdaySum = 0.0
        for (i in 0 until elapsed) {
            val dow = month.atDay(i + 1).dayOfWeek
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) { weekendDays++; weekendSum += daily[i] } else { weekdays++; weekdaySum += daily[i] }
        }
        if (weekendDays >= 2 && weekdays >= 3 && weekdaySum > 0) {
            val we = weekendSum / weekendDays
            val wd = weekdaySum / weekdays
            if (we / wd >= 1.5) {
                add(Insight("🎉", "Weekends cost you ${String.format(Locale.US, "%.1f", we / wd)}× more", "${fmt(we)} per weekend day vs ${fmt(wd)} on weekdays.", Tone.NEUTRAL))
            }
        }

        spentByCat["subs"]?.takeIf { it > 0 }?.let {
            add(Insight("📺", "Subscriptions: ${fmt(it)} this month", "That's about ${fmt(it * 12)} a year. Still using all of them?", Tone.NEUTRAL))
        }
        val small = expenses.filter { it.amount < 10 }
        if (small.size >= 5) {
            add(Insight("☕", "${small.size} small purchases", "Under ${fmt(10.0)} each, but together they add up to ${fmt(small.total())}.", Tone.NEUTRAL))
        }
        biggest?.let {
            add(Insight("🧾", "Largest expense: ${fmt(it.amount)}", "${it.title} on ${it.date.short()}.", Tone.NEUTRAL))
        }
    }

    val recent = inMonth.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id }).take(6)

    return MonthStats(
        month = month,
        isCurrentMonth = isCurrent,
        income = income,
        expense = expense,
        balanceAllTime = balance,
        byCategory = byCategory,
        incomeByCategory = incomeByCategory,
        history = history,
        cumulative = cumulative,
        cumulativePrev = cumulativePrev,
        dailyExpense = daily.toList(),
        budgets = budgets,
        safeToSpendPerDay = safe,
        daysLeft = daysLeft,
        projectedExpense = projected,
        avgDailyExpense = avgDaily,
        biggestExpense = biggest,
        noSpendDays = noSpendDays,
        noSpendStreak = streak,
        healthScore = score,
        insights = insights,
        recent = recent,
        txCount = inMonth.size,
    )
}
