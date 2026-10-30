# Coinly

A private, offline personal finance app for Android. Log what comes in and goes out, and Coinly shows where your money goes, how healthy your month looks, and what to change.

**Try it:** grab the APK from the [latest release](../../releases/latest) and tap *Explore with demo data*. No account, no ads, no internet permission.

<p align="center">
  <img src="docs/home.jpg" width="240" alt="Home: balance, income and spending, safe-to-spend per day, health score">
  <img src="docs/insights.jpg" width="240" alt="Insights: health score and smart tips">
  <img src="docs/plan.jpg" width="240" alt="Plan: monthly budgets per category">
</p>
<p align="center"><sub>Screenshots use the built-in demo data.</sub></p>

- **Home**: total balance, this month's income and spending, and a "safe to spend per day" figure for the rest of the month
- **Financial health score** (0–100) from your savings rate, budgets, spending trend and no-spend days
- **Smart tips**: overspending pace, budgets running out, categories that jumped vs the same point last month, weekend vs weekday spending, subscriptions per year, small purchases that add up…
- **Charts**: spending by category, 6-month cash flow, spending pace vs last month, and a spending calendar heatmap
- **Quick entry**: tap +, type the amount, save. The date starts on today, the category on the one you used last, and the expenses you repeat (your usual coffee, bus fare…) show up as one-tap shortcuts
- **Budgets** per category. While you type an expense you see what it does to that budget ("After this: 92% of the budget, 24.00 left"), and a notification tells you when a budget reaches 80% and when it goes over
- **Savings goals** with target dates and the monthly amount needed to reach them
- **Recurring entries** (salary, rent, subscriptions) booked automatically every month, catching up on missed months, with an optional **bill reminder** 1, 3 or 7 days before each one is due
- **Debts and shared expenses**: track who owes you and whom you owe, settle up in full or in part, and split a bill as you add it (only your share counts as spending)
- **Your year in money**: totals, savings rate, top categories, best and worst months, and a comparison with the same period last year
- **Bank statement import** from CSV: pick the date, description and amount columns once (Coinly remembers them for that bank), duplicates are skipped, and categories are suggested from your past entries and common shop names
- **Home screen widget** with today's safe-to-spend, this month's balance and a + button that opens a new expense
- **App lock** with your fingerprint, face or screen lock, which also hides Coinly in the recent apps screen
- **Backup and restore** to a file you keep, to move to a new phone or survive a reinstall
- **History** with search and filters, **CSV export** of transactions and debts for Excel or Google Sheets
- Light / dark / system theme, and a choice of currencies ($, €, £, DT, TND, MAD, DZD, ₹, ¥, ₪, CHF, CAD)
- **Demo data** to explore every screen before entering your own

## Privacy

No account, no ads, no tracking, and no internet permission. Your data stays in a single JSON file in the app's private storage on your phone. Bank statements are read from files you pick; Coinly never connects to your bank.

## Backups

- **Settings → Back up to a file** saves everything (transactions, budgets, goals, recurring entries, debts, settings) to one `.json` file. Save it to Google Drive, send it to yourself, or copy it to a computer. Settings shows the date of your last backup.
- **Settings → Restore from a backup** on any phone brings it all back. Coinly shows what's in the file and asks before replacing anything.
- Android's own backup also covers Coinly: if Google backup is on, your data comes back when you set up a new phone with the same account.

Uninstalling the app deletes its data, so make a backup first.

## Importing a bank statement

Export your account history as CSV from your bank's website or app, then use **Settings → Import a bank statement**.

- Coinly guesses the date, description and amount columns. Banks that use separate "debit" and "credit" columns work too, and so do amounts like `-12.50`, `1.234,56` or `(12.50)`.
- Check the guess the first time. Coinly remembers the columns for the next file with the same header row.
- Lines already in Coinly (same date, amount and description) are unticked, so importing an overlapping statement twice is safe.
- Tap a line's icon to change its category. Lines with the same description change with it.

## Install

Download the APK from the [latest release](../../releases/latest), open it on your Android phone (Android 8.0 or newer) and allow installing from your browser or file manager.

## Build

You need JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleRelease    # APK in app/build/outputs/apk/release/
./gradlew test               # shortcuts, budget alerts, backups, statement import, debts, bill reminders, yearly report
```

Built with Kotlin and Jetpack Compose.

## Project layout

```
app/src/main/java/com/coinly/app/
  data/          models, JSON storage and backups, recurring entries, stats and insights,
                 quick-entry shortcuts and budget alerts, bank statement import,
                 debts and splits, bill reminders, yearly report, demo data
  BudgetNotifier.kt  budget and bill notifications
  ReminderWorker.kt  daily bill check and widget refresh
  CoinlyWidget.kt    home screen widget
  AppLock.kt         fingerprint / face / screen lock
  ui/screens/    Home, History, Insights, Plan, Settings, transaction editor,
                 statement import, year in money, lock screen
  ui/components/ charts, cards and dialogs
```

## License

[MIT](LICENSE)
