package com.coinly.app.data

import java.time.LocalDate
import kotlin.math.abs

/** Everything still open between the user and one person. [net] > 0 means they owe the user, < 0 that the user owes them. */
data class PersonBalance(val person: String, val net: Double, val open: List<Debt>) {
    val theyOwe: Boolean get() = net > 0
}

private fun personKey(name: String) = name.trim().lowercase()

/** Open debts grouped by person (names match regardless of case), largest balance first. */
fun balances(d: AppData): List<PersonBalance> = d.debts
    .filter { it.isOpen }
    .groupBy { personKey(it.person) }
    .map { (_, list) ->
        val sorted = list.sortedWith(compareByDescending<Debt> { it.date }.thenByDescending { it.id })
        PersonBalance(sorted.first().person.trim(), list.sumOf { if (it.theyOwe) it.amount else -it.amount }, sorted)
    }
    .sortedByDescending { abs(it.net) }

/** Totals across everyone: what others owe the user, and what the user owes others. */
fun debtTotals(d: AppData): Pair<Double, Double> {
    val b = balances(d)
    return b.filter { it.net > 0 }.sumOf { it.net } to b.filter { it.net < 0 }.sumOf { -it.net }
}

/**
 * Closes every open debt with [person]. When [paid] is less than the balance, what's left stays open
 * as one new debt in the same direction, so a partial repayment is just a smaller settle-up.
 */
fun settleUp(d: AppData, person: String, paid: Double?, today: LocalDate): AppData {
    val key = personKey(person)
    val open = d.debts.filter { it.isOpen && personKey(it.person) == key }
    if (open.isEmpty()) return d
    val net = open.sumOf { if (it.theyOwe) it.amount else -it.amount }
    val left = if (paid == null) 0.0 else abs(net) - paid
    val remainder = if (left >= 0.005) listOf(Debt(IdGen.next(), open.first().person.trim(), left, net > 0, "Left after a partial payment", today)) else emptyList()
    val ids = open.map { it.id }.toSet()
    return d.copy(debts = d.debts.map { if (it.id in ids) it.copy(settledOn = today) else it } + remainder)
}

/** A shared expense: the user's share as a normal expense (null if it's zero) and what is owed. */
data class Split(val expense: Transaction?, val debt: Debt)

/**
 * Splits [tx] (the whole bill) with [person]. Only [myShare] is booked as spending, so budgets and stats
 * show what the user really spent. If the user paid, the other share is owed to them; if [person] paid, the user owes their own share.
 */
fun splitExpense(tx: Transaction, person: String, myShare: Double, iPaid: Boolean): Split {
    require(myShare >= 0 && myShare <= tx.amount + 0.005) { "Your share can't be more than the bill" }
    val name = person.trim()
    val expense = if (myShare >= 0.005) tx.copy(amount = myShare, note = (tx.note.ifBlank { tx.category.label } + " · split with $name").take(120)) else null
    val debt = if (iPaid) {
        Debt(IdGen.next(), name, tx.amount - myShare, theyOwe = true, note = tx.title, date = tx.date)
    } else {
        Debt(IdGen.next(), name, myShare, theyOwe = false, note = tx.title, date = tx.date)
    }
    return Split(expense, debt)
}

/** Names already used, most recent first, for one-tap picking. */
fun knownPeople(d: AppData): List<String> = d.debts
    .sortedByDescending { it.date }
    .map { it.person.trim() }
    .distinctBy { personKey(it) }
