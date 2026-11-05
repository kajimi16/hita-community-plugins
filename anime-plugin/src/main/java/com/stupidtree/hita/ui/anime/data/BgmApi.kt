package com.stupidtree.hita.ui.anime.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class BgmException(val status: Int, message: String) : Exception(message)

/**
 * Bangumi v0 客户端。
 *
 * 只依赖 `HttpURLConnection` + `org.json`：插件是 dex，不能携带额外的网络/序列化库，
 * 也不应把宿主的依赖当作可用的公共 API。
 */
class BgmApi(private val tokenProvider: () -> String?) {

    fun calendar(): List<CalendarDay> {
        val array = JSONArray(request("/calendar", auth = false))
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val weekdayObj = obj.optJSONObject("weekday") ?: return@mapNotNull null
            val weekday = weekdayObj.optInt("id", 0)
            if (weekday !in 1..7) return@mapNotNull null
            val items = obj.optJSONArray("items").toSummaries()
            CalendarDay(
                weekday = weekday,
                label = weekdayObj.stringOrNull("cn") ?: weekdayCn(weekday),
                items = items,
            )
        }.sortedBy { it.weekday }
    }

    fun search(filter: SearchFilter, offset: Int = 0, limit: Int = 20): SearchPage {
        val body = JSONObject().apply {
            put("keyword", filter.keyword.trim())
            put("sort", filter.sort)
            put(
                "filter",
                JSONObject().apply {
                    put("type", JSONArray().put(filter.type))
                    put("nsfw", false)
                    if (filter.tags.isNotEmpty()) {
                        put("tag", JSONArray(filter.tags))
                    }
                    val dates = airDateFilter(filter.year, filter.month)
                    if (dates.isNotEmpty()) {
                        put("air_date", JSONArray(dates))
                    }
                },
            )
        }
        val text = request(
            "/v0/search/subjects?limit=$limit&offset=$offset",
            method = "POST",
            body = body.toString(),
            auth = false,
        )
        val root = JSONObject(text)
        return SearchPage(
            total = root.optInt("total", 0),
            offset = root.optInt("offset", offset),
            data = root.optJSONArray("data").toSummaries(),
        )
    }

    /** 时间表：BGM 没有对应端点，按 `air_date` 区间检索聚合（与 Web 端同一策略）。 */
    fun schedule(weekOffset: Int): Schedule {
        val (start, end) = weekRange(weekOffset)
        val body = JSONObject().apply {
            put("keyword", "")
            put("sort", "heat")
            put(
                "filter",
                JSONObject().apply {
                    put("type", JSONArray().put(2))
                    put("nsfw", false)
                    put("air_date", JSONArray().put(">=$start").put("<=$end"))
                },
            )
        }
        val text = request(
            "/v0/search/subjects?limit=50&offset=0",
            method = "POST",
            body = body.toString(),
            auth = false,
        )
        val root = JSONObject(text)
        val grouped = LinkedHashMap<String, MutableList<SubjectSummary>>()
        root.optJSONArray("data").toSummaries().forEach { subject ->
            val date = subject.airDate ?: return@forEach
            grouped.getOrPut(date) { mutableListOf() }.add(subject)
        }
        val days = grouped.entries
            .sortedBy { it.key }
            .map { (date, items) ->
                ScheduleDay(
                    date = date,
                    items = items.sortedByDescending { it.score ?: 0.0 },
                )
            }
        return Schedule(
            weekStart = start,
            weekEnd = end,
            weekOffset = weekOffset,
            days = days,
        )
    }

    fun subject(id: Int): SubjectDetail =
        SubjectDetail.fromJson(JSONObject(request("/v0/subjects/$id", auth = false)))

    fun episodes(subjectId: Int): List<EpisodeItem> {
        val result = mutableListOf<EpisodeItem>()
        var offset = 0
        while (true) {
            val root = JSONObject(
                request(
                    "/v0/episodes?subject_id=$subjectId&type=0&limit=100&offset=$offset",
                    auth = false,
                ),
            )
            val page = root.optJSONArray("data").toEpisodes()
            result += page
            val total = root.optInt("total", result.size)
            if (page.isEmpty() || result.size >= total) break
            offset += page.size
        }
        return result
    }

    fun characters(subjectId: Int): List<CharacterItem> {
        val array = JSONArray(request("/v0/subjects/$subjectId/characters", auth = false))
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            CharacterItem(
                id = obj.optInt("id"),
                name = obj.stringOrNull("name").orEmpty(),
                relation = obj.stringOrNull("relation").orEmpty(),
                images = ImageSet.fromJson(obj.optJSONObject("images")),
                actors = run {
                    val actors = obj.optJSONArray("actors") ?: return@run emptyList()
                    (0 until actors.length()).mapNotNull { actorIndex ->
                        val actor = actors.optJSONObject(actorIndex) ?: return@mapNotNull null
                        Actor(
                            id = actor.optInt("id"),
                            name = actor.stringOrNull("name").orEmpty(),
                            images = ImageSet.fromJson(actor.optJSONObject("images")),
                        )
                    }
                },
            )
        }
    }

    fun persons(subjectId: Int): List<StaffItem> {
        val array = JSONArray(request("/v0/subjects/$subjectId/persons", auth = false))
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            StaffItem(
                id = obj.optInt("id"),
                name = obj.stringOrNull("name").orEmpty(),
                relation = obj.stringOrNull("relation").orEmpty(),
                images = ImageSet.fromJson(obj.optJSONObject("images")),
            )
        }
    }

    fun related(subjectId: Int): List<RelatedSubject> {
        val array = JSONArray(request("/v0/subjects/$subjectId/subjects", auth = false))
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            RelatedSubject(
                id = obj.optInt("id"),
                type = obj.optInt("type", 2),
                name = obj.stringOrNull("name").orEmpty(),
                nameCn = obj.stringOrNull("name_cn"),
                relation = obj.stringOrNull("relation").orEmpty(),
                images = ImageSet.fromJson(obj.optJSONObject("images")),
            )
        }
    }

    fun me(): BgmUser {
        val obj = JSONObject(request("/v0/me", auth = true))
        return BgmUser(
            id = obj.optInt("id"),
            username = obj.stringOrNull("username").orEmpty(),
            nickname = obj.stringOrNull("nickname").orEmpty(),
            avatar = ImageSet.fromJson(obj.optJSONObject("avatar")),
        )
    }

    /** 公开可读；带 token 时也能读到私有收藏。 */
    fun userCollection(username: String, subjectId: Int): MyCollection? {
        val text = runCatching {
            request(
                "/v0/users/${encodePath(username)}/collections/$subjectId",
                auth = true,
                allowNotFound = true,
            )
        }.getOrNull() ?: return null
        if (text.isBlank()) return null
        return parseMyCollection(JSONObject(text), subjectId)
    }

    fun userCollections(
        username: String,
        type: Int,
        offset: Int,
        limit: Int = 30,
    ): CollectionPage {
        val root = JSONObject(
            request(
                "/v0/users/${encodePath(username)}/collections" +
                    "?subject_type=2&type=$type&limit=$limit&offset=$offset",
                auth = true,
            ),
        )
        val data = root.optJSONArray("data") ?: JSONArray()
        val entries = (0 until data.length()).mapNotNull { index ->
            val obj = data.optJSONObject(index) ?: return@mapNotNull null
            val subjectId = obj.optInt("subject_id", 0)
            val subject = obj.optJSONObject("subject")?.let {
                SubjectSummary.fromJson(
                    JSONObject(it.toString()).apply {
                        if (!has("id")) put("id", subjectId)
                    },
                )
            }
            CollectionEntry(
                collection = parseMyCollection(obj, subjectId) ?: return@mapNotNull null,
                subject = subject,
            )
        }
        return CollectionPage(
            total = root.optInt("total", entries.size),
            data = entries,
        )
    }

    /** 已看过的章节 id。需要 `write:collection` 授权；未登录返回空集合。 */
    fun watchedEpisodeIds(subjectId: Int): Set<Int> {
        if (tokenProvider().isNullOrBlank()) return emptySet()
        val text = runCatching {
            request("/v0/users/-/collections/$subjectId/episodes?limit=1000", auth = true)
        }.getOrNull() ?: return emptySet()
        if (text.isBlank()) return emptySet()
        val data = JSONObject(text).optJSONArray("data") ?: return emptySet()
        return buildSet {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                if (item.optInt("type", 0) != 2) continue
                val episodeId = item.optJSONObject("episode")?.optInt("id", 0) ?: 0
                if (episodeId > 0) add(episodeId)
            }
        }
    }

    /**
     * 写入收藏状态。`rate = 0` 表示删除评分（BGM 的约定）。
     * 未登录时抛 [BgmException]。
     */
    fun saveCollection(
        subjectId: Int,
        type: Int? = null,
        rate: Int? = null,
        comment: String? = null,
    ) {
        require(tokenProvider().isNullOrBlank().not()) { "请先在设置里绑定 Bangumi 访问令牌" }
        val body = JSONObject().apply {
            type?.let { put("type", it) }
            rate?.let { put("rate", it) }
            comment?.let { put("comment", it) }
        }
        request(
            "/v0/users/-/collections/$subjectId",
            method = "POST",
            body = body.toString(),
            auth = true,
        )
    }

    /** `type`: 0 未收藏 / 1 想看 / 2 看过 / 3 抛弃（章节维度）。 */
    fun setEpisodeType(episodeId: Int, type: Int) {
        require(tokenProvider().isNullOrBlank().not()) { "请先在设置里绑定 Bangumi 访问令牌" }
        request(
            "/v0/users/-/collections/-/episodes/$episodeId",
            method = "PUT",
            body = JSONObject().put("type", type).toString(),
            auth = true,
        )
    }

    /* ------------------------------------------------------------------ */

    private fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        auth: Boolean = true,
        allowNotFound: Boolean = false,
    ): String {
        val url = if (path.startsWith("http")) path else BASE + path
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            if (auth) {
                tokenProvider()?.takeIf { it.isNotBlank() }?.let {
                    conn.setRequestProperty("Authorization", "Bearer $it")
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
                if (allowNotFound && status == 404) return ""
                throw BgmException(status, errorMessage(text, status))
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(body: String, status: Int): String {
        if (body.isBlank()) return "Bangumi 请求失败（HTTP $status）"
        return runCatching {
            val obj = JSONObject(body)
            obj.stringOrNull("description")
                ?: obj.stringOrNull("title")
                ?: obj.stringOrNull("message")
                ?: "Bangumi 请求失败（HTTP $status）"
        }.getOrDefault("Bangumi 请求失败（HTTP $status）")
    }

    private fun parseMyCollection(obj: JSONObject, subjectId: Int): MyCollection? {
        val type = obj.optInt("type", 0)
        if (type == 0) return null
        return MyCollection(
            subjectId = obj.optInt("subject_id", subjectId),
            type = type,
            rate = obj.optInt("rate", 0),
            comment = obj.stringOrNull("comment"),
            epStatus = obj.optInt("ep_status", 0),
            tags = obj.optJSONArray("tags")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty(),
        )
    }

    companion object {
        const val BASE = "https://api.bgm.tv"
        const val USER_AGENT =
            "hita-anime-plugin/0.2.0 (https://github.com/kajimi16/hita-community-plugins)"

        private fun JSONArray?.toSummaries(): List<SubjectSummary> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                optJSONObject(index)?.let(SubjectSummary::fromJson)
            }
        }

        private fun JSONArray?.toEpisodes(): List<EpisodeItem> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                val obj = optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optInt("id", 0)
                if (id <= 0) return@mapNotNull null
                EpisodeItem(
                    id = id,
                    type = obj.optInt("type", 0),
                    sort = obj.optDouble("sort", 0.0),
                    ep = if (obj.isNull("ep")) null else obj.optDouble("ep"),
                    name = obj.stringOrNull("name").orEmpty(),
                    nameCn = obj.stringOrNull("name_cn"),
                    airdate = obj.stringOrNull("airdate"),
                    duration = obj.stringOrNull("duration"),
                    desc = obj.stringOrNull("desc"),
                )
            }
        }

        private fun encodePath(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun airDateFilter(year: Int?, month: Int?): List<String> {
            if (year == null) return emptyList()
            if (month == null) {
                return listOf(">=$year-01-01", "<=$year-12-31")
            }
            val start = "%04d-%02d-01".format(year, month)
            val days = daysInMonth(year, month)
            val end = "%04d-%02d-%02d".format(year, month, days)
            return listOf(">=$start", "<=$end")
        }
    }
}
