package com.coinly.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coinly.app.data.AppData
import com.coinly.app.data.Budget
import com.coinly.app.data.FinanceRepository
import com.coinly.app.data.Goal
import com.coinly.app.data.IdGen
import com.coinly.app.data.MonthStats
import com.coinly.app.data.Recurring
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.applyRecurring
import com.coinly.app.data.buildCsv
import com.coinly.app.data.computeStats
import com.coinly.app.data.demoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class FinanceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FinanceRepository(File(app.filesDir, "coinly.json"))
    val data: StateFlow<AppData> = repo.data

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** Stats for the month the user is browsing. */
    val stats: StateFlow<MonthStats> = combine(repo.data, _month) { d, m -> computeStats(d, m, LocalDate.now()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, computeStats(repo.data.value, _month.value, LocalDate.now()))

    /** Stats for the real current month (budgets and goals always live in "now"). */
    val currentStats: StateFlow<MonthStats> = repo.data.map { computeStats(it, YearMonth.now(), LocalDate.now()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, computeStats(repo.data.value, YearMonth.now(), LocalDate.now()))

    init {
        refreshRecurring()
    }

    fun refreshRecurring() = repo.update { applyRecurring(it, LocalDate.now()) }

    fun prevMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { if (_month.value < YearMonth.now()) _month.value = _month.value.plusMonths(1) }

    fun upsertTransaction(t: Transaction) = repo.update { d -> d.copy(transactions = d.transactions.filterNot { it.id == t.id } + t) }
    fun deleteTransaction(id: Long) = repo.update { d -> d.copy(transactions = d.transactions.filterNot { it.id == id }) }

    fun saveBudget(b: Budget) = repo.update { d -> d.copy(budgets = d.budgets.filterNot { it.categoryId == b.categoryId } + b) }
    fun deleteBudget(categoryId: String) = repo.update { d -> d.copy(budgets = d.budgets.filterNot { it.categoryId == categoryId }) }

    fun saveGoal(g: Goal) = repo.update { d ->
        val exists = d.goals.any { it.id == g.id }
        d.copy(goals = if (exists) d.goals.map { if (it.id == g.id) g else it } else d.goals + g)
    }
    fun deleteGoal(id: Long) = repo.update { d -> d.copy(goals = d.goals.filterNot { it.id == id }) }

    /** Moving money into a goal is booked as a "Savings" expense so the balance stays honest. */
    fun contribute(goal: Goal, amount: Double) = repo.update { d ->
        d.copy(
            goals = d.goals.map { if (it.id == goal.id) it.copy(saved = it.saved + amount) else it },
            transactions = d.transactions + Transaction(IdGen.next(), amount, TxType.EXPENSE, "savings", "${goal.emoji} ${goal.name}", LocalDate.now()),
        )
    }

    fun saveRecurring(r: Recurring) = repo.update { d ->
        applyRecurring(d.copy(recurring = d.recurring.filterNot { it.id == r.id } + r), LocalDate.now())
    }
    fun deleteRecurring(id: Long) = repo.update { d -> d.copy(recurring = d.recurring.filterNot { it.id == id }) }

    fun setCurrency(c: String) = repo.update { it.copy(currency = c) }
    fun setTheme(t: String) = repo.update { it.copy(theme = t) }
    fun loadDemo() = repo.update { demoData(LocalDate.now(), it.currency).copy(theme = it.theme) }
    fun clearAll() = repo.update { AppData(currency = it.currency, theme = it.theme) }

    fun exportCsv(): String = buildCsv(data.value)
}
