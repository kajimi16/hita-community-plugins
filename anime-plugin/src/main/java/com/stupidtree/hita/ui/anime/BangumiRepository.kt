package com.stupidtree.hita.ui.anime

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

data class Anime(
    val id: Int,
    val name: String,
    val nameCn: String?,
    val score: Double?,
    val airWeekday: Int?,
)

/** Bangumi 公开 API：每日放送 + 关键词搜索。 */
object BangumiRepository {

    private const val UA = "oh-my-hita/0.1.0 (anime plugin; +https://github.com/kajimi16/OH-MY-HITA)"

    fun today(calendar: Calendar = Calendar.getInstance()): List<Anime> {
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val bgmWeekday = if (day == Calendar.SUNDAY) 7 else day - 1
        val root = JSONArray(get("https://api.bgm.tv/calendar"))
        for (i in 0 until root.length()) {
            val entry = root.getJSONObject(i)
            if (entry.optJSONObject("weekday")?.optInt("id") == bgmWeekday) {
                val items = entry.optJSONArray("items") ?: return emptyList()
                return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.toAnime() }
                    .distinctBy { it.id }
            }
        }
        return emptyList()
    }

    fun search(keyword: String): List<Anime> {
        val q = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val root = JSONObject(
            get("https://api.bgm.tv/search/subject/$q?type=2&responseGroup=small&max_results=30")
        )
        val list = root.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it)?.toAnime() }
            .distinctBy { it.id }
    }

    private fun JSONObject.toAnime(): Anime? {
        val id = optInt("id", 0)
        if (id <= 0) return null
        return Anime(
            id = id,
            name = optString("name"),
            nameCn = optString("name_cn").takeIf { it.isNotBlank() },
            score = optJSONObject("rating")?.optDouble("score")?.takeIf { it > 0 },
            airWeekday = optInt("air_weekday", 0).takeIf { it in 1..7 },
        )
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", UA)
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
