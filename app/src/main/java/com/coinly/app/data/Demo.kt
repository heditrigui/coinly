package com.coinly.app.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/** Six months of believable sample data so every chart has something to show. */
fun demoData(today: LocalDate, currency: String): AppData {
    val rnd = Random(today.toEpochDay())
    val tx = mutableListOf<Transaction>()
    val first = YearMonth.from(today).minusMonths(5)

    data class Pattern(val cat: String, val notes: List<String>, val min: Int, val max: Int)
    val everyday = listOf(
        Pattern("food", listOf("Lunch", "Pizza night", "Coffee", "Sushi", "Burger"), 4, 35),
        Pattern("groceries", listOf("Supermarket", "Market", "Bakery"), 15, 90),
        Pattern("transport", listOf("Fuel", "Taxi", "Metro card"), 5, 60),
        Pattern("shopping", listOf("Clothes", "Shoes", "Gadget", "Home decor"), 20, 140),
        Pattern("fun", listOf("Cinema", "Concert", "Bowling", "Games"), 10, 60),
        Pattern("health", listOf("Pharmacy", "Gym"), 10, 50),
    )

    for (i in 0..5) {
        val ym = first.plusMonths(i.toLong())
        fun add(day: Int, amount: Double, type: TxType, cat: String, note: String) {
            val date = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
            if (!date.isAfter(today)) tx += Transaction(IdGen.next(), amount, type, cat, note, date)
        }
        add(1, 3200.0, TxType.INCOME, "salary", "Monthly salary")
        add(2, 950.0, TxType.EXPENSE, "housing", "Rent")
        add(6, 55.0 + rnd.nextInt(40), TxType.EXPENSE, "bills", "Electricity & water")
        add(9, 15.99, TxType.EXPENSE, "subs", "Netflix")
        add(9, 9.99, TxType.EXPENSE, "subs", "Spotify")
        add(12, 29.0, TxType.EXPENSE, "bills", "Phone & internet")
        if (rnd.nextInt(3) == 0) add(rnd.nextInt(10, 26), 250.0 + rnd.nextInt(600), TxType.INCOME, "freelance", "Side project")
        repeat(rnd.nextInt(18, 30)) {
            val p = everyday[rnd.nextInt(everyday.size)]
            add(rnd.nextInt(1, 29), (rnd.nextInt(p.min * 100, p.max * 100) / 100.0), TxType.EXPENSE, p.cat, p.notes[rnd.nextInt(p.notes.size)])
        }
        add(20, 200.0, TxType.EXPENSE, "savings", "🏖️ Summer trip")
    }

    val thisMonth = YearMonth.from(today)
    fun lastFor(day: Int) = (if (today.dayOfMonth >= day) thisMonth else thisMonth.minusMonths(1)).toString()

    return AppData(
        currency = currency,
        transactions = tx,
        budgets = listOf(Budget("food", 250.0), Budget("shopping", 200.0), Budget("fun", 120.0), Budget("groceries", 300.0)),
        goals = listOf(
            Goal(IdGen.next(), "Summer trip", "🏖️", 2000.0, 1200.0, today.plusMonths(6)),
            Goal(IdGen.next(), "Emergency fund", "🛡️", 6000.0, 2100.0, null),
            Goal(IdGen.next(), "New laptop", "💻", 1400.0, 350.0, today.plusMonths(4)),
        ),
        recurring = listOf(
            Recurring(IdGen.next(), 3200.0, TxType.INCOME, "salary", "Monthly salary", 1, lastFor(1)),
            Recurring(IdGen.next(), 950.0, TxType.EXPENSE, "housing", "Rent", 2, lastFor(2), remindDaysBefore = 3),
            Recurring(IdGen.next(), 15.99, TxType.EXPENSE, "subs", "Netflix", 9, lastFor(9)),
            Recurring(IdGen.next(), 9.99, TxType.EXPENSE, "subs", "Spotify", 9, lastFor(9)),
        ),
        debts = listOf(
            Debt(IdGen.next(), "Sam", 24.5, theyOwe = true, note = "Pizza night", date = today.minusDays(3)),
            Debt(IdGen.next(), "Alex", 40.0, theyOwe = false, note = "Concert tickets", date = today.minusDays(10)),
            Debt(IdGen.next(), "Sam", 15.0, theyOwe = true, note = "Taxi", date = today.minusDays(12)),
        ),
    )
}
