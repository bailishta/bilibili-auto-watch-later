package com.bilibili.watchlater.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilibili.watchlater.data.local.entity.PublicationEntity
import com.bilibili.watchlater.data.repository.currentWeekStart
import com.bilibili.watchlater.ui.components.CreatorAvatar
import com.bilibili.watchlater.ui.theme.Coral
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(creators: List<CreatorUi>, records: List<PublicationEntity>, historyWeeks: Int) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    val boundedOffset = offset.coerceIn(-(historyWeeks - 1), 0)
    val start = currentWeekStart().plusWeeks(boundedOffset.toLong())
    LaunchedEffect(historyWeeks) {
        if (boundedOffset != offset) { offset = boundedOffset; selectedDay = null }
    }
    val summary = remember(creators, records, start) { summarizeActivity(creators, records, start) }
    var period by rememberSaveable(start.toEpochDay(), selectedDay) { mutableIntStateOf(0) }
    val day = selectedDay?.let { summary.days[it] }
    val minutes = day?.minutes?.filterKeys { it / 720 == period }.orEmpty()
    val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    BackHandler(selectedDay != null) { selectedDay = null }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Panel(Modifier.animateContentSize(tween(320, easing = FastOutSlowInEasing))) {
                PanelTitle("更新分布")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${start.format(DateTimeFormatter.ofPattern("yyyy年 M月d日"))} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("M月d日"))}" +
                        if (boundedOffset == 0) " · 本周" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { offset--; selectedDay = null }, enabled = boundedOffset > -(historyWeeks - 1), modifier = Modifier.weight(1f)) { Text("上一周") }
                        TextButton(onClick = { offset = 0; selectedDay = null }, modifier = Modifier.weight(1f)) { Text("本周") }
                        TextButton(onClick = { offset++; selectedDay = null }, enabled = boundedOffset < 0, modifier = Modifier.weight(1f)) { Text("下一周") }
                    }
                    if (day == null) {
                        Text("${summary.total} 位 UP 主更新 · 点击星期查看", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val max = summary.days.maxOf { it.mids.size }.coerceAtLeast(1)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            summary.days.forEachIndexed { index, item ->
                                Column(Modifier.weight(1f).clickable { selectedDay = index; period = 0 }.padding(vertical = 8.dp)
                                    .semantics { contentDescription = "${names[index]}，${item.mids.size} 位 UP 主更新" }, horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.mids.size.toString(), style = MaterialTheme.typography.labelMedium)
                                    Box(Modifier.height(86.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)), contentAlignment = Alignment.BottomCenter) {
                                        if (item.mids.isNotEmpty()) Box(Modifier.fillMaxWidth().height((item.mids.size.toFloat() / max * 86).dp)
                                            .background(Coral, RoundedCornerShape(7.dp)))
                                    }
                                    Text(names[index], style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                        if (summary.total == 0) Text("这一周暂无已采集的更新", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${names[selectedDay!!]} · ${day.mids.size} 位 UP 主", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { selectedDay = null }) { Text("返回整周") }
                        }
                        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = period == 0, onClick = { period = 0 }, label = { Text("上午") })
                            FilterChip(selected = period == 1, onClick = { period = 1 }, label = { Text("下午") })
                        }
                        UpdateClock(minutes, period)
                        Text("具体更新时间与 UP 主", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (minutes.isEmpty()) Text("${if (period == 0) "上午" else "下午"}暂无更新", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (day != null) {
            items(minutes.toList(), key = { it.first }) { (minute, people) ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.width(64.dp)) {
                            Text(minuteLabel(minute), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${people.size} 人", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            people.forEach { creator ->
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Row(Modifier.padding(4.dp).semantics { contentDescription = "${creator.name}，UID ${creator.mid}" }, verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        CreatorAvatar(creator.name, creator.face, Modifier.size(24.dp))
                                        Text(creator.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(max = 120.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("${ZoneId.systemDefault().id} · 保留最近 $historyWeeks 周\n记录来自手动检查，每个时段按 UP 主去重，不是完整历史。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun UpdateClock(minutes: Map<Int, List<CreatorUi>>, period: Int) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val numbers = remember(measurer, colors.onSurface) { (0..11).map { measurer.measure(if (it == 0) "12" else it.toString(), TextStyle(color = colors.onSurface, fontSize = 22.sp)) } }
    val total = minutes.values.flatten().map { it.mid }.toSet().size
    Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 420.dp).aspectRatio(1f).semantics { contentDescription = "无指针 12 小时更新表盘，${if (period == 0) "上午" else "下午"} $total 位 UP 主更新" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 16.dp.toPx()
            fun position(r: Float, angle: Double): Offset {
                val rad = Math.toRadians(angle - 90)
                return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
            }
            drawCircle(colors.surfaceVariant, radius, center)
            drawCircle(colors.surface, radius - 5.dp.toPx(), center)
            drawCircle(colors.outlineVariant, radius, center, style = Stroke(1.dp.toPx()))
            for (tick in 0 until 60) {
                val major = tick % 5 == 0
                drawLine(if (major) colors.onSurface else colors.outline, position(radius - if (major) 17.dp.toPx() else 10.dp.toPx(), tick * 6.0),
                    position(radius - 6.dp.toPx(), tick * 6.0), strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx())
                if (major) {
                    val layout = numbers[tick / 5]
                    val at = position(radius - 36.dp.toPx(), tick * 6.0)
                    drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
                }
            }
            minutes.keys.forEach { drawCircle(colors.secondary, 3.dp.toPx(), position(radius + 8.dp.toPx(), (it % 720) / 2.0)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (period == 0) "上午" else "下午", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(total.toString(), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Text("位 UP 主更新", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}
