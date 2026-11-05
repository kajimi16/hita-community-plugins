package com.stupidtree.hita.ui.anime.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stupidtree.hita.ui.anime.data.BgmApi
import com.stupidtree.hita.ui.anime.data.Prefs
import com.stupidtree.hita.ui.anime.data.SearchFilter
import com.stupidtree.hita.ui.anime.data.SubjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private val typeOptions = listOf(
    2 to "动画",
    1 to "书籍",
    4 to "游戏",
    3 to "音乐",
    6 to "三次元",
)

private val sortOptions = listOf(
    "match" to "匹配",
    "heat" to "热度",
    "rank" to "排名",
    "score" to "评分",
)

private val tagOptions = listOf(
    "原创", "漫画改", "游戏改", "轻小说改", "科幻", "奇幻", "校园", "恋爱",
    "战斗", "日常", "治愈", "悬疑", "音乐", "运动", "机战", "百合",
)

@Composable
fun SearchScreen(
    prefs: Prefs,
    bgm: BgmApi,
    onOpenSubject: (Int, String?) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(2) }
    var sort by remember { mutableStateOf("match") }
    var year by remember { mutableStateOf<Int?>(null) }
    var month by remember { mutableStateOf<Int?>(null) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<List<SubjectSummary>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun filter(offset: Int) = SearchFilter(
        keyword = keyword,
        type = type,
        sort = sort,
        tags = tags,
        year = year,
        month = month,
    )

    fun runSearch() {
        if (loading) return
        loading = true
        error = null
        searched = true
        prefs.rememberSearch(keyword)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { bgm.search(filter(0), offset = 0, limit = 30) }
            }.onSuccess { page ->
                results = page.data
                total = page.total
            }.onFailure {
                results = emptyList()
                total = 0
                error = it.message ?: "搜索失败"
            }
            loading = false
        }
    }

    fun loadMore() {
        if (loading || results.size >= total) return
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    bgm.search(filter(results.size), offset = results.size, limit = 30)
                }
            }.onSuccess { page ->
                results = (results + page.data).distinctBy { it.id }
                total = page.total
            }.onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("找番", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("搜索番剧、书籍、游戏…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyword = input.trim()
                    runSearch()
                }),
            )
            Spacer(Modifier.height(0.dp))
            IconButton(onClick = {
                keyword = input.trim()
                runSearch()
            }) {
                Icon(Icons.Filled.Search, contentDescription = "搜索")
            }
        }

        FilterSection(
            type = type,
            sort = sort,
            year = year,
            month = month,
            tags = tags,
            onType = { type = it },
            onSort = { sort = it },
            onYear = {
                year = it
                if (it == null) month = null
            },
            onMonth = { month = it },
            onTag = { tag ->
                tags = if (tag in tags) tags - tag else tags + tag
            },
        )

        if (!searched) {
            HistoryAndHints(
                prefs = prefs,
                onPick = {
                    input = it
                    keyword = it
                    runSearch()
                },
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (total > 0) "共 $total 条 · 已显示 ${results.size}" else "没有结果",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                if (loading) CircularProgressIndicator(Modifier.height(14.dp), strokeWidth = 2.dp)
            }

            when {
                error != null && results.isEmpty() -> ErrorState(error!!) { runSearch() }
                results.isEmpty() && !loading -> EmptyState("没有找到条目", "换个关键词或去掉筛选试试")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(results, key = { it.id }) { subject ->
                        SubjectGridCard(subject) {
                            onOpenSubject(subject.id, subject.displayName)
                        }
                    }
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (results.size < total) {
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
private fun FilterSection(
    type: Int,
    sort: String,
    year: Int?,
    month: Int?,
    tags: List<String>,
    onType: (Int) -> Unit,
    onSort: (String) -> Unit,
    onYear: (Int?) -> Unit,
    onMonth: (Int?) -> Unit,
    onTag: (String) -> Unit,
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    Column {
        HorizontalChips(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            typeOptions.forEach { (value, label) ->
                TagPill(label, selected = type == value, onClick = { onType(value) })
            }
        }
        HorizontalChips(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            sortOptions.forEach { (value, label) ->
                TagPill(label, selected = sort == value, onClick = { onSort(value) })
            }
        }
        HorizontalChips(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            TagPill("全部年份", selected = year == null, onClick = { onYear(null) })
            (currentYear downTo currentYear - 8).forEach { value ->
                TagPill("$value", selected = year == value, onClick = { onYear(value) })
            }
        }
        if (year != null) {
            HorizontalChips(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                TagPill("全年", selected = month == null, onClick = { onMonth(null) })
                (1..12).forEach { value ->
                    TagPill("$value 月", selected = month == value, onClick = { onMonth(value) })
                }
            }
        }
        HorizontalChips(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            tagOptions.forEach { tag ->
                TagPill(tag, selected = tag in tags, onClick = { onTag(tag) })
            }
        }
    }
}

@Composable
private fun HistoryAndHints(prefs: Prefs, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (prefs.searchHistory.isNotEmpty()) {
            SectionTitle(
                "搜索历史",
                trailing = "清空",
                onTrailing = { prefs.clearSearchHistory() },
            )
            HorizontalChips(Modifier.padding(horizontal = 16.dp)) {
                prefs.searchHistory.forEach { keyword ->
                    TagPill(keyword, onClick = { onPick(keyword) })
                }
            }
        }
        SectionTitle("热门标签")
        HorizontalChips(Modifier.padding(horizontal = 16.dp)) {
            tagOptions.take(10).forEach { tag ->
                TagPill(tag, onClick = { onPick(tag) })
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "支持关键词 + 标签 + 年份 + 排序组合筛选。",
            fontSize = 12.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
