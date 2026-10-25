package com.coinly.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ShortcutsTest {
    private val today = LocalDate.of(2026, 10, 25)
    private var nextId = 1L

    private fun tx(amount: Double, cat: String, note: String = "", date: LocalDate = today, type: TxType = TxType.EXPENSE) =
        Transaction(nextId++, amount, type, cat, note, date)

    @Test fun `frequent entries are the repeated ones, most used first`() {
        val d = AppData(
            transactions = listOf(
                tx(3.5, "food", "Coffee"), tx(3.5, "food", "coffee "), tx(3.5, "food", "Coffee"),
                tx(2.0, "transport", "Bus"), tx(2.0, "transport", "Bus"),
                tx(40.0, "shopping", "Shoes"),
            ),
        )
        val q = frequentEntries(d, TxType.EXPENSE, today)
        assertEquals(listOf("food", "transport"), q.map { it.categoryId })
        assertEquals(3, q[0].uses)
        assertEquals(3.5, q[0].amount, 0.0)
    }

    @Test fun `frequent entries skip recurring, goal savings, old entries and the other type`() {
        val d = AppData(
            transactions = listOf(
                tx(950.0, "housing", "Rent"), tx(950.0, "housing", "Rent", today.minusMonths(1)),
                tx(50.0, "savings", "🏖️ Trip"), tx(50.0, "savings", "🏖️ Trip"),
                tx(5.0, "food", "Snack", today.minusDays(120)), tx(5.0, "food", "Snack", today.minusDays(100)),
                tx(100.0, "gift", "Gift", type = TxType.INCOME), tx(100.0, "gift", "Gift", type = TxType.INCOME),
            ),
            recurring = listOf(Recurring(1, 950.0, TxType.EXPENSE, "housing", "Rent", 2, "2026-10")),
        )
        assertTrue(frequentEntries(d, TxType.EXPENSE, today).isEmpty())
        assertEquals(listOf("gift"), frequentEntries(d, TxType.INCOME, today).map { it.categoryId })
    }

    @Test fun `budget preview counts this month only and leaves out the entry being edited`() {
        val edited = tx(20.0, "food")
        val d = AppData(
            transactions = listOf(tx(150.0, "food"), edited, tx(500.0, "food", date = today.minusMonths(1)), tx(30.0, "fun")),
            budgets = listOf(Budget("food", 200.0)),
        )
        val p = budgetPreview(d, "food", today, 40.0, excludeId = edited.id)!!
        assertEquals(150.0, p.spentBefore, 0.0)
        assertEquals(190.0, p.spentAfter, 0.0)
        assertEquals(0.95, p.ratioAfter, 1e-9)
        assertNull(budgetPreview(d, "fun", today, 10.0))
    }

    @Test fun `alerts fire once when crossing 80 percent and again when going over`() {
        val month = YearMonth.from(today)
        val base = AppData(transactions = listOf(tx(70.0, "food")), budgets = listOf(Budget("food", 100.0)))
        val at85 = base.copy(transactions = base.transactions + tx(15.0, "food"))
        val at90 = at85.copy(transactions = at85.transactions + tx(5.0, "food"))
        val over = at90.copy(transactions = at90.transactions + tx(20.0, "food"))

        assertEquals(listOf(1), budgetAlerts(base, at85, month).map { it.level })
        assertTrue(budgetAlerts(at85, at90, month).isEmpty())
        assertEquals(listOf(2), budgetAlerts(at90, over, month).map { it.level })
        assertEquals(listOf(2), budgetAlerts(base, over, month).map { it.level })
        assertTrue(budgetAlerts(over, base, month).isEmpty())
    }

    @Test fun `adding a budget that is already used up does not alert`() {
        val month = YearMonth.from(today)
        val before = AppData(transactions = listOf(tx(90.0, "food")))
        assertTrue(budgetAlerts(before, before.copy(budgets = listOf(Budget("food", 100.0))), month).isEmpty())
    }

    @Test fun `backup round trip keeps everything`() {
        val d = AppData(
            transactions = listOf(tx(12.5, "food", "Lunch, with \"friends\""), tx(3000.0, "salary", type = TxType.INCOME)),
            budgets = listOf(Budget("food", 300.0)),
            goals = listOf(Goal(7, "Trip", "🏖️", 1000.0, 250.0, LocalDate.of(2027, 6, 1))),
            recurring = listOf(Recurring(8, 950.0, TxType.EXPENSE, "housing", "Rent", 2, "2026-10")),
            currency = "₪",
            theme = "dark",
            lastCategory = mapOf(TxType.EXPENSE to "food", TxType.INCOME to "salary"),
            budgetAlerts = false,
            lastBackup = today,
        )
        assertEquals(d, JsonCodec.decodeBackup(JsonCodec.encode(d)))
    }

    @Test fun `version 1 files still load with defaults`() {
        val old = """{"version":1,"currency":"€","theme":"system","transactions":[],"budgets":[],"goals":[],"recurring":[]}"""
        val d = JsonCodec.decodeBackup(old)
        assertEquals("€", d.currency)
        assertTrue(d.budgetAlerts)
        assertTrue(d.lastCategory.isEmpty())
        assertNull(d.lastBackup)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `other JSON files are refused`() {
        JsonCodec.decodeBackup("""{"name":"not coinly"}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non JSON files are refused`() {
        JsonCodec.decodeBackup("date,type,category,amount,note")
    }
}
