package com.coinly.app.data

import java.time.LocalDate

enum class TxType { INCOME, EXPENSE }

data class Category(
    val id: String,
    val label: String,
    val emoji: String,
    val color: Long,
    val type: TxType,
)

object Categories {
    val expense = listOf(
        Category("food", "Food & Dining", "🍔", 0xFFFF7A59, TxType.EXPENSE),
        Category("groceries", "Groceries", "🛒", 0xFF4CAF50, TxType.EXPENSE),
        Category("transport", "Transport", "🚗", 0xFF42A5F5, TxType.EXPENSE),
        Category("housing", "Housing", "🏠", 0xFF7E57C2, TxType.EXPENSE),
        Category("bills", "Bills", "💡", 0xFFFFB300, TxType.EXPENSE),
        Category("shopping", "Shopping", "🛍️", 0xFFEC407A, TxType.EXPENSE),
        Category("health", "Health", "💊", 0xFF26A69A, TxType.EXPENSE),
        Category("fun", "Entertainment", "🎬", 0xFFAB47BC, TxType.EXPENSE),
        Category("travel", "Travel", "✈️", 0xFF29B6F6, TxType.EXPENSE),
        Category("education", "Education", "📚", 0xFF8D6E63, TxType.EXPENSE),
        Category("subs", "Subscriptions", "📺", 0xFF5C6BC0, TxType.EXPENSE),
        Category("savings", "Savings", "🐷", 0xFF66BB6A, TxType.EXPENSE),
        Category("other", "Other", "📦", 0xFF90A4AE, TxType.EXPENSE),
    )
    val income = listOf(
        Category("salary", "Salary", "💼", 0xFF2E7D32, TxType.INCOME),
        Category("freelance", "Freelance", "💻", 0xFF00897B, TxType.INCOME),
        Category("bonus", "Bonus", "🎉", 0xFFF9A825, TxType.INCOME),
        Category("gift", "Gift", "🎁", 0xFFD81B60, TxType.INCOME),
        Category("invest", "Investments", "📈", 0xFF1E88E5, TxType.INCOME),
        Category("other_in", "Other income", "💰", 0xFF78909C, TxType.INCOME),
    )

    private val byId = (expense + income).associateBy { it.id }

    fun of(id: String): Category = byId[id] ?: expense.last()
    fun forType(type: TxType): List<Category> = if (type == TxType.INCOME) income else expense
}

data class Transaction(
    val id: Long,
    val amount: Double,
    val type: TxType,
    val categoryId: String,
    val note: String,
    val date: LocalDate,
) {
    val category: Category get() = Categories.of(categoryId)
    val title: String get() = note.ifBlank { category.label }
}

data class Budget(val categoryId: String, val limit: Double)

data class Goal(
    val id: Long,
    val name: String,
    val emoji: String,
    val target: Double,
    val saved: Double,
    val deadline: LocalDate?,
)

/** A monthly repeating entry (salary, rent...). [lastApplied] is the last YearMonth ("2026-10") it was booked for. */
data class Recurring(
    val id: Long,
    val amount: Double,
    val type: TxType,
    val categoryId: String,
    val note: String,
    val dayOfMonth: Int,
    val lastApplied: String?,
)

data class AppData(
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val currency: String = "$",
    /** "system", "light" or "dark". */
    val theme: String = "system",
) {
    fun isDark(systemDark: Boolean): Boolean = when (theme) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
}

object IdGen {
    private var last = 0L

    @Synchronized
    fun next(): Long {
        val n = maxOf(System.currentTimeMillis(), last + 1)
        last = n
        return n
    }
}
