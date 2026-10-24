package com.coinly.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinly.app.data.Category
import com.coinly.app.data.Money
import com.coinly.app.data.Tone
import com.coinly.app.data.Transaction
import com.coinly.app.data.TxType
import com.coinly.app.data.friendly
import com.coinly.app.data.label
import com.coinly.app.ui.theme.FinColors
import java.time.YearMonth

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun CardTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    val canNext = month < YearMonth.now()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }
        Text(
            if (month == YearMonth.now()) "This month · ${month.label()}" else month.label(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canNext) { Icon(Icons.Outlined.ChevronRight, "Next month") }
    }
}

@Composable
fun CategoryBadge(category: Category, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(category.color).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.emoji, fontSize = (size.value * 0.45f).sp)
    }
}

@Composable
fun TransactionRow(tx: Transaction, currency: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(tx.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${tx.category.label} · ${tx.date.friendly()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        val income = tx.type == TxType.INCOME
        Text(
            (if (income) "+" else "−") + Money.format(tx.amount, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (income) FinColors.Income else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A simple rounded progress bar; turns amber/red as it fills when [warnColors] is on. */
@Composable
fun ProgressBar(ratio: Float, modifier: Modifier = Modifier, color: Color? = null, warnColors: Boolean = false, height: Dp = 8.dp) {
    val anim by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(800), label = "progress")
    val c = color ?: when {
        !warnColors -> MaterialTheme.colorScheme.primary
        ratio > 1f -> FinColors.Expense
        ratio >= 0.8f -> FinColors.Warn
        else -> FinColors.Income
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(anim)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(c),
        )
    }
}

@Composable
fun AnimatedAmount(value: Double, currency: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(800, easing = FastOutSlowInEasing)) }
    val shown = if (anim.isRunning) anim.value.toDouble() else value
    Text(Money.format(shown, currency), style = style, color = color, modifier = modifier, maxLines = 1)
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, emoji: String? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (emoji != null) Text(emoji, fontSize = 20.sp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun toneColor(t: Tone): Color = when (t) {
    Tone.GOOD -> FinColors.Income
    Tone.WARN -> FinColors.Warn
    Tone.BAD -> FinColors.Expense
    Tone.NEUTRAL -> FinColors.HeroEnd
}

fun scoreColor(score: Int): Color = when {
    score < 0 -> Color.Gray
    score < 40 -> FinColors.Expense
    score < 70 -> FinColors.Warn
    else -> FinColors.Income
}

fun scoreLabel(score: Int): String = when {
    score < 0 -> "Not enough data"
    score < 40 -> "Needs attention"
    score < 60 -> "Fair"
    score < 80 -> "Good"
    else -> "Excellent"
}
