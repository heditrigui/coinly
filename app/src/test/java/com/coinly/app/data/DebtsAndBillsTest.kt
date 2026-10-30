package com.coinly.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtsAndBillsTest {
    private val today = LocalDate.of(2026, 10, 25)
    private var nextId = 1L

    private fun debt(person: String, amount: Double, theyOwe: Boolean, settled: LocalDate? = null) =
        Debt(nextId++, person, amount, theyOwe, "", today, settled)

    @Test fun `balances net each person's debts, ignoring case and settled ones`() {
        val d = AppData(
            debts = listOf(
                debt("Sam", 30.0, theyOwe = true),
                debt("sam ", 10.0, theyOwe = false),
                debt("Alex", 50.0, theyOwe = false),
                debt("Alex", 80.0, theyOwe = true, settled = today),
            ),
        )
        val b = balances(d)
        // Shown with the spelling used most recently.
        assertEquals(listOf("Alex", "sam"), b.map { it.person })
        assertEquals(-50.0, b[0].net, 1e-9)
        assertEquals(20.0, b[1].net, 1e-9)
        assertEquals(2, b[1].open.size)
        assertEquals(20.0 to 50.0, debtTotals(d))
    }

    @Test fun `settling up closes every open debt with that person`() {
        val d = AppData(debts = listOf(debt("Sam", 30.0, true), debt("SAM", 10.0, false), debt("Alex", 5.0, true)))
        val after = settleUp(d, "sam", null, today)
        assertEquals(listOf("Alex"), balances(after).map { it.person })
        assertEquals(2, after.debts.count { it.settledOn == today })
    }

    @Test fun `a partial payment leaves the rest open`() {
        val d = AppData(debts = listOf(debt("Sam", 30.0, true), debt("Sam", 10.0, false)))
        val after = settleUp(d, "Sam", 15.0, today)
        val b = balances(after).single()
        assertEquals(5.0, b.net, 1e-9)
        assertEquals(1, b.open.size)
        assertTrue(b.theyOwe)
    }

    @Test fun `splitting a bill I paid books my share and records what they owe`() {
        val bill = Transaction(1, 60.0, TxType.EXPENSE, "food", "Dinner", today)
        val s = splitExpense(bill, " Sam ", 25.0, iPaid = true)
        assertEquals(25.0, s.expense!!.amount, 1e-9)
        assertEquals("Dinner · split with Sam", s.expense!!.note)
        assertEquals(Debt(s.debt.id, "Sam", 35.0, true, "Dinner", today), s.debt)
    }

    @Test fun `splitting a bill they paid means I owe my share`() {
        val bill = Transaction(1, 60.0, TxType.EXPENSE, "food", "", today)
        val s = splitExpense(bill, "Sam", 30.0, iPaid = false)
        assertEquals(30.0, s.expense!!.amount, 1e-9)
        assertEquals("Food & Dining · split with Sam", s.expense!!.note)
        assertEquals(30.0, s.debt.amount, 1e-9)
        assertEquals(false, s.debt.theyOwe)
    }

    @Test fun `paying entirely for someone is just a loan`() {
        val s = splitExpense(Transaction(1, 40.0, TxType.EXPENSE, "fun", "Tickets", today), "Sam", 0.0, iPaid = true)
        assertNull(s.expense)
        assertEquals(40.0, s.debt.amount, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `my share can't be more than the bill`() {
        splitExpense(Transaction(1, 40.0, TxType.EXPENSE, "fun", "", today), "Sam", 50.0, iPaid = true)
    }

    @Test fun `known people are distinct and most recent first`() {
        val d = AppData(
            debts = listOf(
                Debt(1, "Sam", 1.0, true, "", today.minusDays(5)),
                Debt(2, "Alex", 1.0, true, "", today),
                Debt(3, "sam", 1.0, true, "", today.minusDays(1)),
            ),
        )
        assertEquals(listOf("Alex", "sam"), knownPeople(d))
    }

    private fun bill(day: Int, remind: Int?, type: TxType = TxType.EXPENSE) =
        Recurring(nextId++, 50.0, type, "bills", "Bill $day", day, "2026-10", remind)

    @Test fun `next due date rolls into next month and clamps short months`() {
        assertEquals(LocalDate.of(2026, 10, 28), nextDue(bill(28, 3), today))
        assertEquals(LocalDate.of(2026, 11, 25), nextDue(bill(25, 3), today))
        assertEquals(LocalDate.of(2027, 2, 28), nextDue(bill(31, 3), LocalDate.of(2027, 1, 31)))
    }

    @Test fun `reminders cover bills due within their window only`() {
        val d = AppData(
            recurring = listOf(
                bill(28, 3), // due in 3 days: remind
                bill(27, 1), // due in 2 days but only 1 day's notice wanted
                bill(26, null), // no reminder
                bill(2, 7), // Nov 2, in 8 days
                bill(1, 7), // Nov 1, in 7 days: remind
                bill(27, 5, TxType.INCOME), // income is never a bill
            ),
        )
        val r = billReminders(d, today)
        assertEquals(listOf("Bill 28", "Bill 1"), r.map { it.recurring.note })
        assertEquals(listOf(3, 7), r.map { it.daysLeft })
        assertEquals("${r[0].recurring.id}@2026-10-28", r[0].key)
    }
}
