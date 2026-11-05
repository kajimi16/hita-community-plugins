package com.stupidtree.hita.ui.anime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val statusOptions = listOf(
    3 to "在看",
    1 to "想看",
    2 to "看过",
    4 to "搁置",
    5 to "抛弃",
)

@Composable
fun LibraryScreen(
    prefs: Prefs,
    bgm: BgmApi,
    onOpenSubject: (Int, String?) -> Unit,
    onOpenProfile: () -> Unit,
) {
    var status by remember { mutableStateOf(3) }
    var entries by remember { mutableStateOf<List<CollectionEntry>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var username by remember { mutableStateOf(prefs.bgmUsername) }
    var tick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(status, tick, prefs.bgmToken) {
        if (prefs.bgmToken.isBlank()) {
            entries = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                val name = if (username.isNotBlank()) {
                    username
                } else {
                    bgm.me().also {
                        username = it.username
                        prefs.bgmUsername = it.username
                    }.username
                }
                bgm.userCollections(name, status, offset = 0, limit = 30)
            }
        }.onSuccess {
            entries = it.data
            total = it.total
        }.onFailure {
            entries = emptyList()
            error = it.message ?: "加载失败"
        }
        loading = false
    }

    fun loadMore() {
        if (loading || entries.size >= total) return
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    bgm.userCollections(username, status, offset = entries.size, limit = 30)
                }
            }.onSuccess {
                entries = (entries + it.data).distinctBy { entry -> entry.collection.subjectId }
                total = it.total
            }.onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("我的追番", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                Text(
                    if (prefs.bgmToken.isBlank()) "未绑定 Bangumi" else "Bangumi 收藏 · $username",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                )
            }
        }

        if (prefs.bgmToken.isBlank()) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                elevation = 0.dp,
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(onClick = onOpenProfile),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("绑定 Bangumi 后这里会显示你的收藏", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bangumi → 设置 → 开放接口 → 创建个人访问令牌。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        } else {

        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(statusOptions) { (value, label) ->
                TagPill(label, selected = status == value, onClick = {
                    if (status != value) {
                        status = value
                        entries = emptyList()
                        total = 0
                    }
                })
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (total > 0) "共 $total 部" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f),
            )
            Text(
                "刷新",
                fontSize = 12.sp,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.clickable { tick++ },
            )
        }

        when {
            loading && entries.isEmpty() -> LoadingState()
            error != null && entries.isEmpty() -> ErrorState(error!!) { tick++ }
            entries.isEmpty() -> EmptyState("这一分组还没有收藏")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.collection.subjectId }) { entry ->
                    CollectionRow(entry) {
                        onOpenSubject(
                            entry.collection.subjectId,
                            entry.subject?.displayName,
                        )
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (entries.size < total) {
                            TagPill("加载更多", onClick = { loadMore() })
                        } else {
                            Text(
                                "已经到底了",
                                fontSize = 12.sp,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CollectionRow(entry: CollectionEntry, onClick: () -> Unit) {
    val subject = entry.subject
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncCover(subject?.images?.grid ?: subject?.images?.cover, Modifier.width(62.dp).height(84.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                subject?.displayName ?: "条目 ${entry.collection.subjectId}",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.collection.epStatus > 0) {
                    Text(
                        "看到第 ${entry.collection.epStatus} 集",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (entry.collection.rate > 0) {
                    Text("★ ${entry.collection.rate}", fontSize = 11.sp, color = MaterialTheme.colors.secondaryVariant)
                }
            }
            entry.collection.comment?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

