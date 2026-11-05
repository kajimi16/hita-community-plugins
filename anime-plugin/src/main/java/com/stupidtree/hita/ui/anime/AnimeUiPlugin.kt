package com.stupidtree.hita.ui.anime

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stupidtree.hita.api.ComposeUiPlugin
import com.stupidtree.hita.api.PluginUiHost
import com.stupidtree.hita.api.TimetableService
import com.stupidtree.hita.api.TimetableUiService
import com.stupidtree.hita.ui.anime.data.BgmApi
import com.stupidtree.hita.ui.anime.data.BgmSource
import com.stupidtree.hita.ui.anime.data.CoverLoader
import com.stupidtree.hita.ui.anime.data.EpisodeItem
import com.stupidtree.hita.ui.anime.data.HitaniApi
import com.stupidtree.hita.ui.anime.data.Prefs
import com.stupidtree.hita.ui.anime.ui.AnimeTheme
import com.stupidtree.hita.ui.anime.ui.DanmakuScreen
import com.stupidtree.hita.ui.anime.ui.HomeScreen
import com.stupidtree.hita.ui.anime.ui.LibraryScreen
import com.stupidtree.hita.ui.anime.ui.ProfileScreen
import com.stupidtree.hita.ui.anime.ui.ScheduleScreen
import com.stupidtree.hita.ui.anime.ui.SearchScreen
import com.stupidtree.hita.ui.anime.ui.SubjectScreen

/**
 * 番剧界面插件：宿主主界面 tab，运行时由 [ComposeUiPlugin] 容器承载。
 *
 * 功能对齐 Animeko 的 Android 客户端（浏览 / 时间表 / 搜索 / 追番 / 条目详情 /
 * 弹幕 / 评论 / 资源索引），但受插件边界限制：
 * - 不声明 Activity，因此播放交给外部播放器或站内页面；
 * - 不携带资源表，图标来自宿主的 material-icons；
 * - 数据直接走 Bangumi v0 与 hit-ani 的公开/会话接口。
 */
class AnimeUiPlugin : ComposeUiPlugin {

    override val id = "com.stupidtree.hita.ui.anime"
    override val displayName = "番剧"
    override val requiresApi = 5

    override fun createView(context: Context, host: PluginUiHost): View {
        CoverLoader.init(context)
        val backDispatcher = BackDispatcher()
        val timetable = host.service(TimetableService.KEY)
        val timetableUi = host.service(TimetableUiService.KEY)
        val composeView = ComposeView(context).apply {
            setContent {
                AnimeTheme(dark = isSystemInDarkTheme()) {
                    val initialSubjectId = host.arguments.getInt("subjectId", 0)
                    AnimeApp(
                        context = context,
                        initialSubjectId = initialSubjectId,
                        backDispatcher = backDispatcher,
                        timetable = timetable,
                        timetableUi = timetableUi,
                    )
                }
            }
        }
        return BackAwareContainer(context, backDispatcher).apply {
            addView(
                composeView,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        }
    }
}

/**
 * 宿主的 `MainActivity.onBackPressed()` 不调用 `super`，直接回桌面，
 * 因此 Compose 的 `BackHandler` / `OnBackPressedDispatcher` 都收不到返回键。
 * 这里是唯一的拦截点：在插件的根 View 上消费 KEYCODE_BACK。
 *
 * `ComposeView` 是 final，不能继承，所以用 FrameLayout 包一层；
 * 它位于按键分发链上，能在 Activity 处理返回键之前拿到事件。
 */
private class BackDispatcher {
    var onBack: (() -> Boolean)? = null
}

private class BackAwareContainer(
    context: Context,
    private val backDispatcher: BackDispatcher,
) : FrameLayout(context) {

    init {
        // 焦点链兜底：宿主工具栏/ViewPager 持有焦点时事件不会经过本容器。
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isShown &&
            event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP &&
            !event.isCanceled
        ) {
            if (backDispatcher.onBack?.invoke() == true) return true
        }
        return super.dispatchKeyEvent(event)
    }
}

private sealed interface Screen {
    data object Root : Screen
    data class Subject(val id: Int, val title: String?) : Screen
    data class Danmaku(
        val subjectId: Int,
        val subjectTitle: String,
        val episode: EpisodeItem,
    ) : Screen
}

private data class TabSpec(val id: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec("home", "首页", Icons.Filled.Home),
    TabSpec("schedule", "时间表", Icons.Filled.DateRange),
    TabSpec("search", "搜索", Icons.Filled.Search),
    TabSpec("library", "追番", Icons.Filled.Favorite),
    TabSpec("profile", "我的", Icons.Filled.Person),
)

@Composable
private fun AnimeApp(
    context: Context,
    initialSubjectId: Int,
    backDispatcher: BackDispatcher,
    timetable: TimetableService?,
    timetableUi: TimetableUiService?,
) {
    val prefs = remember { Prefs(context) }
    val bgmDirect = remember(prefs.bgmToken) { BgmApi(tokenProvider = { prefs.bgmToken }) }
    val bgm = remember(prefs.bgmToken, prefs.hitaniBase) {
        BgmSource(bgmDirect, prefs.bases())
    }
    val hitani = remember(prefs.hitaniBase, prefs.hitaniCookie) {
        HitaniApi(prefs.bases().first(), prefs.hitaniCookie)
    }

    SideEffect { CoverLoader.configure(prefs.bases()) }

    var stack by remember {
        mutableStateOf<List<Screen>>(
            if (initialSubjectId > 0) {
                listOf(Screen.Root, Screen.Subject(initialSubjectId, null))
            } else {
                listOf(Screen.Root)
            },
        )
    }

    SideEffect {
        backDispatcher.onBack = {
            if (stack.size > 1) {
                stack = stack.dropLast(1)
                true
            } else {
                false
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { backDispatcher.onBack = null }
    }

    when (val top = stack.last()) {
        is Screen.Root -> RootScreen(
            context = context,
            prefs = prefs,
            bgm = bgm,
            hitani = hitani,
            timetable = timetable,
            timetableUi = timetableUi,
            onOpenSubject = { id, title ->
                stack = stack + Screen.Subject(id, title)
            },
        )

        is Screen.Subject -> SubjectScreen(
            context = context,
            prefs = prefs,
            bgm = bgm,
            hitani = hitani,
            subjectId = top.id,
            initialTitle = top.title,
            onBack = { stack = stack.dropLast(1) },
            onOpenSubject = { id, title ->
                stack = stack + Screen.Subject(id, title)
            },
            onOpenDanmaku = { subjectId, subjectTitle, episode ->
                stack = stack + Screen.Danmaku(subjectId, subjectTitle, episode)
            },
        )

        is Screen.Danmaku -> DanmakuScreen(
            prefs = prefs,
            hitani = hitani,
            subjectId = top.subjectId,
            subjectTitle = top.subjectTitle,
            episode = top.episode,
            onBack = { stack = stack.dropLast(1) },
        )
    }
}

@Composable
private fun RootScreen(
    context: Context,
    prefs: Prefs,
    bgm: BgmSource,
    hitani: HitaniApi,
    timetable: TimetableService?,
    timetableUi: TimetableUiService?,
    onOpenSubject: (Int, String?) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf("home") }
    Column(Modifier.fillMaxSize()) {
        // 宿主本身已有底部导航；插件再放一个会被压在它下面（实测完全不可见），
        // 因此这里用顶部的分段导航，始终可点。
        Surface(
            color = MaterialTheme.colors.surface,
            elevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth()) {
                tabs.forEach { tab ->
                    val active = selected == tab.id
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { selected = tab.id }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.title,
                            tint = if (active) {
                                MaterialTheme.colors.primary
                            } else {
                                MaterialTheme.colors.onSurface.copy(alpha = 0.42f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            tab.title,
                            fontSize = 11.sp,
                            color = if (active) {
                                MaterialTheme.colors.primary
                            } else {
                                MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            },
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (selected) {
                "home" -> HomeScreen(
                    prefs = prefs,
                    bgm = bgm,
                    timetable = timetable,
                    onOpenClass = { event ->
                        when {
                            event.subjectId.isNotBlank() ->
                                timetableUi?.openSubject(context, event.subjectId)
                            event.timetableId.isNotBlank() ->
                                timetableUi?.openDetail(context, event.timetableId)
                        }
                    },
                    onOpenSubject = onOpenSubject,
                    onOpenTab = { selected = it },
                )
                "schedule" -> ScheduleScreen(
                    bgm = bgm,
                    onOpenSubject = onOpenSubject,
                )
                "search" -> SearchScreen(
                    prefs = prefs,
                    bgm = bgm,
                    onOpenSubject = onOpenSubject,
                )
                "library" -> LibraryScreen(
                    prefs = prefs,
                    bgm = bgm,
                    onOpenSubject = onOpenSubject,
                    onOpenProfile = { selected = "profile" },
                )
                else -> ProfileScreen(
                    prefs = prefs,
                    bgm = bgm,
                    hitani = hitani,
                )
            }
        }
    }
}
