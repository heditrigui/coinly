package com.coinly.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BankImportTest {
    private var nextId = 1L
    private fun tx(amount: Double, cat: String, note: String, date: LocalDate = LocalDate.of(2026, 10, 3), type: TxType = TxType.EXPENSE) =
        Transaction(nextId++, amount, type, cat, note, date)

    @Test fun `csv cells keep quoted separators, quotes and line breaks`() {
        val rows = parseCsv("﻿Date;Libellé;Montant\r\n03/10/2026;\"CARREFOUR; TUNIS\";-12,50\r\n04/10/2026;\"Say \"\"hi\"\"\nthere\";5\r\n\r\n")
        assertEquals(listOf("Date", "Libellé", "Montant"), rows[0])
        assertEquals(listOf("03/10/2026", "CARREFOUR; TUNIS", "-12,50"), rows[1])
        assertEquals("Say \"hi\"\nthere", rows[2][1])
        assertEquals(3, rows.size)
    }

    @Test fun `header row is detected and preamble lines are skipped`() {
        val st = parseStatement("Account statement\nIBAN XX12 3456\nDate,Description,Amount\n2026-10-03,Coffee,-3.50\n2026-10-04,Salary,2000\n")
        assertEquals(listOf("Date", "Description", "Amount"), st.header)
        assertEquals(2, st.rows.size)
        assertEquals("date|description|amount", st.key)

        val noHeader = parseStatement("2026-10-03,Coffee,-3.50\n2026-10-04,Salary,2000\n")
        assertNull(noHeader.header)
        assertEquals(2, noHeader.rows.size)
        assertEquals("Column 2", noHeader.columnName(1))
    }

    @Test fun `amounts in every common bank format`() {
        assertEquals(-12.5, parseBankAmount("-12.50")!!, 1e-9)
        assertEquals(1234.56, parseBankAmount("1,234.56")!!, 1e-9)
        assertEquals(1234.56, parseBankAmount("1.234,56", ',')!!, 1e-9)
        assertEquals(-12.5, parseBankAmount("(12.50)")!!, 1e-9)
        assertEquals(-12.5, parseBankAmount("12.50-")!!, 1e-9)
        assertEquals(-12.5, parseBankAmount("−12.50 €")!!, 1e-9)
        assertEquals(1200.0, parseBankAmount("TND 1 200,000", ',')!!, 1e-9)
        assertNull(parseBankAmount(""))
        assertNull(parseBankAmount("n/a"))
    }

    @Test fun `decimal separator is read from the whole column`() {
        assertEquals(',', detectDecimal(listOf("-12,50", "1.234,56", "")))
        assertEquals('.', detectDecimal(listOf("-12.50", "3.2", "1,250.00")))
        // Three-decimal currencies (dinars) write every amount like this.
        assertEquals(',', detectDecimal(listOf("12,500", "-3,250")))
        assertEquals('.', detectDecimal(listOf("12.500", "-3.250")))
    }

    @Test fun `date layout prefers day first unless a value proves otherwise`() {
        assertEquals("d/M/yyyy", detectDatePattern(listOf("03/10/2026", "25/10/2026")))
        assertEquals("M/d/yyyy", detectDatePattern(listOf("10/03/2026", "10/25/2026")))
        assertEquals("yyyy-M-d", detectDatePattern(listOf("2026-10-03", "2026-10-25 14:02:11")))
        assertEquals("d-MMM-yyyy", detectDatePattern(listOf("03-OCT-2026", "25-Oct-2026")))
        assertEquals("d.M.yy", detectDatePattern(listOf("03.10.26")))
        assertNull(detectDatePattern(listOf("Coffee", "")))
        assertEquals(LocalDate.of(2026, 10, 25), parseBankDate("2026-10-25T14:02:11", "yyyy-M-d"))
    }

    @Test fun `mapping guessed from debit and credit headers`() {
        val st = parseStatement("Date;Libellé;Débit;Crédit;Solde\n03/10/2026;CARREFOUR TUNIS;45,200;;1200,000\n05/10/2026;VIR SALAIRE OCTOBRE;;2500,000;3700,000\n")
        val m = guessMapping(st)!!
        assertEquals(0, m.dateCol)
        assertEquals(1, m.descCol)
        assertEquals(2, m.amountCol)
        assertEquals(3, m.inCol)
        assertEquals("d/M/yyyy", m.datePattern)

        val lines = readStatement(st, m)
        assertEquals(listOf(TxType.EXPENSE, TxType.INCOME), lines.map { it.type })
        assertEquals(45.2, lines[0].amount, 1e-9)
        assertEquals(2500.0, lines[1].amount, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 5), lines[1].date)
    }

    @Test fun `single signed amount column and header-less files`() {
        val st = parseStatement("2026-10-03,STARBUCKS 1234,-4.20,995.80\n2026-10-04,ACME PAYROLL,2000.00,2995.80\n2026-10-04,Opening balance,,\n")
        val m = guessMapping(st)!!
        assertEquals(0, m.dateCol)
        assertEquals(1, m.descCol)
        assertEquals(2, m.amountCol)
        assertNull(m.inCol)
        assertFalse(m.expensesPositive)
        val lines = readStatement(st, m)
        assertEquals(2, lines.size)
        assertEquals(StatementLine(LocalDate.of(2026, 10, 3), 4.2, TxType.EXPENSE, "STARBUCKS 1234"), lines[0])
        assertEquals(TxType.INCOME, lines[1].type)
    }

    @Test fun `positive-only amount column means expenses are positive`() {
        val st = parseStatement("Date,Merchant,Amount\n10/03/2026,Uber,12.40\n10/25/2026,Netflix,15.99\n")
        val m = guessMapping(st)!!
        assertTrue(m.expensesPositive)
        assertEquals("M/d/yyyy", m.datePattern)
        assertTrue(readStatement(st, m).all { it.type == TxType.EXPENSE })
        val flipped = readStatement(st, m.copy(expensesPositive = false))
        assertTrue(flipped.all { it.type == TxType.INCOME })
    }

    @Test fun `duplicates match one to one`() {
        val day = LocalDate.of(2026, 10, 3)
        val existing = listOf(tx(3.5, "food", "Coffee  shop", day))
        val lines = listOf(
            StatementLine(day, 3.5, TxType.EXPENSE, "coffee shop"),
            StatementLine(day, 3.5, TxType.EXPENSE, "Coffee shop"),
            StatementLine(day, 3.5, TxType.INCOME, "Coffee shop"),
            StatementLine(day.plusDays(1), 3.5, TxType.EXPENSE, "Coffee shop"),
        )
        assertEquals(listOf(true, false, false, false), findDuplicates(lines, existing))
    }

    @Test fun `re-importing the same file skips every line`() {
        val st = parseStatement("Date,Description,Amount\n2026-10-03,Coffee,-3.50\n2026-10-03,Coffee,-3.50\n2026-10-04,Bus,-1.20\n")
        val lines = readStatement(st, guessMapping(st)!!)
        val first = prepareImport(lines, AppData())
        assertTrue(first.none { it.duplicate })
        val imported = AppData(transactions = first.map { tx(it.line.amount, it.categoryId, it.line.description, it.line.date, it.line.type) })
        assertTrue(prepareImport(lines, imported).all { it.duplicate })
    }

    @Test fun `categories come from history first, then keywords`() {
        val history = listOf(
            tx(20.0, "groceries", "MAGASIN GENERAL 001"),
            tx(25.0, "groceries", "Magasin General 002"),
            tx(9.0, "fun", "Pool hall"),
            tx(3000.0, "salary", "ACME Corp", type = TxType.INCOME),
        )
        val g = CategoryGuesser(history)
        assertEquals("groceries", g.guess("MAGASIN GENERAL 314 TUNIS", TxType.EXPENSE))
        assertEquals("fun", g.guess("CB POOL HALL 03/10", TxType.EXPENSE))
        assertEquals("salary", g.guess("ACME CORP OCT", TxType.INCOME))
        assertEquals("transport", g.guess("CB*UBER TRIP HELP.UBER.COM", TxType.EXPENSE))
        assertEquals("food", g.guess("UBER EATS 42", TxType.EXPENSE))
        assertEquals("subs", g.guess("NETFLIX.COM", TxType.EXPENSE))
        assertEquals("salary", g.guess("VIR SALAIRE OCTOBRE", TxType.INCOME))
        assertEquals("other", g.guess("BUSINESS LUNCH CLUB", TxType.EXPENSE))
        assertEquals("other", g.guess("XYZ 123", TxType.EXPENSE))
        assertEquals("other_in", g.guess("XYZ 123", TxType.INCOME))
    }

    @Test fun `guessing needs at least a date and an amount column`() {
        assertNull(guessMapping(parseStatement("Name,Note\nSam,Hello\n")))
        assertNotNull(guessMapping(parseStatement("Date,Amount,Note\n2026-10-03,-5,Bus\n")))
    }
}
