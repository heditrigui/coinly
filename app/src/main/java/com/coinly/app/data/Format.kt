package com.coinly.app.data

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Money {
    val currencies = listOf("$", "€", "£", "DT", "TND", "MAD", "DZD", "₹", "¥", "CHF", "CAD")

    fun format(v: Double, currency: String, decimals: Boolean = true): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = if (decimals) 2 else 0
            maximumFractionDigits = if (decimals) 2 else 0
        }
        return place(nf.format(abs(v)), v < 0, currency)
    }

    /** Short form for charts and tight spaces: 950, 12.4k, 1.2M. */
    fun compact(v: Double, currency: String): String {
        val a = abs(v)
        val s = when {
            a >= 1_000_000 -> String.format(Locale.US, "%.1fM", a / 1_000_000)
            a >= 10_000 -> String.format(Locale.US, "%.1fk", a / 1000)
            else -> NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(a)
        }
        return place(s, v < 0, currency)
    }

    private fun place(number: String, negative: Boolean, currency: String): String {
        val sign = if (negative) "-" else ""
        return if (currency.length <= 1) "$sign$currency$number" else "$sign$number $currency"
    }
}

fun pct(x: Double): String = "${(x * 100).roundToInt()}%"

fun parseAmount(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val dayMonth = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val weekdayDay = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val fullDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

fun YearMonth.label(): String = format(monthYear)
fun YearMonth.shortLabel(): String = format(shortMonth)
fun LocalDate.short(): String = format(dayMonth)
fun LocalDate.full(): String = format(fullDate)

fun LocalDate.friendly(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> if (year == today.year) format(weekdayDay) else format(fullDate)
}
