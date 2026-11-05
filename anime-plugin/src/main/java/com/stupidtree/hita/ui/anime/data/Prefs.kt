package com.stupidtree.hita.ui.anime.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * 插件自己的持久化。用 SharedPreferences 而不是数据库：
 * 只有令牌、站点地址、搜索历史这类小数据，Room 会引入不必要的构建期依赖。
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("hita_anime_plugin", Context.MODE_PRIVATE)

    private var _bgmToken by mutableStateOf(sp.getString(KEY_BGM_TOKEN, "").orEmpty())
    var bgmToken: String
        get() = _bgmToken
        set(value) {
            val normalized = value.trim()
            _bgmToken = normalized
            sp.edit().putString(KEY_BGM_TOKEN, normalized).apply()
        }

    private var _bgmUsername by mutableStateOf(sp.getString(KEY_BGM_USERNAME, "").orEmpty())
    var bgmUsername: String
        get() = _bgmUsername
        set(value) {
            _bgmUsername = value
            sp.edit().putString(KEY_BGM_USERNAME, value).apply()
        }

    private var _hitaniBase by mutableStateOf(
        sp.getString(KEY_HITANI_BASE, HitaniApi.DEFAULT_BASE) ?: HitaniApi.DEFAULT_BASE,
    )
    var hitaniBase: String
        get() = _hitaniBase
        set(value) {
            val normalized = HitaniApi.normalizeBase(value)
            _hitaniBase = normalized
            sp.edit().putString(KEY_HITANI_BASE, normalized).apply()
        }

    private var _hitaniCookie by mutableStateOf(sp.getString(KEY_HITANI_COOKIE, "").orEmpty())
    var hitaniCookie: String
        get() = _hitaniCookie
        set(value) {
            _hitaniCookie = value
            sp.edit().putString(KEY_HITANI_COOKIE, value).apply()
        }

    private var _hitaniNickname by mutableStateOf(sp.getString(KEY_HITANI_NICK, "").orEmpty())
    var hitaniNickname: String
        get() = _hitaniNickname
        set(value) {
            _hitaniNickname = value
            sp.edit().putString(KEY_HITANI_NICK, value).apply()
        }

    private var _searchHistory by mutableStateOf(loadHistory())
    var searchHistory: List<String>
        get() = _searchHistory
        set(value) {
            val trimmed = value.filter { it.isNotBlank() }.distinct().take(10)
            _searchHistory = trimmed
            sp.edit().putString(KEY_SEARCH_HISTORY, JSONArray(trimmed).toString()).apply()
        }

    /** 缓存/站点地址（逗号或换行分隔，按顺序尝试）。 */
    fun bases(): List<String> = hitaniBase
        .split(',', '\n')
        .map { it.trim().trimEnd('/') }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .distinct()
        .ifEmpty { listOf(HitaniApi.DEFAULT_BASE) }

    fun rememberSearch(keyword: String) {
        val value = keyword.trim()
        if (value.isBlank()) return
        searchHistory = listOf(value) + searchHistory.filterNot { it == value }
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
    }

    fun logoutHitani() {
        hitaniCookie = ""
        hitaniNickname = ""
    }

    private fun loadHistory(): List<String> = runCatching {
        val text = sp.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_BGM_TOKEN = "bgm_token"
        private const val KEY_BGM_USERNAME = "bgm_username"
        private const val KEY_HITANI_BASE = "hitani_base"
        private const val KEY_HITANI_COOKIE = "hitani_cookie"
        private const val KEY_HITANI_NICK = "hitani_nickname"
        private const val KEY_SEARCH_HISTORY = "search_history"
    }
}
