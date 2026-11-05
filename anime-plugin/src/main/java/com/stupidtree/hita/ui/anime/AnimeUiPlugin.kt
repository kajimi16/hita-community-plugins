package com.stupidtree.hita.ui.anime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stupidtree.hita.api.ComposeUiPlugin
import com.stupidtree.hita.api.PluginUiHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 番剧界面插件：宿主主界面的第五个 tab，通过 [ComposeUiPlugin] 提供 Compose 视图。
 *
 * 不含 Activity / 资源表，也不打进宿主 APK；从市场安装后由宿主容器渲染。
 */
class AnimeUiPlugin : ComposeUiPlugin {

    override val id = "com.stupidtree.hita.ui.anime"
    override val displayName = "番剧"
    override val requiresApi = 4

    override fun createView(context: Context, host: PluginUiHost): View =
        ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    AnimePage(context)
                }
            }
        }
}

@Composable
private fun AnimePage(context: Context) {
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<List<Anime>>(emptyList()) }

    LaunchedEffect(query) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                if (query.isBlank()) BangumiRepository.today() else BangumiRepository.search(query)
            }
        }.onSuccess { list = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { query = input.trim() }),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                "搜索番剧",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        inner()
                    }
                },
            )
            TextButton(onClick = { query = input.trim() }) {
                Text("搜索")
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "")
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有找到番剧")
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { anime ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://bgm.tv/subject/${anime.id}"),
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            anime.nameCn ?: anime.name,
                            style = MaterialTheme.typography.body1,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = buildString {
                            if (!anime.nameCn.isNullOrBlank()) append(anime.name)
                            if (anime.airWeekday != null) {
                                if (isNotEmpty()) append(" · ")
                                append("周").append(weekdayName(anime.airWeekday))
                            }
                            if (anime.score != null) {
                                if (isNotEmpty()) append(" · ")
                                append("评分 ").append(anime.score)
                            }
                        }
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Divider()
                }
            }
        }
    }
}

private fun weekdayName(day: Int): String = when (day) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
}
