package com.coinly.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinly.app.data.Money
import com.coinly.app.data.MonthBar
import com.coinly.app.data.shortLabel
import com.coinly.app.ui.theme.FinColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

@Composable
private fun rememberEntrance(key: Any?): State<Float> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key) {
        a.snapTo(0f)
        a.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    return a.asState()
}

data class Slice(val color: Color, val value: Float)

@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 20.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val p by rememberEntrance(slices)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val d = size.minDimension - stroke
            val arcSize = Size(d, d)
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            val gap = if (slices.size > 1) 2.5f else 0f
            var start = -90f
            slices.forEach { s ->
                val sweep = s.value / total * 360f * p
                drawArc(s.color, start + gap / 2f, (sweep - gap).coerceAtLeast(0.1f), false, topLeft, arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun ScoreGauge(score: Int, modifier: Modifier = Modifier, stroke: Dp = 10.dp, textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall) {
    val anim by animateFloatAsState(if (score < 0) 0f else score / 100f, tween(1100, easing = FastOutSlowInEasing), label = "score")
    val color = scoreColor(score)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = size.minDimension - s
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(track, 135f, 270f, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
            if (anim > 0f) drawArc(color, 135f, 270f * anim, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        Text(if (score < 0) "–" else "${(anim * 100).toInt()}", style = textStyle, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Grouped income/expense bars, one group per month. The [selected] month is drawn at full strength. */
@Composable
fun IncomeExpenseBars(bars: List<MonthBar>, selected: YearMonth, modifier: Modifier = Modifier) {
    val p by rememberEntrance(bars)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val peak = (bars.maxOfOrNull { max(it.income, it.expense) } ?: 0.0).coerceAtLeast(1.0)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }
            if (bars.isEmpty()) return@Canvas
            val groupW = size.width / bars.size
            val barW = min(groupW * 0.3f, 14.dp.toPx())
            val gap = 3.dp.toPx()
            bars.forEachIndexed { i, b ->
                val cx = groupW * i + groupW / 2f
                val alpha = if (b.month == selected) 1f else 0.4f
                val hi = (b.income / peak).toFloat() * size.height * p
                val he = (b.expense / peak).toFloat() * size.height * p
                drawRoundRect(FinColors.Income.copy(alpha = alpha), Offset(cx - gap / 2f - barW, size.height - hi), Size(barW, hi), CornerRadius(barW / 2f))
                drawRoundRect(FinColors.Expense.copy(alpha = alpha), Offset(cx + gap / 2f, size.height - he), Size(barW, he), CornerRadius(barW / 2f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { b ->
                val sel = b.month == selected
                Text(
                    b.month.shortLabel(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Running total of spending through the month (solid) against last month (dashed). */
@Composable
fun CumulativeChart(current: List<Double>, previous: List<Double>, days: Int, modifier: Modifier = Modifier) {
    val p by rememberEntrance(current)
    val line = MaterialTheme.colorScheme.primary
    val prevColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dotCenter = MaterialTheme.colorScheme.surface
    val peak = max(current.maxOrNull() ?: 0.0, previous.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(i: Int) = if (days <= 1) 0f else w * i / (days - 1).toFloat()
        fun y(v: Double) = h - (v / peak).toFloat() * h * 0.92f
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        for (i in 0..3) {
            val gy = h * i / 3f
            drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        }
        clipRect(0f, 0f, w * p, h) {
            if (previous.size > 1) {
                val path = Path()
                previous.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                drawPath(path, prevColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))
            }
            if (current.isNotEmpty()) {
                val path = Path()
                current.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                val fill = Path().apply {
                    addPath(path)
                    lineTo(x(current.lastIndex), h)
                    lineTo(x(0), h)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), Color.Transparent)))
                drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val end = Offset(x(current.lastIndex), y(current.last()))
                drawCircle(line, 6.dp.toPx(), end)
                drawCircle(dotCenter, 3.dp.toPx(), end)
            }
        }
    }
}

/** Calendar grid where darker days mean more spending. Tap a day to see its total. */
@Composable
fun SpendingCalendar(month: YearMonth, daily: List<Double>, currency: String) {
    val peak = daily.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val offset = month.atDay(1).dayOfWeek.value - 1
    val length = month.lengthOfMonth()
    val rows = (offset + length + 6) / 7
    val today = LocalDate.now()
    var selected by remember(month) { mutableStateOf<Int?>(null) }
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - offset
                    if (idx in 0 until length) {
                        val v = daily.getOrElse(idx) { 0.0 }
                        val intensity = (v / peak).toFloat()
                        val date = month.atDay(idx + 1)
                        val bg = if (v > 0) FinColors.Expense.copy(alpha = 0.15f + 0.75f * intensity) else empty
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .clickable { selected = idx },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (date == today || selected == idx) FontWeight.Bold else FontWeight.Normal,
                                color = if (intensity > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        val sel = selected
        Text(
            if (sel == null) "Tap a day to see what you spent"
            else {
                val v = daily.getOrElse(sel) { 0.0 }
                val label = month.atDay(sel + 1).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                if (v > 0) "$label: ${Money.format(v, currency)}" else "$label: no spending 🎉"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
