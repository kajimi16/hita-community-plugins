package com.stupidtree.hita.ui.anime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stupidtree.hita.ui.anime.data.BgmApi
import com.stupidtree.hita.ui.anime.data.CollectionEntry
import com.stupidtree.hita.ui.anime.data.Prefs
import com.stupidtree.hita.ui.anime.data.Schedule
import com.stupidtree.hita.ui.anime.data.ScheduleDay
import com.stupidtree.hita.ui.anime.data.SubjectSummary
import com.stupidtree.hita.ui.anime.data.formatShortDay
import com.stupidtree.hita.ui.anime.data.todayIso
import com.stupidtree.hita.ui.anime.data.todayWeekday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private data class HomeData(
    val today: List<SubjectSummary>,
    val schedule: Schedule,
    val doing: List<CollectionEntry>,
)

@Composable
fun HomeScreen(
    prefs: Prefs,
    bgm: BgmApi,
    onOpenSubject: (Int, String?) -> Unit,
    onOpenTab: (String) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<HomeData?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick, prefs.bgmToken, prefs.bgmUsername) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val todayDeferred = async {
                        val weekday = todayWeekday()
                        bgm.calendar().firstOrNull { it.weekday == weekday }?.items.orEmpty()
                    }
                    val scheduleDeferred = async { bgm.schedule(0) }
                    val doingDeferred = async {
                        val username = prefs.bgmUsername
                        if (prefs.bgmToken.isBlank() || username.isBlank()) {
                            emptyList()
                        } else {
                            runCatching {
                                bgm.userCollections(username, type = 3, offset = 0, limit = 12).data
                            }.getOrDefault(emptyList())
                        }
                    }
                    HomeData(
                        today = todayDeferred.await().sortedByDescending { it.score ?: 0.0 }.take(20),
                        schedule = scheduleDeferred.await(),
                        doing = doingDeferred.await(),
                    )
                }
            }
        }.onSuccess { data = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "番剧",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Bangumi × 校内弹幕",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                )
            }
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }

        when {
            loading && data == null -> LoadingState()
            error != null && data == null -> ErrorState(error!!) { refreshTick++ }
            data != null -> HomeContent(
                data = data!!,
                prefs = prefs,
                onOpenSubject = onOpenSubject,
                onOpenTab = onOpenTab,
            )
            else -> EmptyState("暂无数据")
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    prefs: Prefs,
    onOpenSubject: (Int, String?) -> Unit,
    onOpenTab: (String) -> Unit,
) {
    var selectedDate by remember(data.schedule) {
        mutableStateOf(data.schedule.days.firstOrNull { it.date == todayIso() }?.date
            ?: data.schedule.days.firstOrNull()?.date)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TodayHero(data.today, onOpenSubject)
        }
        item {
            SectionTitle(
                "继续追番",
                trailing = if (data.doing.isEmpty()) null else "全部",
                onTrailing = { onOpenTab("library") },
            )
        }
        item {
            if (data.doing.isEmpty()) {
                BindHint(prefs, onOpenTab)
            } else {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(data.doing, key = { it.collection.subjectId }) { entry ->
                        DoingCard(entry) {
                            onOpenSubject(entry.collection.subjectId, entry.subject?.displayName)
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("本周新番")
        }
        item {
            DayChips(data.schedule.days, selectedDate) { selectedDate = it }
        }
        val day = data.schedule.days.firstOrNull { it.date == selectedDate }
        if (day == null || day.items.isEmpty()) {
            item { EmptyState("这一天没有放送") }
        } else {
            items(day.items, key = { it.id }) { subject ->
                SubjectRow(subject) {
                    onOpenSubject(subject.id, subject.displayName)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TodayHero(items: List<SubjectSummary>, onOpenSubject: (Int, String?) -> Unit) {
    if (items.isEmpty()) {
        EmptyState("今日没有放送")
        return
    }
    Column {
        SectionTitle("今日放送")
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { subject ->
                Column(
                    Modifier
                        .width(112.dp)
                        .clickable { onOpenSubject(subject.id, subject.displayName) },
                ) {
                    Box {
                        AsyncCover(
                            subject.images.common ?: subject.images.cover,
                            Modifier.size(width = 112.dp, height = 156.dp),
                        )
                        ScorePill(
                            subject.score,
                            Modifier.align(Alignment.BottomStart).padding(6.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        subject.displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoingCard(entry: CollectionEntry, onClick: () -> Unit) {
    val subject = entry.subject
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.surface,
        modifier = Modifier
            .width(260.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncCover(subject?.images?.grid ?: subject?.images?.cover, Modifier.size(52.dp, 72.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    subject?.displayName ?: "条目 ${entry.collection.subjectId}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (entry.collection.epStatus > 0) {
                        "看到第 ${entry.collection.epStatus} 集"
                    } else {
                        "尚未开始"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                )
                if (entry.collection.rate > 0) {
                    Text(
                        "我的评分 ${entry.collection.rate}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.secondaryVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BindHint(prefs: Prefs, onOpenTab: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onOpenTab("profile") },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (prefs.bgmToken.isBlank()) "绑定 Bangumi，开启追番同步" else "同步你的 Bangumi 收藏",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (prefs.bgmToken.isBlank()) {
                    "在设置里填入个人访问令牌，即可读取收藏、回写进度与评分。"
                } else {
                    "前往设置校验令牌并查看账号状态。"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun DayChips(days: List<ScheduleDay>, selected: String?, onSelect: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days, key = { it.date }) { day ->
            TagPill(
                text = formatShortDay(day.date),
                selected = day.date == selected,
                onClick = { onSelect(day.date) },
            )
        }
    }
}

