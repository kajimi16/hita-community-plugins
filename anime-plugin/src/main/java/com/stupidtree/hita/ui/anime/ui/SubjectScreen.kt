package com.stupidtree.hita.ui.anime.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.stupidtree.hita.ui.anime.data.CharacterItem
import com.stupidtree.hita.ui.anime.data.DanmakuItem
import com.stupidtree.hita.ui.anime.data.EpisodeItem
import com.stupidtree.hita.ui.anime.data.HitaniApi
import com.stupidtree.hita.ui.anime.data.MyCollection
import com.stupidtree.hita.ui.anime.data.Prefs
import com.stupidtree.hita.ui.anime.data.RelatedSubject
import com.stupidtree.hita.ui.anime.data.ResourceGroup
import com.stupidtree.hita.ui.anime.data.ReviewItem
import com.stupidtree.hita.ui.anime.data.StaffItem
import com.stupidtree.hita.ui.anime.data.SubjectDetail
import com.stupidtree.hita.ui.anime.data.formatDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SubjectData(
    val detail: SubjectDetail,
    val episodes: List<EpisodeItem>,
    val characters: List<CharacterItem>,
    val staff: List<StaffItem>,
    val related: List<RelatedSubject>,
    val collection: MyCollection?,
    val watched: Set<Int>,
)

private val collectionOptions = listOf(
    3 to "在看",
    1 to "想看",
    2 to "看过",
    4 to "搁置",
    5 to "抛弃",
)

@Composable
fun SubjectScreen(
    context: Context,
    prefs: Prefs,
    bgm: BgmApi,
    hitani: HitaniApi,
    subjectId: Int,
    initialTitle: String?,
    onBack: () -> Unit,
    onOpenSubject: (Int, String?) -> Unit,
    onOpenDanmaku: (Int, String, EpisodeItem) -> Unit,
) {
    var data by remember(subjectId) { mutableStateOf<SubjectData?>(null) }
    var loading by remember(subjectId) { mutableStateOf(true) }
    var error by remember(subjectId) { mutableStateOf<String?>(null) }
    var tick by remember(subjectId) { mutableStateOf(0) }
    var tab by remember(subjectId) { mutableStateOf(0) }
    var message by remember(subjectId) { mutableStateOf<String?>(null) }
    var actionError by remember(subjectId) { mutableStateOf<String?>(null) }
    var saving by remember(subjectId) { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(subjectId, tick, prefs.bgmToken) {
        loading = data == null
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val detailDeferred = async { bgm.subject(subjectId) }
                    val episodesDeferred = async { bgm.episodes(subjectId) }
                    val charactersDeferred = async { runCatching { bgm.characters(subjectId) }.getOrDefault(emptyList()) }
                    val staffDeferred = async { runCatching { bgm.persons(subjectId) }.getOrDefault(emptyList()) }
                    val relatedDeferred = async { runCatching { bgm.related(subjectId) }.getOrDefault(emptyList()) }
                    val collectionDeferred = async {
                        if (prefs.bgmToken.isBlank()) return@async null
                        runCatching {
                            val username = prefs.bgmUsername.ifBlank {
                                bgm.me().also { prefs.bgmUsername = it.username }.username
                            }
                            bgm.userCollection(username, subjectId)
                        }.getOrNull()
                    }
                    val watchedDeferred = async {
                        runCatching { bgm.watchedEpisodeIds(subjectId) }.getOrDefault(emptySet())
                    }
                    SubjectData(
                        detail = detailDeferred.await(),
                        episodes = episodesDeferred.await(),
                        characters = charactersDeferred.await(),
                        staff = staffDeferred.await(),
                        related = relatedDeferred.await(),
                        collection = collectionDeferred.await(),
                        watched = watchedDeferred.await(),
                    )
                }
            }
        }.onSuccess { data = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    fun updateCollection(type: Int? = null, rate: Int? = null, comment: String? = null) {
        if (saving) return
        saving = true
        message = null
        actionError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    bgm.saveCollection(subjectId, type = type, rate = rate, comment = comment)
                }
            }.onSuccess {
                val current = data ?: return@onSuccess
                val old = current.collection
                val updated = MyCollection(
                    subjectId = subjectId,
                    type = type ?: old?.type ?: 3,
                    rate = rate ?: old?.rate ?: 0,
                    comment = comment ?: old?.comment,
                    epStatus = old?.epStatus ?: 0,
                    tags = old?.tags.orEmpty(),
                )
                data = current.copy(collection = updated)
                message = when {
                    type != null -> "已更新收藏状态"
                    rate != null -> if (rate == 0) "已清除评分" else "已评分 $rate 分"
                    else -> "已保存短评"
                }
            }.onFailure { actionError = it.message ?: "保存失败" }
            saving = false
        }
    }

    fun toggleEpisode(episode: EpisodeItem) {
        val current = data ?: return
        if (prefs.bgmToken.isBlank()) {
            actionError = "请先在「我的」里绑定 Bangumi 访问令牌"
            return
        }
        val watched = episode.id in current.watched
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    bgm.setEpisodeType(episode.id, if (watched) 0 else 2)
                }
            }.onSuccess {
                data = current.copy(
                    watched = if (watched) current.watched - episode.id else current.watched + episode.id,
                )
            }.onFailure { actionError = it.message ?: "进度保存失败" }
        }
    }

    fun markAll() {
        val current = data ?: return
        if (prefs.bgmToken.isBlank()) {
            actionError = "请先在「我的」里绑定 Bangumi 访问令牌"
            return
        }
        val pending = current.episodes.filterNot { it.id in current.watched }
        if (pending.isEmpty()) return
        scope.launch {
            saving = true
            actionError = null
            val done = mutableSetOf<Int>()
            runCatching {
                withContext(Dispatchers.IO) {
                    pending.forEachIndexed { index, episode ->
                        bgm.setEpisodeType(episode.id, 2)
                        done += episode.id
                        if (index != pending.lastIndex) delay(120)
                    }
                }
            }.onSuccess {
                data = (data ?: current).copy(watched = current.watched + done)
                message = "已标记 ${done.size} 集为看过"
            }.onFailure {
                data = (data ?: current).copy(watched = current.watched + done)
                actionError = it.message ?: "批量标记失败"
            }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(initialTitle ?: data?.detail?.displayName ?: "条目详情", onBack = onBack)
        when {
            loading && data == null -> LoadingState()
            error != null && data == null -> ErrorState(error!!) { tick++ }
            data != null -> {
                val subject = data!!
                LazyColumn(Modifier.fillMaxSize()) {
                    item { SubjectHeader(subject.detail) }
                    item {
                        CollectionBar(
                            hasToken = prefs.bgmToken.isNotBlank(),
                            collection = subject.collection,
                            saving = saving,
                            onStatus = { updateCollection(type = it) },
                            onRate = { updateCollection(rate = it) },
                            onComment = {
                                commentDraft = subject.collection?.comment.orEmpty()
                                showCommentDialog = true
                            },
                        )
                    }
                    message?.let { text ->
                        item {
                            Text(
                                text,
                                color = MaterialTheme.colors.primary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    actionError?.let { text ->
                        item {
                            Text(
                                text,
                                color = MaterialTheme.colors.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    item {
                        SubjectTabs(selected = tab, onSelect = { tab = it })
                    }
                    when (tab) {
                        0 -> episodesTab(
                            subject = subject,
                            actions = SubjectActions(
                                onToggleEpisode = ::toggleEpisode,
                                onOpenDanmaku = {
                                    onOpenDanmaku(subjectId, subject.detail.displayName, it)
                                },
                                onMarkAll = ::markAll,
                                hasToken = prefs.bgmToken.isNotBlank(),
                            ),
                        )
                        1 -> summaryTab(subject)
                        2 -> charactersTab(subject.characters) { character ->
                            openWeb(context, "https://bgm.tv/character/${character.id}")
                        }
                        3 -> staffTab(subject.staff) { staff ->
                            openWeb(context, "https://bgm.tv/person/${staff.id}")
                        }
                        4 -> relatedTab(subject.related) { related ->
                            onOpenSubject(related.id, related.displayName)
                        }
                        5 -> item {
                            danmakuTab(
                                hitani = hitani,
                                subject = subject,
                                onOpenDanmaku = {
                                    onOpenDanmaku(subjectId, subject.detail.displayName, it)
                                },
                            )
                        }
                        6 -> item { reviewsTab(hitani, subjectId) }
                        else -> item { resourcesTab(hitani, subjectId) }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    if (showCommentDialog) {
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("收藏短评") },
            text = {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最多 500 字") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCommentDialog = false
                    updateCollection(comment = commentDraft)
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showCommentDialog = false }) { Text("取消") }
            },
        )
    }
}

private data class SubjectActions(
    val onToggleEpisode: (EpisodeItem) -> Unit,
    val onOpenDanmaku: (EpisodeItem) -> Unit,
    val onMarkAll: () -> Unit,
    val hasToken: Boolean,
)

private fun androidx.compose.foundation.lazy.LazyListScope.episodesTab(
    subject: SubjectData,
    actions: SubjectActions,
) {
    item {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已看 ${subject.watched.size} / ${subject.episodes.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            if (actions.hasToken && subject.episodes.isNotEmpty()) {
                TagPill("全部标记看过", onClick = actions.onMarkAll)
            }
        }
    }
    item {
        EpisodeGrid(
            episodes = subject.episodes,
            watched = subject.watched,
            onToggle = actions.onToggleEpisode,
        )
    }
    item { SectionTitle("章节列表") }
    items(subject.episodes, key = { it.id }) { episode ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(
                        if (episode.id in subject.watched) {
                            MaterialTheme.colors.primary
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                        },
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    episode.label,
                    fontSize = 11.sp,
                    color = if (episode.id in subject.watched) {
                        MaterialTheme.colors.onPrimary
                    } else {
                        MaterialTheme.colors.onSurface
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    episode.displayName,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(episode.airdate?.let(::formatDay), episode.duration)
                        .joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            }
            Text(
                if (episode.id in subject.watched) "已看" else "标记",
                fontSize = 11.sp,
                color = MaterialTheme.colors.primary,
                modifier = Modifier
                    .clickable { actions.onToggleEpisode(episode) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                "弹幕",
                fontSize = 11.sp,
                color = MaterialTheme.colors.secondaryVariant,
                modifier = Modifier
                    .clickable { actions.onOpenDanmaku(episode) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.summaryTab(subject: SubjectData) {
    val detail = subject.detail
    item {
        var expanded by remember(detail.id) { mutableStateOf(false) }
        Column(Modifier.padding(horizontal = 16.dp)) {
            detail.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    summary,
                    fontSize = 13.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Text(
                    if (expanded) "收起" else "展开",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(vertical = 6.dp),
                )
            }
            if (detail.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("标签", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    detail.tags.take(20).forEach { tag ->
                        TagPill("${tag.name} ${tag.count}")
                    }
                }
            }
            if (detail.infobox.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("作品信息", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                detail.infobox.forEach { row -> KeyValueRow(row.key, row.value) }
            }
            Spacer(Modifier.height(16.dp))
            RatingHistogram(detail)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.charactersTab(
    characters: List<CharacterItem>,
    onOpen: (CharacterItem) -> Unit,
) {
    if (characters.isEmpty()) {
        item { EmptyState("没有角色数据") }
        return
    }
    item {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(characters, key = { it.id }) { character ->
                Column(
                    Modifier.width(92.dp).clickable { onOpen(character) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncCover(character.images.grid ?: character.images.cover, Modifier.size(82.dp), radius = 41.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        character.name,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        character.relation,
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                    character.actors.firstOrNull()?.let {
                        Text(
                            "CV ${it.name}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    items(characters, key = { "row-${it.id}" }) { character ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(character) }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncCover(character.images.small ?: character.images.grid, Modifier.size(44.dp), radius = 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(character.name, fontSize = 13.sp)
                Text(
                    character.actors.joinToString(" / ") { it.name },
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            }
            Text(
                character.relation,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.staffTab(
    staff: List<StaffItem>,
    onOpen: (StaffItem) -> Unit,
) {
    if (staff.isEmpty()) {
        item { EmptyState("没有制作人员数据") }
        return
    }
    items(staff, key = { it.id }) { person ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(person) }
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncCover(person.images.small ?: person.images.grid, Modifier.size(40.dp), radius = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(person.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                person.relation,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.relatedTab(
    related: List<RelatedSubject>,
    onOpen: (RelatedSubject) -> Unit,
) {
    if (related.isEmpty()) {
        item { EmptyState("没有关联条目") }
        return
    }
    items(related, key = { it.id }) { subject ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(subject) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncCover(subject.images.grid ?: subject.images.cover, Modifier.size(52.dp, 72.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    subject.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subject.name,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
            Text(
                subject.relation,
                fontSize = 11.sp,
                color = MaterialTheme.colors.primary,
            )
        }
    }
}

@Composable
private fun danmakuTab(
    hitani: HitaniApi,
    subject: SubjectData,
    onOpenDanmaku: (EpisodeItem) -> Unit,
) {
    var selected by remember(subject.detail.id) {
        mutableStateOf(subject.episodes.firstOrNull { it.id !in subject.watched } ?: subject.episodes.firstOrNull())
    }
    var items by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(selected?.id, tick) {
        val episode = selected ?: return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { hitani.danmaku(episode.id, limit = 100) }
        }.onSuccess { items = it.items }
            .onFailure { error = it.message ?: "弹幕加载失败" }
        loading = false
    }

    Column {
        if (subject.episodes.isEmpty()) {
            EmptyState("没有章节")
        } else {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(subject.episodes, key = { it.id }) { episode ->
                TagPill(
                    episode.label,
                    selected = selected?.id == episode.id,
                    onClick = { selected = episode },
                )
            }
        }
        selected?.let { episode ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${episode.displayName} · ${items.size} 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                TagPill("全屏弹幕", onClick = { onOpenDanmaku(episode) })
            }
        }
        when {
            loading -> LoadingState("正在拉取弹幕…")
            error != null -> ErrorState(error!!) { tick++ }
            items.isEmpty() -> EmptyState("这一集还没有弹幕", "发送弹幕后会在全屏页显示")
            else -> items.take(80).forEach { item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Text(
                        com.stupidtree.hita.ui.anime.data.formatDuration(item.playTimeMs / 1000),
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.width(44.dp),
                    )
                    Text(item.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(
                        item.locationLabel,
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun reviewsTab(hitani: HitaniApi, subjectId: Int) {
    var items by remember(subjectId) { mutableStateOf<List<ReviewItem>>(emptyList()) }
    var loading by remember(subjectId) { mutableStateOf(true) }
    var error by remember(subjectId) { mutableStateOf<String?>(null) }
    var tick by remember(subjectId) { mutableStateOf(0) }

    LaunchedEffect(subjectId, tick) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { hitani.reviews(subjectId, limit = 50) }
        }.onSuccess { items = it.items }
            .onFailure { error = it.message ?: "评论加载失败" }
        loading = false
    }

    when {
        loading -> LoadingState("正在加载校内评论…")
        error != null -> ErrorState(error!!) { tick++ }
        items.isEmpty() -> EmptyState("还没有评论", "登录站点后可在 Web 端发表")
        else -> Column {
            items.forEach { review ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            review.user?.nickname ?: "匿名",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        if (review.fromCollection) TagPill("收藏短评")
                        if (review.rating != null) {
                            Spacer(Modifier.width(6.dp))
                            TagPill("★ ${review.rating}")
                        }
                    }
                    review.title?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(review.content, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        review.createdAt.take(10),
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                    )
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

@Composable
private fun resourcesTab(hitani: HitaniApi, subjectId: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var groups by remember(subjectId) { mutableStateOf<List<ResourceGroup>>(emptyList()) }
    var loading by remember(subjectId) { mutableStateOf(true) }
    var error by remember(subjectId) { mutableStateOf<String?>(null) }
    var tick by remember(subjectId) { mutableStateOf(0) }

    LaunchedEffect(subjectId, tick) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { hitani.resources(subjectId) }
        }.onSuccess { groups = it }
            .onFailure { error = it.message ?: "资源加载失败" }
        loading = false
    }

    when {
        loading -> LoadingState("正在检索外部资源…")
        error != null -> ErrorState(error!!) { tick++ }
        groups.isEmpty() -> EmptyState("没有可用资源", "站点未配置抓取源，或需要登录")
        else -> Column {
            groups.forEach { group ->
                SectionTitle(
                    group.episodeNumber?.let { "第 ${com.stupidtree.hita.ui.anime.data.formatNumber(it)} 集" }
                        ?: "合集",
                )
                group.items.forEach { link ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text(link.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append(link.sourceName)
                                if (link.isTorrent) append(" · BT")
                                link.sizeBytes?.let { bytes ->
                                    append(" · ")
                                    append(
                                        when {
                                            bytes >= 1024L * 1024 * 1024 ->
                                                "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
                                            bytes >= 1024L * 1024 ->
                                                "%.1f MB".format(bytes / 1024.0 / 1024.0)
                                            else -> "${bytes / 1024} KB"
                                        },
                                    )
                                }
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectHeader(detail: SubjectDetail) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            AsyncCover(detail.images.hero, Modifier.size(width = 116.dp, height = 162.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    detail.displayName,
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.nameCn != null && detail.nameCn.isNotBlank()) {
                    Text(
                        detail.name,
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScorePill(detail.score)
                    Spacer(Modifier.width(8.dp))
                    detail.rank?.let {
                        Text("排名 #$it", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        detail.date?.let { "首播 $it" },
                        detail.platform,
                        detail.eps?.let { "$it 话" },
                    ).joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                )
                if (detail.ratingTotal > 0) {
                    Text(
                        "${detail.ratingTotal} 人评分",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionBar(
    hasToken: Boolean,
    collection: MyCollection?,
    saving: Boolean,
    onStatus: (Int) -> Unit,
    onRate: (Int) -> Unit,
    onComment: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (!hasToken) {
            Surface(
                color = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "绑定 Bangumi 访问令牌后可收藏、评分、标记进度。",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            return
        }
        HorizontalChips {
            collectionOptions.forEach { (value, label) ->
                TagPill(label, selected = collection?.type == value, onClick = { onStatus(value) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "评分",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.width(40.dp),
            )
            HorizontalChips(Modifier.weight(1f)) {
                (1..10).forEach { value ->
                    TagPill(
                        "$value",
                        selected = collection?.rate == value,
                        onClick = { onRate(value) },
                    )
                }
                TagPill(
                    "清除",
                    selected = (collection?.rate ?: 0) == 0,
                    onClick = { onRate(0) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagPill("写短评", onClick = onComment)
            if (saving) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            collection?.comment?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SubjectTabs(selected: Int, onSelect: (Int) -> Unit) {
    val titles = listOf("章节", "简介", "角色", "制作", "关联", "弹幕", "评论", "资源")
    ScrollableTabRow(
        selectedTabIndex = selected,
        edgePadding = 8.dp,
        backgroundColor = MaterialTheme.colors.background,
        contentColor = MaterialTheme.colors.primary,
    ) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                text = { Text(title, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<EpisodeItem>,
    watched: Set<Int>,
    onToggle: (EpisodeItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        episodes.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { episode ->
                    EpisodeCell(episode, episode.id in watched, Modifier.weight(1f)) { onToggle(episode) }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EpisodeCell(
    episode: EpisodeItem,
    watched: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(46.dp)
            .background(
                if (watched) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.07f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (watched) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                episode.label,
                fontSize = 12.sp,
                color = if (watched) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
            )
        }
    }
}

@Composable
private fun RatingHistogram(detail: SubjectDetail) {
    if (detail.ratingTotal <= 0) return
    val max = detail.ratingCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth()) {
        Text("评分分布", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        detail.ratingCounts.forEachIndexed { index, count ->
            val score = 10 - index
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$score", fontSize = 10.sp, modifier = Modifier.width(18.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(MaterialTheme.colors.onSurface.copy(alpha = 0.07f), RoundedCornerShape(3.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .height(6.dp)
                            .background(MaterialTheme.colors.primary, RoundedCornerShape(3.dp)),
                    )
                }
                Text(
                    "$count",
                    fontSize = 9.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.width(30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

private fun openWeb(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
