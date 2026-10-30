package com.coinly.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class YearReportTest {
    private val today = LocalDate.of(2026, 10, 25)
    private var nextId = 1L

    private fun tx(amount: Double, cat: String, date: LocalDate, type: TxType = TxType.EXPENSE) =
        Transaction(nextId++, amount, type, cat, "", date)

    private val data = AppData(
        transactions = listOf(
            // Last year: one entry before today's date last year, one after.
            tx(2000.0, "salary", LocalDate.of(2025, 3, 1), TxType.INCOME),
            tx(500.0, "food", LocalDate.of(2025, 3, 2)),
            tx(900.0, "travel", LocalDate.of(2025, 12, 20)),
            // This year.
            tx(3000.0, "salary", LocalDate.of(2026, 2, 1), TxType.INCOME),
            tx(3000.0, "salary", LocalDate.of(2026, 9, 1), TxType.INCOME),
            tx(800.0, "food", LocalDate.of(2026, 2, 3)),
            tx(1200.0, "housing", LocalDate.of(2026, 9, 2)),
            tx(300.0, "food", LocalDate.of(2026, 9, 5)),
            // Future-dated entries are not part of the report yet.
            tx(99.0, "fun", LocalDate.of(2026, 12, 1)),
        ),
    )

    @Test fun `totals, months and top categories`() {
        val r = computeYearReport(data, 2026, today)
        assertEquals(6000.0, r.totals.income, 1e-9)
        assertEquals(2300.0, r.totals.expense, 1e-9)
        assertEquals(3700.0 / 6000.0, r.totals.savingsRate, 1e-9)
        assertEquals(12, r.months.size)
        assertEquals(1500.0, r.months[8].expense, 1e-9)
        assertEquals(listOf("housing", "food"), r.topCategories.map { it.category.id })
        assertEquals(1100.0, r.topCategories[1].amount, 1e-9)
        assertEquals(YearMonth.of(2026, 9), r.biggestSpendingMonth!!.month)
        assertEquals(YearMonth.of(2026, 2), r.bestSavingMonth!!.month)
        assertEquals(1200.0, r.biggestExpense!!.amount, 1e-9)
        assertEquals(5, r.txCount)
        // February to October.
        assertEquals(9, r.monthsCounted)
        assertEquals(2300.0 / 9, r.avgMonthlyExpense, 1e-9)
    }

    @Test fun `this year is compared with the same stretch of last year`() {
        val r = computeYearReport(data, 2026, today)
        assertEquals(2000.0, r.previous!!.income, 1e-9)
        assertEquals(500.0, r.previous!!.expense, 1e-9)
        assertEquals(LocalDate.of(2025, 10, 25), r.previousUntil)
        assertEquals(listOf("housing", "food"), r.changes.map { it.category.id })
        assertEquals(600.0, r.changes[1].diff, 1e-9)
    }

    @Test fun `a finished year compares with all of the year before`() {
        val r = computeYearReport(data, 2025, today)
        assertEquals(1400.0, r.totals.expense, 1e-9)
        assertNull(r.previous)
        assertEquals(10, r.monthsCounted)
        // March 1 to December 31 is 306 days, with expenses on two of them.
        assertEquals(304, r.noSpendDays)
    }

    @Test fun `report years include the current one, newest first`() {
        assertEquals(listOf(2026, 2025), reportYears(data, today))
        assertEquals(listOf(2026), reportYears(AppData(), today))
    }

    @Test fun `an empty year has nothing to show`() {
        val r = computeYearReport(AppData(), 2026, today)
        assertEquals(0, r.txCount)
        assertEquals(0, r.monthsCounted)
        assertNull(r.biggestSpendingMonth)
        assertNull(r.bestSavingMonth)
        assertEquals(0, r.noSpendDays)
    }
}
