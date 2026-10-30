package com.coinly.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class YearTotals(val income: Double, val expense: Double) {
    val net: Double get() = income - expense
    val savingsRate: Double get() = if (income > 0) net / income else 0.0
}

/** A category's spending this year next to last year's. */
data class CategoryChange(val category: Category, val now: Double, val before: Double) {
    val diff: Double get() = now - before
}

/** "Your year in money". For the current year, last year is cut at the same date so the comparison is fair. */
data class YearReport(
    val year: Int,
    val isCurrentYear: Boolean,
    val totals: YearTotals,
    /** January to December, empty months included. */
    val months: List<MonthBar>,
    /** Months with data so far, used for monthly averages. */
    val monthsCounted: Int,
    val topCategories: List<CategorySpend>,
    val biggestSpendingMonth: MonthBar?,
    val bestSavingMonth: MonthBar?,
    val biggestExpense: Transaction?,
    val txCount: Int,
    val noSpendDays: Int,
    /** Null when there is nothing to compare with. */
    val previous: YearTotals?,
    /** The last day of last year that [previous] counts. */
    val previousUntil: LocalDate?,
    /** Categories that moved the most against last year, biggest change first. */
    val changes: List<CategoryChange>,
) {
    val avgMonthlyExpense: Double get() = if (monthsCounted > 0) totals.expense / monthsCounted else 0.0
}

/** Years that have transactions, plus the current one, newest first. */
fun reportYears(d: AppData, today: LocalDate): List<Int> =
    (d.transactions.map { it.date.year } + today.year).distinct().sortedDescending()

fun computeYearReport(d: AppData, year: Int, today: LocalDate): YearReport {
    val isCurrent = year == today.year
    val inYear = d.transactions.filter { it.date.year == year && !it.date.isAfter(today) }
    val expenses = inYear.filter { it.type == TxType.EXPENSE }
    val totals = YearTotals(inYear.filter { it.type == TxType.INCOME }.sumOf { it.amount }, expenses.sumOf { it.amount })

    val byMonth = inYear.groupBy { it.date.monthValue }
    val months = (1..12).map { m ->
        val l = byMonth[m].orEmpty()
        MonthBar(YearMonth.of(year, m), l.filter { it.type == TxType.INCOME }.sumOf { it.amount }, l.filter { it.type == TxType.EXPENSE }.sumOf { it.amount })
    }
    val firstMonth = inYear.minOfOrNull { it.date.monthValue }
    val lastMonth = if (isCurrent) today.monthValue else 12
    val monthsCounted = if (firstMonth == null) 0 else lastMonth - firstMonth + 1
    val active = months.filter { it.income > 0 || it.expense > 0 }

    val topCategories = expenses.groupBy { it.categoryId }
        .map { (id, l) -> val s = l.sumOf { it.amount }; CategorySpend(Categories.of(id), s, if (totals.expense > 0) (s / totals.expense).toFloat() else 0f) }
        .sortedByDescending { it.amount }

    val noSpendDays = inYear.minOfOrNull { it.date }?.let { first ->
        val end = if (isCurrent) today else LocalDate.of(year, 12, 31)
        val spendDays = expenses.map { it.date }.toSet()
        val days = ChronoUnit.DAYS.between(first, end).toInt() + 1
        days - spendDays.size
    } ?: 0

    // Last year, up to the same day when this year isn't over yet.
    val prevEnd = if (isCurrent) today.minusYears(1) else LocalDate.of(year - 1, 12, 31)
    val inPrev = d.transactions.filter { it.date.year == year - 1 && !it.date.isAfter(prevEnd) }
    val prevExpenses = inPrev.filter { it.type == TxType.EXPENSE }
    val previous = if (inPrev.isEmpty()) null else YearTotals(inPrev.filter { it.type == TxType.INCOME }.sumOf { it.amount }, prevExpenses.sumOf { it.amount })

    val changes = if (previous == null) emptyList() else {
        val now = expenses.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val before = prevExpenses.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amount } }
        (now.keys + before.keys)
            .map { CategoryChange(Categories.of(it), now[it] ?: 0.0, before[it] ?: 0.0) }
            .filter { kotlin.math.abs(it.diff) >= 1.0 }
            .sortedByDescending { kotlin.math.abs(it.diff) }
            .take(4)
    }

    return YearReport(
        year = year,
        isCurrentYear = isCurrent,
        totals = totals,
        months = months,
        monthsCounted = monthsCounted,
        topCategories = topCategories,
        biggestSpendingMonth = active.filter { it.expense > 0 }.maxByOrNull { it.expense },
        bestSavingMonth = active.filter { it.income > 0 }.maxByOrNull { it.income - it.expense }?.takeIf { it.income > it.expense },
        biggestExpense = expenses.maxByOrNull { it.amount },
        txCount = inYear.size,
        noSpendDays = noSpendDays,
        previous = previous,
        previousUntil = if (previous == null) null else prevEnd,
        changes = changes,
    )
}
