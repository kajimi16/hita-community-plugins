package com.stupidtree.hita.ui.anime.data

import org.json.JSONArray
import org.json.JSONObject

/** Bangumi 图片集合。不同端点给的尺寸不同，统一取最合适的一张。 */
data class ImageSet(
    val small: String? = null,
    val grid: String? = null,
    val common: String? = null,
    val medium: String? = null,
    val large: String? = null,
) {
    /** 列表用封面：优先 400 宽，避免把 800 宽的图拉进内存。 */
    val cover: String? get() = common ?: medium ?: large ?: grid ?: small

    /** 详情页大图。 */
    val hero: String? get() = large ?: medium ?: common ?: grid ?: small

    companion object {
        fun fromJson(json: JSONObject?): ImageSet {
            if (json == null) return ImageSet()
            return ImageSet(
                small = json.stringOrNull("small"),
                grid = json.stringOrNull("grid"),
                common = json.stringOrNull("common"),
                medium = json.stringOrNull("medium"),
                large = json.stringOrNull("large"),
            )
        }
    }
}

data class Tag(val name: String, val count: Int)

data class InfoboxRow(val key: String, val value: String)

/** 列表 / 搜索结果共用的条目摘要。 */
data class SubjectSummary(
    val id: Int,
    val type: Int,
    val name: String,
    val nameCn: String?,
    val images: ImageSet,
    val score: Double?,
    val rank: Int?,
    val airDate: String?,
    val summary: String? = null,
    val tags: List<Tag> = emptyList(),
) {
    val displayName: String get() = nameCn?.takeIf { it.isNotBlank() } ?: name

    companion object {
        fun fromJson(json: JSONObject): SubjectSummary = SubjectSummary(
            id = json.optInt("id"),
            type = json.optInt("type", 2),
            name = json.stringOrNull("name").orEmpty(),
            nameCn = json.stringOrNull("name_cn"),
            images = ImageSet.fromJson(json.optJSONObject("images")),
            score = json.optJSONObject("rating")?.doubleOrNull("score"),
            rank = json.optJSONObject("rating")?.intOrNull("rank"),
            airDate = json.stringOrNull("date") ?: json.stringOrNull("air_date"),
            summary = json.stringOrNull("summary") ?: json.stringOrNull("short_summary"),
            tags = json.optJSONArray("tags").toTags(),
        )
    }
}

/** 条目详情。 */
data class SubjectDetail(
    val id: Int,
    val type: Int,
    val name: String,
    val nameCn: String?,
    val summary: String?,
    val date: String?,
    val platform: String?,
    val eps: Int?,
    val images: ImageSet,
    val tags: List<Tag>,
    val infobox: List<InfoboxRow>,
    val score: Double?,
    val rank: Int?,
    val ratingTotal: Int,
    /** 10 分到 1 分各档人数，index 0 = 10 分。 */
    val ratingCounts: List<Int>,
    val collection: Map<String, Int>,
) {
    val displayName: String get() = nameCn?.takeIf { it.isNotBlank() } ?: name

    companion object {
        fun fromJson(json: JSONObject): SubjectDetail {
            val rating = json.optJSONObject("rating")
            val countsJson = rating?.optJSONObject("count")
            val counts = (10 downTo 1).map { score ->
                countsJson?.optInt(score.toString(), 0) ?: 0
            }
            return SubjectDetail(
                id = json.optInt("id"),
                type = json.optInt("type", 2),
                name = json.stringOrNull("name").orEmpty(),
                nameCn = json.stringOrNull("name_cn"),
                summary = json.stringOrNull("summary"),
                date = json.stringOrNull("date"),
                platform = json.stringOrNull("platform"),
                eps = json.intOrNull("eps") ?: json.intOrNull("total_episodes"),
                images = ImageSet.fromJson(json.optJSONObject("images")),
                tags = json.optJSONArray("tags").toTags(),
                infobox = json.optJSONArray("infobox").toInfobox(),
                score = rating?.doubleOrNull("score"),
                rank = rating?.intOrNull("rank"),
                ratingTotal = rating?.optInt("total", 0) ?: 0,
                ratingCounts = counts,
                collection = buildMap {
                    json.optJSONObject("collection")?.let { c ->
                        for (key in listOf("wish", "collect", "doing", "on_hold", "dropped")) {
                            put(key, c.optInt(key, 0))
                        }
                    }
                },
            )
        }
    }
}

data class EpisodeItem(
    val id: Int,
    val type: Int,
    val sort: Double,
    val ep: Double?,
    val name: String,
    val nameCn: String?,
    val airdate: String?,
    val duration: String?,
    val desc: String?,
) {
    val displayName: String get() = nameCn?.takeIf { it.isNotBlank() } ?: name
    val label: String get() = ep?.let { formatNumber(it) } ?: formatNumber(sort)
}

data class Actor(val id: Int, val name: String, val images: ImageSet)

data class CharacterItem(
    val id: Int,
    val name: String,
    val relation: String,
    val images: ImageSet,
    val actors: List<Actor>,
)

data class StaffItem(
    val id: Int,
    val name: String,
    val relation: String,
    val images: ImageSet,
)

data class RelatedSubject(
    val id: Int,
    val type: Int,
    val name: String,
    val nameCn: String?,
    val relation: String,
    val images: ImageSet,
) {
    val displayName: String get() = nameCn?.takeIf { it.isNotBlank() } ?: name
}

/** 搜索筛选条件（对应 BGM `POST /v0/search/subjects`）。 */
data class SearchFilter(
    val keyword: String = "",
    val type: Int = 2,
    val sort: String = "match",
    val tags: List<String> = emptyList(),
    val year: Int? = null,
    val month: Int? = null,
)

data class SearchPage(
    val total: Int,
    val offset: Int,
    val data: List<SubjectSummary>,
)

data class BgmUser(
    val id: Int,
    val username: String,
    val nickname: String,
    val avatar: ImageSet,
)

/** 我的收藏（单条）。 */
data class MyCollection(
    val subjectId: Int,
    val type: Int,
    val rate: Int,
    val comment: String?,
    val epStatus: Int,
    val tags: List<String>,
)

data class CollectionEntry(
    val collection: MyCollection,
    val subject: SubjectSummary?,
)

data class CollectionPage(
    val total: Int,
    val data: List<CollectionEntry>,
)

data class CalendarDay(
    val weekday: Int,
    val label: String,
    val items: List<SubjectSummary>,
)

data class ScheduleDay(
    val date: String,
    val items: List<SubjectSummary>,
)

data class Schedule(
    val weekStart: String,
    val weekEnd: String,
    val weekOffset: Int,
    val days: List<ScheduleDay>,
)

/* ------------------------------------------------------------------ *
 * JSON helpers
 * ------------------------------------------------------------------ */

fun JSONObject.stringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}

fun JSONObject.intOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key, 0).takeIf { it > 0 }
}

fun JSONObject.doubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key, 0.0).takeIf { it > 0 }
}

private fun JSONArray?.toTags(): List<Tag> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let { obj ->
            val name = obj.stringOrNull("name") ?: return@let null
            Tag(name = name, count = obj.optInt("count", 0))
        }
    }
}

private fun JSONArray?.toInfobox(): List<InfoboxRow> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val obj = optJSONObject(index) ?: return@mapNotNull null
        val key = obj.stringOrNull("key") ?: return@mapNotNull null
        val value: Any = obj.opt("value") ?: return@mapNotNull null
        val text = when (value) {
            is String -> value
            is JSONArray -> (0 until value.length()).joinToString(" / ") { i ->
                when (val element = value.opt(i)) {
                    is JSONObject -> element.stringOrNull("v").orEmpty()
                    is String -> element
                    else -> ""
                }
            }.trim(' ', '/')
            else -> value.toString()
        }
        if (text.isBlank()) null else InfoboxRow(key, text)
    }
}

internal fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
