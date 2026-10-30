package com.coinly.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.coinly.app.data.BillReminder
import com.coinly.app.data.BudgetAlert
import com.coinly.app.data.Categories
import com.coinly.app.data.Money
import com.coinly.app.data.pct
import com.coinly.app.data.short

/** Posts a notification when a budget reaches 80% or goes over, and a few days before a bill is due. */
class BudgetNotifier(private val context: Context) {
    init {
        val budgets = NotificationChannel(CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a budget is almost used up or goes over"
        }
        val bills = NotificationChannel(BILLS, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A few days before a recurring bill is due"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(budgets, bills))
    }

    fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(alert: BudgetAlert, currency: String) {
        if (!canNotify()) return
        val s = alert.status
        val fmt = { v: Double -> Money.format(v, currency) }
        val (title, body) = if (alert.level >= 2) {
            "${s.category.emoji} ${s.category.label} is over budget" to "Spent ${fmt(s.spent)} of ${fmt(s.budget.limit)}: ${fmt(-s.remaining)} over this month."
        } else {
            "${s.category.emoji} ${s.category.label} budget almost gone" to "${pct(s.ratio.toDouble())} used, ${fmt(s.remaining)} left this month."
        }
        // One notification per category, so a later alert replaces the earlier one.
        show(CHANNEL, s.category.id.hashCode(), title, body)
    }

    fun postBill(r: BillReminder, currency: String) {
        if (!canNotify()) return
        val bill = r.recurring
        val name = bill.note.ifBlank { Categories.of(bill.categoryId).label }
        val whenDue = when (r.daysLeft) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in ${r.daysLeft} days (${r.due.short()})"
        }
        show(BILLS, ("bill" + bill.id).hashCode(), "🔔 $name is due $whenDue", "${Money.format(bill.amount, currency)} will be booked on ${r.due.short()}.")
    }

    private fun show(channel: String, id: Int, title: String, body: String) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_coinly)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private companion object {
        const val CHANNEL = "budgets"
        const val BILLS = "bills"
    }
}
