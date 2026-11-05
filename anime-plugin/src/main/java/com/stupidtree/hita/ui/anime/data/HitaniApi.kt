package com.stupidtree.hita.ui.anime.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HitaniException(val status: Int, message: String) : Exception(message)

data class HitaniUser(
    val id: String,
    val nickname: String,
    val email: String?,
    val schoolId: String?,
)

data class LoginResult(val user: HitaniUser, val cookie: String)

data class DanmakuItem(
    val id: String,
    val episodeId: Int,
    val serviceId: String,
    val senderName: String,
    val schoolId: String?,
    val playTimeMs: Long,
    val color: Int,
    val text: String,
    val location: Int,
) {
    val locationLabel: String get() = when (location) {
        4 -> "顶部"
        5 -> "底部"
        else -> "滚动"
    }
}

data class DanmakuPage(
    val total: Int,
    val schoolTotal: Int,
    val schoolOnly: Boolean,
    val externalCount: Int,
    val items: List<DanmakuItem>,
)

data class ReviewUser(val nickname: String, val avatarUrl: String?)

data class ReviewItem(
    val id: String,
    val kind: Int,
    val title: String?,
    val content: String,
    val rating: Int?,
    val schoolId: String?,
    val likes: Int,
    val createdAt: String,
    val user: ReviewUser?,
    val fromCollection: Boolean,
)

data class ReviewPage(
    val total: Int,
    val schoolTotal: Int,
    val schoolOnly: Boolean,
    val items: List<ReviewItem>,
)

data class ResourceLink(
    val sourceName: String,
    val title: String,
    val url: String,
    val episodeNumber: Double?,
    val sizeBytes: Long?,
    val isTorrent: Boolean,
)

data class ResourceGroup(val episodeNumber: Double?, val items: List<ResourceLink>)

/**
 * hit-ani 站点客户端。
 *
 * 只读接口（条目缓存、弹幕、评论、时间表）无需登录；发弹幕 / 资源索引需要
 * 站点会话。会话是 HttpOnly Cookie，插件自己保存 `hitani_session`，
 * 不依赖 WebView 或宿主。
 */
class HitaniApi(private val baseUrl: String, private val cookie: String?) {

    fun login(identifier: String, password: String): LoginResult {
        val body = JSONObject().put("identifier", identifier).put("password", password)
        val response = requestWithHeaders(
            "/api/auth/login",
            method = "POST",
            body = body.toString(),
            auth = false,
        )
        val userObj = JSONObject(response.body).optJSONObject("user")
        val sessionCookie = response.headers
            .filter { it.startsWith("hitani_session=") }
            .joinToString("; ") { it.substringBefore(";").trim() }
        if (sessionCookie.isBlank()) {
            throw HitaniException(502, "登录成功但没有拿到会话 Cookie")
        }
        val user = HitaniUser(
            id = userObj?.stringOrNull("id").orEmpty(),
            nickname = userObj?.stringOrNull("nickname").orEmpty(),
            email = userObj?.stringOrNull("email"),
            schoolId = userObj?.stringOrNull("schoolId"),
        )
        return LoginResult(user, sessionCookie)
    }

    fun me(): HitaniUser? {
        val text = runCatching { request("/api/auth/login", auth = true) }.getOrNull() ?: return null
        val userObj = JSONObject(text).optJSONObject("user") ?: return null
        return HitaniUser(
            id = userObj.stringOrNull("id").orEmpty(),
            nickname = userObj.stringOrNull("nickname").orEmpty(),
            email = userObj.stringOrNull("email"),
            schoolId = userObj.stringOrNull("schoolId"),
        )
    }

    fun schedule(weekOffset: Int): Schedule {
        val root = JSONObject(request("/api/schedule?weekOffset=$weekOffset"))
        val days = root.optJSONArray("days") ?: JSONArray()
        return Schedule(
            weekStart = root.stringOrNull("weekStart").orEmpty(),
            weekEnd = root.stringOrNull("weekEnd").orEmpty(),
            weekOffset = root.optInt("weekOffset", weekOffset),
            days = (0 until days.length()).mapNotNull { index ->
                val day = days.optJSONObject(index) ?: return@mapNotNull null
                val items = day.optJSONArray("items") ?: JSONArray()
                ScheduleDay(
                    date = day.stringOrNull("date").orEmpty(),
                    items = (0 until items.length()).mapNotNull { itemIndex ->
                        val item = items.optJSONObject(itemIndex) ?: return@mapNotNull null
                        SubjectSummary(
                            id = item.optInt("id"),
                            type = 2,
                            name = item.stringOrNull("name").orEmpty(),
                            nameCn = item.stringOrNull("nameCn"),
                            images = ImageSet(common = item.stringOrNull("coverUrl")),
                            score = item.doubleOrNull("score"),
                            rank = null,
                            airDate = day.stringOrNull("date"),
                            tags = run {
                                val tags = item.optJSONArray("tags") ?: return@run emptyList()
                                (0 until tags.length()).mapNotNull { tagIndex ->
                                    val tag = tags.optJSONObject(tagIndex) ?: return@mapNotNull null
                                    tag.stringOrNull("name")?.let { Tag(it, tag.optInt("count", 0)) }
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    fun danmaku(
        episodeId: Int,
        limit: Int = 500,
        schoolOnly: Boolean = false,
        fromMs: Long? = null,
        toMs: Long? = null,
    ): DanmakuPage {
        val params = buildString {
            append("/api/danmaku?episodeId=$episodeId&limit=$limit")
            if (schoolOnly) append("&schoolOnly=true")
            fromMs?.let { append("&fromMs=$it") }
            toMs?.let { append("&toMs=$it") }
        }
        val root = JSONObject(request(params, auth = true))
        val data = root.optJSONArray("data") ?: JSONArray()
        val items = (0 until data.length()).mapNotNull { index ->
            val obj = data.optJSONObject(index) ?: return@mapNotNull null
            DanmakuItem(
                id = obj.stringOrNull("id").orEmpty(),
                episodeId = obj.optInt("episodeId", episodeId),
                serviceId = obj.stringOrNull("serviceId").orEmpty(),
                senderName = obj.stringOrNull("senderName").orEmpty(),
                schoolId = obj.stringOrNull("schoolId"),
                playTimeMs = obj.optLong("playTimeMs", 0L),
                color = obj.optInt("color", 0xFFFFFF),
                text = obj.stringOrNull("text").orEmpty(),
                location = obj.optInt("location", 1),
            )
        }
        return DanmakuPage(
            total = root.optInt("total", items.size),
            schoolTotal = root.optInt("schoolTotal", 0),
            schoolOnly = root.optBoolean("schoolOnly", schoolOnly),
            externalCount = root.optJSONObject("external")?.optInt("externalCount", 0) ?: 0,
            items = items,
        )
    }

    fun sendDanmaku(
        episodeId: Int,
        playTimeMs: Long,
        text: String,
        color: Int = 0xFFFFFF,
        location: Int = 1,
    ) {
        val body = JSONObject()
            .put("episodeId", episodeId)
            .put("playTimeMs", playTimeMs)
            .put("text", text)
            .put("color", color)
            .put("location", location)
        request("/api/danmaku", method = "POST", body = body.toString(), auth = true)
    }

    fun reviews(
        subjectId: Int,
        limit: Int = 30,
        offset: Int = 0,
        schoolOnly: Boolean = false,
    ): ReviewPage {
        val root = JSONObject(
            request(
                "/api/reviews?subjectId=$subjectId&limit=$limit&offset=$offset" +
                    if (schoolOnly) "&schoolOnly=true" else "",
                auth = true,
            ),
        )
        fun parseArray(array: JSONArray?, fromCollection: Boolean): List<ReviewItem> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                ReviewItem(
                    id = obj.stringOrNull("id").orEmpty(),
                    kind = obj.optInt("kind", 0),
                    title = obj.stringOrNull("title"),
                    content = obj.stringOrNull("content").orEmpty(),
                    rating = obj.intOrNull("rating"),
                    schoolId = obj.stringOrNull("schoolId"),
                    likes = obj.optInt("likes", 0),
                    createdAt = obj.stringOrNull("createdAt").orEmpty(),
                    user = obj.optJSONObject("user")?.let {
                        ReviewUser(
                            nickname = it.stringOrNull("nickname").orEmpty(),
                            avatarUrl = it.stringOrNull("avatarUrl"),
                        )
                    },
                    fromCollection = fromCollection,
                )
            }
        }
        val items = parseArray(root.optJSONArray("data"), false) +
            parseArray(root.optJSONArray("collectionComments"), true)
        return ReviewPage(
            total = root.optInt("total", 0),
            schoolTotal = root.optInt("schoolTotal", 0),
            schoolOnly = root.optBoolean("schoolOnly", schoolOnly),
            items = items.sortedByDescending { it.createdAt },
        )
    }

    /** 外部资源索引（需登录）。返回按集分组后的结果。 */
    fun resources(subjectId: Int): List<ResourceGroup> {
        val root = JSONObject(
            request("/api/media/resources?subjectId=$subjectId", auth = true),
        )
        val groups = root.optJSONArray("groups") ?: JSONArray()
        return (0 until groups.length()).mapNotNull { index ->
            val group = groups.optJSONObject(index) ?: return@mapNotNull null
            val items = group.optJSONArray("items") ?: JSONArray()
            ResourceGroup(
                episodeNumber = if (group.isNull("episodeNumber")) null else group.optDouble("episodeNumber"),
                items = (0 until items.length()).mapNotNull { itemIndex ->
                    val item = items.optJSONObject(itemIndex) ?: return@mapNotNull null
                    ResourceLink(
                        sourceName = item.stringOrNull("sourceName").orEmpty(),
                        title = item.stringOrNull("title").orEmpty(),
                        url = item.stringOrNull("url").orEmpty(),
                        episodeNumber = if (item.isNull("episodeNumber")) null else item.optDouble("episodeNumber"),
                        sizeBytes = item.optLong("sizeBytes", 0L).takeIf { it > 0 },
                        isTorrent = item.optBoolean("isTorrent", false),
                    )
                },
            )
        }
    }

    /* ------------------------------------------------------------------ */

    private data class RawResponse(val body: String, val headers: List<String>)

    private fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        auth: Boolean = true,
    ): String = requestWithHeaders(path, method, body, auth).body

    private fun requestWithHeaders(
        path: String,
        method: String = "GET",
        body: String? = null,
        auth: Boolean = true,
    ): RawResponse {
        val url = base + path
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", BgmApi.USER_AGENT)
            if (auth) {
                cookie?.takeIf { it.isNotBlank() }?.let {
                    conn.setRequestProperty("Cookie", it)
                }
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw HitaniException(status, errorMessage(text, status))
            }
            val headers = conn.headerFields
                .filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
                .values
                .flatten()
            return RawResponse(text, headers)
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(body: String, status: Int): String {
        if (body.isBlank()) return "站点请求失败（HTTP $status）"
        return runCatching {
            val obj = JSONObject(body)
            obj.stringOrNull("error")
                ?: obj.stringOrNull("message")
                ?: "站点请求失败（HTTP $status）"
        }.getOrDefault("站点请求失败（HTTP $status）")
    }

    private val base: String
        get() = baseUrl.trim().removeSuffix("/")

    companion object {
        const val DEFAULT_BASE = "https://ani.kajimi.cc"

        fun normalizeBase(value: String): String {
            val trimmed = value.trim().removeSuffix("/")
            return trimmed.ifBlank { DEFAULT_BASE }
        }

        fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
