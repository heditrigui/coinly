package com.coinly.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** A bank statement CSV, split into cells. [header] is null when the first row is already data. */
class Statement(val header: List<String>?, val rows: List<List<String>>) {
    val columnCount: Int get() = maxOf(header?.size ?: 0, rows.maxOfOrNull { it.size } ?: 0)

    /** Remembered mappings are keyed by this, so the same bank's next file is recognised. */
    val key: String get() = header?.joinToString("|") { it.trim().lowercase() } ?: "columns:$columnCount"

    fun columnName(i: Int): String = header?.getOrNull(i)?.trim()?.ifBlank { null } ?: "Column ${i + 1}"
    fun column(i: Int): List<String> = rows.map { it.getOrElse(i) { "" } }
}

/** One statement line turned into money, before the user confirms it. */
data class StatementLine(val date: LocalDate, val amount: Double, val type: TxType, val description: String)

/** What the import screen shows for each line: the guessed category and whether Coinly already has it. */
data class ImportCandidate(val line: StatementLine, val categoryId: String, val duplicate: Boolean)

fun parseStatement(text: String): Statement {
    val all = parseCsv(text)
    if (all.isEmpty()) return Statement(null, emptyList())
    // Some banks put the account name or number on a few short lines above the table: start at the first full-width row.
    val width = all.groupingBy { it.size }.eachCount().maxBy { it.value }.key
    val rows = all.drop(all.indexOfFirst { it.size >= width })
    // Header cells are words ("Date", "Amount"); data rows always have digits in them.
    val hasHeader = rows.first().none { cell -> cell.any { it.isDigit() } }
    return if (hasHeader) Statement(rows.first(), rows.drop(1)) else Statement(null, rows)
}

/** Splits CSV text into rows of trimmed cells. Handles quotes, a BOM, and comma, semicolon or tab separators. */
fun parseCsv(text: String): List<List<String>> {
    val src = text.removePrefix("﻿")
    val sample = src.lineSequence().filter { it.isNotBlank() }.take(10).toList()
    if (sample.isEmpty()) return emptyList()
    val sep = listOf(',', ';', '\t').maxBy { c -> sample.sumOf { countOutsideQuotes(it, c) } }

    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    fun endCell() { row += cell.toString().trim(); cell.clear() }
    fun endRow() {
        endCell()
        if (row.any { it.isNotEmpty() }) rows += row
        row = mutableListOf()
    }
    while (i < src.length) {
        val c = src[i]
        when {
            quoted && c == '"' && src.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            quoted -> cell.append(c)
            c == sep -> endCell()
            c == '\r' -> Unit
            c == '\n' -> endRow()
            else -> cell.append(c)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}

private fun countOutsideQuotes(line: String, c: Char): Int {
    var quoted = false
    var n = 0
    for (ch in line) {
        if (ch == '"') quoted = !quoted else if (ch == c && !quoted) n++
    }
    return n
}

/**
 * Reads an amount the way banks write them: "-12.50", "1,234.56", "1.234,56", "(12.50)", "12.50-", "TND 1 200,000".
 * [decimal] is the decimal separator used by the whole column (see [detectDecimal]).
 */
fun parseBankAmount(raw: String, decimal: Char = '.'): Double? {
    val s = raw.trim()
    if (s.none { it.isDigit() }) return null
    val negative = s.startsWith('-') || s.startsWith('−') || s.endsWith('-') || (s.contains('(') && s.contains(')'))
    val number = s.filter { it.isDigit() || it == decimal }.replace(decimal, '.')
    val v = number.toDoubleOrNull() ?: return null
    return if (negative) -v else v
}

/** Guesses whether a column uses "." or "," for decimals by looking at all of its values. */
fun detectDecimal(values: List<String>): Char {
    var dot = 0
    var comma = 0
    var threeDigitTails = 0
    var numbers = 0
    for (v in values) {
        val s = v.filter { it.isDigit() || it == '.' || it == ',' }
        if (s.none { it.isDigit() }) continue
        numbers++
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) dot += 2 else comma += 2
            lastDot >= 0 -> when {
                s.count { it == '.' } > 1 -> comma++
                s.length - lastDot - 1 == 3 -> threeDigitTails++
                else -> dot++
            }
            lastComma >= 0 -> when {
                s.count { it == ',' } > 1 -> dot++
                s.length - lastComma - 1 == 3 -> threeDigitTails++
                else -> comma++
            }
        }
    }
    if (dot == 0 && comma == 0 && numbers > 0 && threeDigitTails == numbers) {
        // Every value looks like "12,500": a currency with three decimals (dinars), not thousands.
        return values.firstOrNull { it.contains(',') }?.let { ',' } ?: '.'
    }
    return if (comma > dot) ',' else '.'
}

/** Date layouts tried in order, so day-first wins over month-first when both fit. */
val datePatterns = listOf(
    "yyyy-M-d", "yyyy/M/d", "yyyy.M.d", "yyyyMMdd",
    "d/M/yyyy", "M/d/yyyy", "d.M.yyyy", "d-M-yyyy", "M-d-yyyy",
    "d/M/yy", "M/d/yy", "d.M.yy", "d-M-yy",
    "d MMM yyyy", "d-MMM-yyyy", "MMM d, yyyy", "MMM d yyyy", "d MMM yy", "d-MMM-yy",
)

private val formatters = HashMap<String, DateTimeFormatter>()

private fun formatter(pattern: String): DateTimeFormatter = synchronized(formatters) {
    formatters.getOrPut(pattern) { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH) }
}

private val timePart = Regex("[ T]\\d{1,2}:\\d{2}.*$")

fun parseBankDate(raw: String, pattern: String): LocalDate? =
    runCatching { LocalDate.parse(raw.trim().replace(timePart, ""), formatter(pattern)) }.getOrNull()

/** The layout that reads the most values of a date column, or null when none reads any. */
fun detectDatePattern(values: List<String>): String? {
    val sample = values.filter { it.isNotBlank() }.take(200)
    if (sample.isEmpty()) return null
    return datePatterns
        .map { p -> p to sample.count { parseBankDate(it, p) != null } }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first
}

private val dateWords = listOf("date", "datum", "fecha", "posted", "booking", "valeur", "jour", "day")
private val descWords = listOf("description", "libell", "label", "details", "detail", "memo", "payee", "narrative", "merchant", "concept", "operation", "opération", "name", "note", "reference", "text")
private val amountWords = listOf("amount", "montant", "importe", "betrag", "sum", "value")
private val outWords = listOf("debit", "débit", "withdrawal", "paid out", "money out", "out", "dépense", "sortie")
private val inWords = listOf("credit", "crédit", "deposit", "paid in", "money in", "in", "recette", "entrée")

/** A first guess at the columns, from the header names when there are some and from the values otherwise. */
fun guessMapping(st: Statement): CsvMapping? {
    val n = st.columnCount
    if (n < 2 || st.rows.isEmpty()) return null
    val names = (0 until n).map { st.columnName(it).lowercase() }
    fun find(words: List<String>, exclude: Set<Int> = emptySet()) = words.firstNotNullOfOrNull { w ->
        names.indices.firstOrNull { it !in exclude && (names[it] == w || names[it].startsWith("$w ") || (w.length > 3 && names[it].contains(w))) }
    }

    val datePatternsByCol = (0 until n).associateWith { detectDatePattern(st.column(it)) }
    val dateReads = (0 until n).associateWith { c -> datePatternsByCol[c]?.let { p -> st.column(c).count { parseBankDate(it, p) != null } } ?: 0 }
    val dateCol = (if (st.header != null) find(dateWords)?.takeIf { dateReads[it]!! > 0 } else null)
        ?: dateReads.maxBy { it.value }.takeIf { it.value > 0 }?.key
        ?: return null

    fun numeric(c: Int) = st.column(c).let { vals -> val dec = detectDecimal(vals); vals.count { parseBankAmount(it, dec) != null } }
    val outCol = if (st.header != null) find(outWords, setOf(dateCol)) else null
    val inCol = if (st.header != null) find(inWords, setOfNotNull(dateCol, outCol)) else null
    val twoColumns = outCol != null && inCol != null
    val amountCol = if (twoColumns) outCol!! else {
        (if (st.header != null) find(amountWords, setOf(dateCol)) else null)
            ?: (0 until n).filter { it != dateCol && numeric(it) > st.rows.size / 2 }.firstOrNull()
            ?: return null
    }
    val taken = setOfNotNull(dateCol, amountCol, if (twoColumns) inCol else null)
    val descCol = (if (st.header != null) find(descWords, taken) else null)
        ?: (0 until n).filter { it !in taken }.maxByOrNull { c -> st.column(c).sumOf { v -> v.count { it.isLetter() } } }
        ?: return null

    val amounts = st.column(amountCol).let { vals -> val dec = detectDecimal(vals); vals.mapNotNull { parseBankAmount(it, dec) } }
    return CsvMapping(
        dateCol = dateCol,
        descCol = descCol,
        amountCol = amountCol,
        inCol = if (twoColumns) inCol else null,
        // A single amount column with no negative numbers at all is a list of expenses written as positive numbers.
        expensesPositive = !twoColumns && amounts.isNotEmpty() && amounts.none { it < 0 },
        datePattern = datePatternsByCol[dateCol]!!,
    )
}

/** Turns the statement rows into money using [m]. Rows without a readable date or a non-zero amount are left out. */
fun readStatement(st: Statement, m: CsvMapping): List<StatementLine> {
    val outDecimal = detectDecimal(st.column(m.amountCol))
    val inDecimal = m.inCol?.let { detectDecimal(st.column(it)) } ?: outDecimal
    return st.rows.mapNotNull { row ->
        val date = parseBankDate(row.getOrElse(m.dateCol) { "" }, m.datePattern) ?: return@mapNotNull null
        val description = cleanDescription(row.getOrElse(m.descCol) { "" })
        val (amount, type) = if (m.inCol != null) {
            val moneyIn = parseBankAmount(row.getOrElse(m.inCol) { "" }, inDecimal)?.let(::abs) ?: 0.0
            val moneyOut = parseBankAmount(row.getOrElse(m.amountCol) { "" }, outDecimal)?.let(::abs) ?: 0.0
            when {
                moneyIn > 0 -> moneyIn to TxType.INCOME
                moneyOut > 0 -> moneyOut to TxType.EXPENSE
                else -> return@mapNotNull null
            }
        } else {
            val v = parseBankAmount(row.getOrElse(m.amountCol) { "" }, outDecimal) ?: return@mapNotNull null
            if (v == 0.0) return@mapNotNull null
            val expense = if (m.expensesPositive) v > 0 else v < 0
            abs(v) to if (expense) TxType.EXPENSE else TxType.INCOME
        }
        StatementLine(date, amount, type, description)
    }
}

fun cleanDescription(s: String): String = s.trim().replace(Regex("\\s+"), " ").take(80)

private fun dedupeKey(date: LocalDate, type: TxType, amount: Double, note: String) =
    listOf(date.toEpochDay(), type, (amount * 100).roundToLong(), note.trim().lowercase().replace(Regex("\\s+"), " "))

/**
 * Lines Coinly already has: same date, amount, type and description. Matching is one to one,
 * so two identical coffees on one day both import the first time and neither does the second time.
 */
fun findDuplicates(lines: List<StatementLine>, existing: List<Transaction>): List<Boolean> {
    val have = existing.groupingBy { dedupeKey(it.date, it.type, it.amount, it.note) }.eachCount().toMutableMap()
    return lines.map { l ->
        val k = dedupeKey(l.date, l.type, l.amount, l.description)
        val left = have[k] ?: 0
        if (left > 0) { have[k] = left - 1; true } else false
    }
}

private val stopWords = setOf(
    "the", "and", "for", "from", "with", "payment", "paiement", "purchase", "achat", "card", "carte", "cb", "pos",
    "debit", "credit", "transfer", "virement", "vir", "prlv", "sepa", "ref", "fact", "date", "online", "www", "com",
)

/** Lowercase words of a description, without numbers, card noise or very short words. */
fun keywords(description: String): List<String> =
    description.lowercase().split(Regex("[^\\p{L}\\p{N}&]+"))
        .filter { w -> w.length >= 3 && w !in stopWords && w.any { it.isLetter() } }

/** Built-in hints for well-known shops and words, used when the user's own history has no answer. First match wins. */
private val keywordRules: List<Pair<String, String>> = listOf(
    "uber eats" to "food", "deliveroo" to "food", "glovo" to "food", "doordash" to "food", "just eat" to "food",
    "restaurant" to "food", "resto" to "food", "cafe" to "food", "café" to "food", "coffee" to "food", "starbucks" to "food",
    "mcdonald" to "food", "kfc" to "food", "pizza" to "food", "burger" to "food", "snack" to "food",
    "carrefour" to "groceries", "monoprix" to "groceries", "lidl" to "groceries", "aldi" to "groceries", "tesco" to "groceries",
    "walmart" to "groceries", "auchan" to "groceries", "spar" to "groceries", "geant" to "groceries", "aziza" to "groceries",
    "supermarket" to "groceries", "supermarche" to "groceries", "grocery" to "groceries", "bakery" to "groceries", "boulangerie" to "groceries",
    "uber" to "transport", "bolt" to "transport", "lyft" to "transport", "taxi" to "transport", "fuel" to "transport", "petrol" to "transport",
    "carburant" to "transport", "parking" to "transport", "metro" to "transport", "train" to "transport", "sncf" to "transport", "bus" to "transport",
    "rent" to "housing", "loyer" to "housing", "mortgage" to "housing",
    "electric" to "bills", "electricity" to "bills", "steg" to "bills", "sonede" to "bills", "water" to "bills", "internet" to "bills",
    "telecom" to "bills", "ooredoo" to "bills", "orange" to "bills", "vodafone" to "bills", "verizon" to "bills", "insurance" to "bills", "assurance" to "bills",
    "netflix" to "subs", "spotify" to "subs", "youtube" to "subs", "disney" to "subs", "icloud" to "subs", "apple.com" to "subs",
    "prime video" to "subs", "subscription" to "subs", "abonnement" to "subs",
    "amazon" to "shopping", "aliexpress" to "shopping", "jumia" to "shopping", "ebay" to "shopping", "zara" to "shopping", "ikea" to "shopping", "decathlon" to "shopping",
    "pharma" to "health", "pharmacie" to "health", "doctor" to "health", "clinic" to "health", "clinique" to "health", "hospital" to "health", "dentist" to "health", "gym" to "health",
    "cinema" to "fun", "steam" to "fun", "playstation" to "fun", "concert" to "fun",
    "airbnb" to "travel", "hotel" to "travel", "booking.com" to "travel", "airline" to "travel", "ryanair" to "travel", "tunisair" to "travel", "expedia" to "travel",
    "school" to "education", "university" to "education", "udemy" to "education", "coursera" to "education", "ecole" to "education",
    "salary" to "salary", "salaire" to "salary", "payroll" to "salary", "wage" to "salary", "paie" to "salary",
    "upwork" to "freelance", "fiverr" to "freelance", "invoice" to "freelance", "freelance" to "freelance",
    "dividend" to "invest", "interest" to "invest", "interets" to "invest", "intérêts" to "invest",
    "bonus" to "bonus", "gift" to "gift", "cadeau" to "gift",
)

/**
 * Suggests a category for a bank description from what the user did before: first the same description,
 * then the words it shares with past notes, then a few built-in keywords.
 */
class CategoryGuesser(history: List<Transaction>) {
    private val exact = HashMap<Pair<TxType, String>, String>()
    private val words = HashMap<Pair<TxType, String>, MutableMap<String, Int>>()

    init {
        history.filter { it.note.isNotBlank() && it.categoryId != "savings" }
            .groupBy { it.type to keywords(it.note).joinToString(" ") }
            .forEach { (key, list) -> if (key.second.isNotEmpty()) exact[key] = list.groupingBy { it.categoryId }.eachCount().maxBy { it.value }.key }
        history.filter { it.note.isNotBlank() && it.categoryId != "savings" }.forEach { t ->
            keywords(t.note).toSet().forEach { w ->
                val counts = words.getOrPut(t.type to w) { HashMap() }
                counts[t.categoryId] = (counts[t.categoryId] ?: 0) + 1
            }
        }
    }

    fun guess(description: String, type: TxType): String {
        val kw = keywords(description)
        exact[type to kw.joinToString(" ")]?.let { return it }

        val votes = HashMap<String, Int>()
        kw.forEach { w -> words[type to w]?.forEach { (cat, n) -> votes[cat] = (votes[cat] ?: 0) + n } }
        votes.maxByOrNull { it.value }?.let { return it.key }

        val text = " " + description.lowercase().replace(Regex("[^\\p{L}\\p{N}&.]+"), " ") + " "
        keywordRules.firstOrNull { (word, cat) -> Categories.of(cat).type == type && containsWord(text, word) }?.let { return it.second }

        return if (type == TxType.INCOME) "other_in" else "other"
    }
}

/** [word] at the start of a word of [text]; short words ("bus", "gym") must be the whole word, so "business" is not "bus". */
private fun containsWord(text: String, word: String): Boolean {
    var i = text.indexOf(" $word")
    while (i >= 0) {
        val end = i + 1 + word.length
        if (word.length >= 5 || end >= text.length || !text[end].isLetter()) return true
        i = text.indexOf(" $word", i + 1)
    }
    return false
}

/** Everything the import screen needs: each line with a suggested category and its duplicate flag. */
fun prepareImport(lines: List<StatementLine>, d: AppData): List<ImportCandidate> {
    val guesser = CategoryGuesser(d.transactions)
    val dupes = findDuplicates(lines, d.transactions)
    return lines.mapIndexed { i, l -> ImportCandidate(l, guesser.guess(l.description, l.type), dupes[i]) }
}
