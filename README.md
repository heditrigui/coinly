# Coinly

A private, offline personal finance app for Android. Log what comes in and goes out, and Coinly shows where your money goes, how healthy your month looks, and what to change.

- **Home**: total balance, this month's income and spending, and a "safe to spend per day" figure for the rest of the month
- **Financial health score** (0–100) from your savings rate, budgets, spending trend and no-spend days
- **Smart tips**: overspending pace, budgets running out, categories that jumped vs the same point last month, weekend vs weekday spending, subscriptions per year, small purchases that add up…
- **Charts**: spending by category, 6-month cash flow, spending pace vs last month, and a spending calendar heatmap
- **Budgets** per category, with warnings before you go over
- **Savings goals** with target dates and the monthly amount needed to reach them
- **Recurring entries** (salary, rent, subscriptions) booked automatically every month, catching up on missed months
- **History** with search and filters, **CSV export** for Excel or Google Sheets
- Light / dark / system theme, and a choice of currencies ($, €, £, DT, TND, MAD, DZD, ₹, ¥, CHF, CAD)
- **Demo data** to explore every screen before entering your own

## Privacy

No account, no ads, no tracking, and no internet permission. Your data stays in a single JSON file in the app's private storage on your phone. Export to CSV whenever you want a copy.

## Install

Download the APK from the [latest release](../../releases/latest), open it on your Android phone (Android 8.0 or newer) and allow installing from your browser or file manager.

## Build

You need JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleRelease    # APK in app/build/outputs/apk/release/
```

Built with Kotlin and Jetpack Compose.

## Project layout

```
app/src/main/java/com/coinly/app/
  data/          models, JSON storage, recurring entries, stats and insights, demo data
  ui/screens/    Home, History, Insights, Plan, Settings, transaction editor
  ui/components/ charts, cards and dialogs
```

## License

[MIT](LICENSE)
