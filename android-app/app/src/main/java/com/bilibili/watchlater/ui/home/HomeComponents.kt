package com.bilibili.watchlater.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun PanelTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

fun formatFans(value: Long): String = when {
    value >= 100_000_000 -> String.format(Locale.US, "%.1f", value / 100_000_000.0).removeSuffix(".0") + "亿"
    value >= 10_000 -> String.format(Locale.US, "%.1f", value / 10_000.0).removeSuffix(".0") + "万"
    else -> value.toString()
}
fun formatPublication(value: Long?): String = value?.takeIf { it > 0 }?.let {
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(it))
} ?: "待检查"
fun formatCheck(value: Long): String = if (value <= 0) "尚未检查" else
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
fun minuteLabel(value: Int) = "${(value / 60 % 12).takeIf { it != 0 } ?: 12}:${(value % 60).toString().padStart(2, '0')}"

val SortOptions = linkedMapOf("default" to "默认排序", "fans-desc" to "粉丝数从高到低", "fans-asc" to "粉丝数从低到高",
    "updated-desc" to "更新时间从新到旧", "updated-asc" to "更新时间从旧到新")
val WindowOptions = linkedMapOf(12 to "最近 12 小时", 24 to "最近 24 小时", 48 to "最近 2 天", 72 to "最近 3 天", 168 to "最近 7 天")
