package com.coinly.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Keeps everything in memory and mirrors it to a single JSON file in the app's private storage. */
class FinanceRepository(private val file: File) {
    private val state = MutableStateFlow(load())
    val data: StateFlow<AppData> = state.asStateFlow()

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    fun update(transform: (AppData) -> AppData) {
        var changed = false
        state.update { old -> transform(old).also { changed = it !== old } }
        if (changed) io.launch { writeLock.withLock { persist(state.value) } }
    }

    private fun load(): AppData = try {
        if (file.exists()) JsonCodec.decode(file.readText()) else AppData()
    } catch (e: Exception) {
        // Keep a copy of an unreadable file instead of silently losing it.
        runCatching { file.copyTo(File(file.path + ".corrupt"), overwrite = true) }
        AppData()
    }

    private fun persist(d: AppData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JsonCodec.encode(d))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/** Books every recurring entry that is due up to [today], catching up on missed months. */
fun applyRecurring(d: AppData, today: LocalDate): AppData {
    if (d.recurring.isEmpty()) return d
    val current = YearMonth.from(today)
    val booked = mutableListOf<Transaction>()
    val updated = d.recurring.map { r ->
        var ym = r.lastApplied?.let { YearMonth.parse(it).plusMonths(1) } ?: current
        var last = r.lastApplied
        while (!ym.isAfter(current)) {
            val date = ym.atDay(r.dayOfMonth.coerceIn(1, ym.lengthOfMonth()))
            if (date.isAfter(today)) break
            booked += Transaction(IdGen.next(), r.amount, r.type, r.categoryId, r.note, date)
            last = ym.toString()
            ym = ym.plusMonths(1)
        }
        r.copy(lastApplied = last)
    }
    return if (booked.isEmpty()) d else d.copy(transactions = d.transactions + booked, recurring = updated)
}

object JsonCodec {
    fun encode(d: AppData): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("currency", d.currency)
        root.put("theme", d.theme)
        root.put("transactions", JSONArray().apply {
            d.transactions.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("amount", t.amount)
                    put("type", t.type.name)
                    put("cat", t.categoryId)
                    put("note", t.note)
                    put("date", t.date.toEpochDay())
                })
            }
        })
        root.put("budgets", JSONArray().apply {
            d.budgets.forEach { b -> put(JSONObject().put("cat", b.categoryId).put("limit", b.limit)) }
        })
        root.put("goals", JSONArray().apply {
            d.goals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("emoji", g.emoji)
                    put("target", g.target)
                    put("saved", g.saved)
                    g.deadline?.let { put("deadline", it.toEpochDay()) }
                })
            }
        })
        root.put("recurring", JSONArray().apply {
            d.recurring.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("amount", r.amount)
                    put("type", r.type.name)
                    put("cat", r.categoryId)
                    put("note", r.note)
                    put("day", r.dayOfMonth)
                    r.lastApplied?.let { put("last", it) }
                })
            }
        })
        return root.toString()
    }

    fun decode(s: String): AppData {
        val r = JSONObject(s)
        return AppData(
            currency = r.optString("currency", "$"),
            theme = r.optString("theme", "system"),
            transactions = r.optJSONArray("transactions").objects().map {
                Transaction(
                    id = it.getLong("id"),
                    amount = it.getDouble("amount"),
                    type = TxType.valueOf(it.getString("type")),
                    categoryId = it.getString("cat"),
                    note = it.optString("note"),
                    date = LocalDate.ofEpochDay(it.getLong("date")),
                )
            },
            budgets = r.optJSONArray("budgets").objects().map { Budget(it.getString("cat"), it.getDouble("limit")) },
            goals = r.optJSONArray("goals").objects().map {
                Goal(
                    id = it.getLong("id"),
                    name = it.getString("name"),
                    emoji = it.optString("emoji", "🎯"),
                    target = it.getDouble("target"),
                    saved = it.optDouble("saved", 0.0),
                    deadline = if (it.has("deadline")) LocalDate.ofEpochDay(it.getLong("deadline")) else null,
                )
            },
            recurring = r.optJSONArray("recurring").objects().map {
                Recurring(
                    id = it.getLong("id"),
                    amount = it.getDouble("amount"),
                    type = TxType.valueOf(it.getString("type")),
                    categoryId = it.getString("cat"),
                    note = it.optString("note"),
                    dayOfMonth = it.getInt("day"),
                    lastApplied = if (it.has("last")) it.getString("last") else null,
                )
            },
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}

fun buildCsv(d: AppData): String {
    fun esc(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    return buildString {
        appendLine("date,type,category,amount,note")
        d.transactions.sortedBy { it.date }.forEach { t ->
            appendLine(listOf(t.date.toString(), t.type.name.lowercase(), esc(t.category.label), "%.2f".format(java.util.Locale.US, t.amount), esc(t.note)).joinToString(","))
        }
    }
}
