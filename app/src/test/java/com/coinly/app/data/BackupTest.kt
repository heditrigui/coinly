package com.coinly.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val today = LocalDate.of(2026, 10, 25)

    @Test fun `debts, reminders, app lock and import mappings survive a backup`() {
        val d = AppData(
            transactions = listOf(Transaction(1, 12.5, TxType.EXPENSE, "food", "Lunch", today)),
            recurring = listOf(
                Recurring(2, 45.0, TxType.EXPENSE, "bills", "Electricity", 6, "2026-10", remindDaysBefore = 3),
                Recurring(3, 3000.0, TxType.INCOME, "salary", "Salary", 1, "2026-10"),
            ),
            debts = listOf(
                Debt(4, "Sam", 35.0, theyOwe = true, note = "Dinner, \"split\"", date = today),
                Debt(5, "Alex", 20.0, theyOwe = false, note = "", date = today.minusDays(3), settledOn = today),
            ),
            appLock = true,
            importMappings = mapOf(
                "date|libellé|débit|crédit" to CsvMapping(0, 1, 2, inCol = 3, datePattern = "d/M/yyyy"),
                "columns:4" to CsvMapping(0, 1, 2, expensesPositive = true, datePattern = "yyyy-M-d"),
            ),
        )
        assertEquals(d, JsonCodec.decodeBackup(JsonCodec.encode(d)))
    }

    @Test fun `version 2 files load with the new data empty`() {
        val v2 = """{"app":"coinly","version":2,"currency":"DT","theme":"dark","budgetAlerts":false,""" +
            """"transactions":[{"id":1,"amount":5.5,"type":"EXPENSE","cat":"food","note":"Coffee","date":${today.toEpochDay()}}],""" +
            """"budgets":[],"goals":[],"recurring":[{"id":2,"amount":950,"type":"EXPENSE","cat":"housing","note":"Rent","day":2,"last":"2026-10"}]}"""
        val d = JsonCodec.decodeBackup(v2)
        assertEquals(1, d.transactions.size)
        assertEquals("DT", d.currency)
        assertFalse(d.budgetAlerts)
        assertNull(d.recurring.single().remindDaysBefore)
        assertTrue(d.debts.isEmpty())
        assertFalse(d.appLock)
        assertTrue(d.importMappings.isEmpty())
        // Saving again writes the current version.
        assertTrue(JsonCodec.encode(d).contains("\"version\":3"))
    }

    @Test fun `debts are exported as their own CSV`() {
        val d = AppData(
            debts = listOf(
                Debt(2, "Alex", 20.0, theyOwe = false, note = "", date = today, settledOn = today),
                Debt(1, "Sam", 35.0, theyOwe = true, note = "Dinner, drinks", date = today.minusDays(1)),
            ),
        )
        assertEquals(
            "date,person,direction,amount,note,settled\n" +
                "2026-10-24,Sam,owes me,35.00,\"Dinner, drinks\",\n" +
                "2026-10-25,Alex,I owe,20.00,,2026-10-25\n",
            buildDebtsCsv(d).replace("\r\n", "\n"),
        )
    }
}
