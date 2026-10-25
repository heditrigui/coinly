package com.coinly.app.data

import java.time.LocalDate
import java.time.YearMonth

/** An entry the user keeps typing by hand (same category, note and amount), offered as a one-tap shortcut. */
data class QuickEntry(val categoryId: String, val note: String, val amount: Double, val uses: Int) {
    val category: Category get() = Categories.of(categoryId)
}

/** Most repeated entries of the last [days] days, leaving out what Coinly books by itself (recurring entries, goal savings). */
fun frequentEntries(d: AppData, type: TxType, today: LocalDate, limit: Int = 4, days: Long = 90): List<QuickEntry> {
    val since = today.minusDays(days)
    val automatic = d.recurring.map { Triple(it.categoryId, it.note.trim().lowercase(), it.amount) }.toSet()
    return d.transactions
        .filter { it.type == type && it.date.isAfter(since) && !it.date.isAfter(today) && it.categoryId != "savings" }
        .groupBy { Triple(it.categoryId, it.note.trim().lowercase(), it.amount) }
        .filter { (key, list) -> list.size >= 2 && key !in automatic }
        .map { (_, list) ->
            val latest = list.maxBy { it.id }
            QuickEntry(latest.categoryId, latest.note.trim(), latest.amount, list.size)
        }
        .sortedWith(compareByDescending<QuickEntry> { it.uses }.thenByDescending { it.amount })
        .take(limit)
}

/** Where a category's monthly budget stands, and where it would stand after the entry being typed. */
data class BudgetPreview(val category: Category, val limit: Double, val spentBefore: Double, val spentAfter: Double) {
    val ratioBefore: Double get() = spentBefore / limit
    val ratioAfter: Double get() = spentAfter / limit
    val leftAfter: Double get() = limit - spentAfter
}

/** Null when the category has no budget. [excludeId] is the transaction being edited, so it isn't counted twice. */
fun budgetPreview(d: AppData, categoryId: String, date: LocalDate, amount: Double, excludeId: Long? = null): BudgetPreview? {
    val budget = d.budgets.firstOrNull { it.categoryId == categoryId && it.limit > 0 } ?: return null
    val month = YearMonth.from(date)
    val spent = d.transactions
        .filter { it.type == TxType.EXPENSE && it.categoryId == categoryId && it.id != excludeId && YearMonth.from(it.date) == month }
        .sumOf { it.amount }
    return BudgetPreview(Categories.of(categoryId), budget.limit, spent, spent + amount)
}

/** 0 = fine, 1 = 80% used, 2 = over budget. */
fun alertLevel(spent: Double, limit: Double): Int = when {
    limit <= 0 -> 0
    spent > limit -> 2
    spent >= limit * 0.8 -> 1
    else -> 0
}

data class BudgetAlert(val status: BudgetStatus, val level: Int)

/** Budgets of [month] that moved up an alert level between [before] and [after]. */
fun budgetAlerts(before: AppData, after: AppData, month: YearMonth): List<BudgetAlert> {
    fun spentByCat(d: AppData) = d.transactions
        .filter { it.type == TxType.EXPENSE && YearMonth.from(it.date) == month }
        .groupBy { it.categoryId }
        .mapValues { (_, l) -> l.sumOf { it.amount } }
    val was = spentByCat(before)
    val now = spentByCat(after)
    return after.budgets.mapNotNull { b ->
        val oldLimit = before.budgets.firstOrNull { it.categoryId == b.categoryId }?.limit ?: b.limit
        val oldLevel = alertLevel(was[b.categoryId] ?: 0.0, oldLimit)
        val spent = now[b.categoryId] ?: 0.0
        val level = alertLevel(spent, b.limit)
        if (level > oldLevel) BudgetAlert(BudgetStatus(b, Categories.of(b.categoryId), spent), level) else null
    }
}
