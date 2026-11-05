package com.stupidtree.hita.ui.anime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stupidtree.hita.ui.anime.data.BgmApi
import com.stupidtree.hita.ui.anime.data.Schedule
import com.stupidtree.hita.ui.anime.data.formatShortDay
import com.stupidtree.hita.ui.anime.data.todayIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 放送表：当前在播番组按星期分组。
 *
 * 这里刻意不按「首播日期」筛选 —— 那会把已经播了几十集的长篇排除掉。
 * 数据源是 Bangumi `calendar`（当前在播），不是 `air_date` 搜索。
 */
@Composable
fun ScheduleScreen(
    bgm: BgmApi,
    onOpenSubject: (Int, String?) -> Unit,
) {
    var schedule by remember { mutableStateOf<Schedule?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { bgm.airingSchedule(0) }
        }.onSuccess {
            schedule = it
            val dates = it.days.map { day -> day.date }
            selectedDate = if (todayIso() in dates) todayIso() else dates.firstOrNull()
        }.onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("放送表", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                Text(
                    "当前在播 · 按星期 · 含长篇连载",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                )
            }
            TagPill("刷新", onClick = { tick++ })
        }

        when {
            loading -> LoadingState("正在获取放送表…")
            error != null -> ErrorState(error!!) { tick++ }
            schedule == null -> EmptyState("暂无放送数据")
            else -> {
                val current = schedule!!
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current.days, key = { it.date }) { day ->
                        TagPill(
                            text = "${formatShortDay(day.date)} · ${day.items.size}",
                            selected = day.date == selectedDate,
                            onClick = { selectedDate = day.date },
                        )
                    }
                }
                val day = current.days.firstOrNull { it.date == selectedDate }
                if (day == null || day.items.isEmpty()) {
                    EmptyState("这一天没有在播番组")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Text(
                                "点击条目查看详情与章节进度。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.45f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            )
                        }
                        items(day.items, key = { it.id }) { subject ->
                            SubjectRow(subject) {
                                onOpenSubject(subject.id, subject.displayName)
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}
