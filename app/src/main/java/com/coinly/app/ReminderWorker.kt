package com.coinly.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coinly.app.data.AppData
import com.coinly.app.data.billReminders
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Once a day: bill reminders, and a fresh "safe to spend today" on the widget. Runs on the phone only, no network. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val d = Store.repo(applicationContext).data.value
        BillCheck.run(applicationContext, d)
        CoinlyWidget.refresh(applicationContext, d)
        return Result.success()
    }

    companion object {
        /** Daily, around 9 in the morning. Keeps the existing schedule if there is one. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(9, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Notifies each upcoming bill once per due date. */
object BillCheck {
    fun run(context: Context, d: AppData, today: LocalDate = LocalDate.now()) {
        val due = billReminders(d, today)
        val prefs = context.getSharedPreferences("coinly", Context.MODE_PRIVATE)
        val sent = prefs.getStringSet("billsNotified", emptySet()).orEmpty()
        // Forget occurrences whose due date has passed, so the set stays small.
        val kept = sent.filter { key -> runCatching { !LocalDate.parse(key.substringAfter('@')).isBefore(today) }.getOrDefault(false) }.toMutableSet()
        val notifier = BudgetNotifier(context)
        due.filter { it.key !in kept }.forEach { r ->
            if (notifier.canNotify()) {
                notifier.postBill(r, d.currency)
                kept += r.key
            }
        }
        if (kept != sent) prefs.edit().putStringSet("billsNotified", kept).apply()
    }
}
