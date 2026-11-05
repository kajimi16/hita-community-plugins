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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import com.stupidtree.hita.ui.anime.data.HitaniApi
import com.stupidtree.hita.ui.anime.data.Schedule
import com.stupidtree.hita.ui.anime.data.formatShortDay
import com.stupidtree.hita.ui.anime.data.todayIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScheduleScreen(
    hitani: HitaniApi,
    bgm: BgmApi,
    onOpenSubject: (Int, String?) -> Unit,
) {
    var weekOffset by remember { mutableStateOf(0) }
    var schedule by remember { mutableStateOf<Schedule?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(weekOffset, tick) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                if (hitani.me() != null) {
                    hitani.schedule(weekOffset) to "hit-ani"
                } else {
                    bgm.schedule(weekOffset) to "Bangumi"
                }
            }
        }.onSuccess { (result, label) ->
            schedule = result
            source = label
            val dates = result.days.map { it.date }
            selectedDate = when {
                weekOffset == 0 && todayIso() in dates -> todayIso()
                else -> dates.firstOrNull()
            }
        }.onFailure {
            // 站点不可用时退回 Bangumi，保证时间表始终可用。
            runCatching {
                withContext(Dispatchers.IO) { bgm.schedule(weekOffset) }
            }.onSuccess {
                schedule = it
                source = "Bangumi"
                selectedDate = it.days.firstOrNull()?.date
                error = null
            }.onFailure { inner ->
                error = inner.message ?: "加载失败"
            }
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("新番时间表", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                Text(
                    if (source.isBlank()) "按周聚合" else "数据源：$source",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                )
            }
            IconButton(onClick = { weekOffset-- }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一周")
            }
            Text(
                schedule?.let { "${formatShortDay(it.weekStart)} - ${formatShortDay(it.weekEnd)}" }
                    ?: "本周",
                fontSize = 12.sp,
                modifier = Modifier.width(96.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { weekOffset++ }) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一周")
            }
        }

        when {
            loading -> LoadingState("正在获取时间表…")
            error != null -> ErrorState(error!!) { tick++ }
            schedule == null || schedule!!.days.isEmpty() -> EmptyState("这一周没有放送")
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
                    EmptyState("这一天没有放送")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
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
