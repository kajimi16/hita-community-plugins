package com.stupidtree.hita.ui.anime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stupidtree.hita.ui.anime.data.DanmakuItem
import com.stupidtree.hita.ui.anime.data.EpisodeItem
import com.stupidtree.hita.ui.anime.data.HitaniApi
import com.stupidtree.hita.ui.anime.data.Prefs
import com.stupidtree.hita.ui.anime.data.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val colorOptions = listOf(
    0xFFFFFF to "白",
    0xFF5C7C to "红",
    0xFFC93C to "黄",
    0x6CE08A to "绿",
    0x6EC8FF to "蓝",
    0xFF9AD5 to "粉",
)

@Composable
fun DanmakuScreen(
    prefs: Prefs,
    hitani: HitaniApi,
    subjectId: Int,
    subjectTitle: String,
    episode: EpisodeItem,
    onBack: () -> Unit,
) {
    var items by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var externalCount by remember { mutableStateOf(0) }
    var schoolOnly by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    var text by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("0") }
    var location by remember { mutableStateOf(1) }
    var color by remember { mutableStateOf(0xFFFFFF) }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(episode.id, schoolOnly, tick) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                hitani.danmaku(episode.id, limit = 1000, schoolOnly = schoolOnly)
            }
        }.onSuccess {
            items = it.items.sortedBy { d -> d.playTimeMs }
            total = it.total
            externalCount = it.externalCount
        }.onFailure { error = it.message ?: "弹幕加载失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("弹幕 · ${subjectTitle}", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "第 ${episode.label} 集 · ${episode.displayName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本地 $total 条 · 外部 $externalCount 条",
                    fontSize = 11.sp,
                    color = androidx.compose.material.MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                TagPill(
                    "只看本校",
                    selected = schoolOnly,
                    onClick = {
                        if (prefs.hitaniCookie.isBlank()) {
                            sendError = "只看本校需要先在「我的」里登录站点"
                        } else {
                            schoolOnly = !schoolOnly
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                TagPill("刷新", onClick = { tick++ })
            }
        }

        if (prefs.hitaniCookie.isBlank()) {
            Text(
                "未登录站点：可以浏览弹幕；发送与本校筛选需要登录。",
                fontSize = 11.sp,
                color = androidx.compose.material.MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("发一条弹幕…", fontSize = 12.sp) },
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { value -> seconds = value.filter(Char::isDigit).take(5) },
                        modifier = Modifier.width(74.dp),
                        singleLine = true,
                        label = { Text("秒", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                HorizontalChips {
                    listOf(1 to "滚动", 4 to "顶部", 5 to "底部").forEach { (value, label) ->
                        TagPill(label, selected = location == value, onClick = { location = value })
                    }
                    colorOptions.forEach { (value, label) ->
                        TagPill(label, selected = color == value, onClick = { color = value })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !sending,
                        onClick = {
                            val content = text.trim()
                            if (content.isEmpty()) {
                                sendError = "弹幕不能为空"
                                return@TextButton
                            }
                            sending = true
                            sendError = null
                            message = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        hitani.sendDanmaku(
                                            episodeId = episode.id,
                                            playTimeMs = (seconds.toLongOrNull() ?: 0L) * 1000,
                                            text = content,
                                            color = color,
                                            location = location,
                                        )
                                    }
                                }.onSuccess {
                                    text = ""
                                    message = "已发送"
                                    tick++
                                }.onFailure { sendError = it.message ?: "发送失败" }
                                sending = false
                            }
                        },
                    ) { Text(if (sending) "发送中…" else "发送") }
                    sendError?.let {
                        Text(it, fontSize = 11.sp, color = androidx.compose.material.MaterialTheme.colors.error)
                    }
                    message?.let {
                        Text(it, fontSize = 11.sp, color = androidx.compose.material.MaterialTheme.colors.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        when {
            loading -> LoadingState("正在拉取弹幕…")
            error != null -> ErrorState(error!!) { tick++ }
            items.isEmpty() -> EmptyState("这一集还没有弹幕")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "按时间轴排序，颜色为发送时的原始颜色。",
                        fontSize = 10.sp,
                        color = androidx.compose.material.MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(items, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            formatDuration(item.playTimeMs / 1000),
                            fontSize = 10.sp,
                            color = androidx.compose.material.MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.width(46.dp).padding(top = 2.dp),
                        )
                        Text(
                            item.text,
                            fontSize = 13.sp,
                            color = Color(item.color or 0xFF000000.toInt()),
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                item.senderName.ifBlank { item.serviceId },
                                fontSize = 9.sp,
                                color = androidx.compose.material.MaterialTheme.colors.onBackground.copy(alpha = 0.35f),
                            )
                            Text(
                                item.locationLabel,
                                fontSize = 9.sp,
                                color = androidx.compose.material.MaterialTheme.colors.onBackground.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
