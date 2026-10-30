package com.coinly.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.coinly.app.data.AppData
import com.coinly.app.data.Money
import com.coinly.app.data.computeStats
import java.time.LocalDate
import java.time.YearMonth

/** Home screen widget: today's safe-to-spend, this month's balance, and a button straight to a new expense. */
class CoinlyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, render(context, Store.repo(context).data.value))
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "com.coinly.app.ADD_EXPENSE"

        fun refresh(context: Context, d: AppData) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, CoinlyWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, d))
        }

        private fun render(context: Context, d: AppData): RemoteViews {
            val today = LocalDate.now()
            val stats = computeStats(d, YearMonth.from(today), today)
            val views = RemoteViews(context.packageName, R.layout.widget_coinly)
            if (d.appLock) {
                // Amounts stay hidden on the home screen when the app itself is locked.
                views.setTextViewText(R.id.widget_safe, "•••")
                views.setTextViewText(R.id.widget_month, "Open Coinly to see your month")
            } else {
                views.setTextViewText(R.id.widget_safe, stats.safeToSpendPerDay?.let { Money.format(it, d.currency) } ?: "–")
                val net = stats.income - stats.expense
                val month = if (stats.income > 0 || stats.expense > 0) (if (net >= 0) "+" else "") + Money.format(net, d.currency) else "nothing yet"
                views.setTextViewText(R.id.widget_month, "This month: $month")
            }

            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val add = Intent(open).setAction(ACTION_ADD_EXPENSE)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 1, open, flags))
            views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, 2, add, flags))
            return views
        }
    }
}
