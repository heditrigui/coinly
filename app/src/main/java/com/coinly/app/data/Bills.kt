package com.coinly.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A recurring bill coming up on [due], [daysLeft] days from today. */
data class BillReminder(val recurring: Recurring, val due: LocalDate, val daysLeft: Int) {
    /** Identifies this occurrence, so each one is notified once. */
    val key: String get() = "${recurring.id}@$due"
}

/** The next day [r] is booked, after [today]. Short months use their last day, like [applyRecurring]. */
fun nextDue(r: Recurring, today: LocalDate): LocalDate {
    var ym = YearMonth.from(today)
    while (true) {
        val date = ym.atDay(r.dayOfMonth.coerceIn(1, ym.lengthOfMonth()))
        if (date.isAfter(today)) return date
        ym = ym.plusMonths(1)
    }
}

/** Recurring expenses with a reminder whose next due date is within their reminder window, soonest first. */
fun billReminders(d: AppData, today: LocalDate): List<BillReminder> = d.recurring
    .filter { it.type == TxType.EXPENSE && (it.remindDaysBefore ?: 0) > 0 }
    .map { r -> nextDue(r, today).let { BillReminder(r, it, ChronoUnit.DAYS.between(today, it).toInt()) } }
    .filter { it.daysLeft <= it.recurring.remindDaysBefore!! }
    .sortedBy { it.due }
