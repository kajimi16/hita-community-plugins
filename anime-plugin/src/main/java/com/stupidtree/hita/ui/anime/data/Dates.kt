package com.stupidtree.hita.ui.anime.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val dayFormat = SimpleDateFormat("M月d日", Locale.CHINA)
private val shortDayFormat = SimpleDateFormat("M/d", Locale.CHINA)

fun formatIso(calendar: Calendar): String = isoFormat.format(calendar.time)

fun todayIso(now: Long = System.currentTimeMillis()): String =
    isoFormat.format(Date(now))

fun formatDay(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching { dayFormat.format(isoFormat.parse(iso)!!) }.getOrDefault(iso)
}

fun formatShortDay(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching { shortDayFormat.format(isoFormat.parse(iso)!!) }.getOrDefault(iso)
}

/** BGM 周历 id：周一 = 1 … 周日 = 7。 */
fun weekdayCn(id: Int): String = when (id) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}

/** 今天对应的 BGM 周历 id。 */
fun todayWeekday(now: Long = System.currentTimeMillis()): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    return ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
}

/**
 * 相对当前周偏移 [offset] 周，返回周一与周日的 ISO 日期。
 *
 * 用 Calendar 而不是 java.time：插件 minSdk 23，未启用脱糖。
 */
fun weekRange(offset: Int, now: Long = System.currentTimeMillis()): Pair<String, String> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // Sunday = 1
    val daysSinceMonday = (dayOfWeek + 5) % 7
    calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday + offset * 7)
    val start = formatIso(calendar)
    calendar.add(Calendar.DAY_OF_MONTH, 6)
    val end = formatIso(calendar)
    return start to end
}

/** 一周七天的 ISO 日期（周一起）。 */
fun weekDates(offset: Int, now: Long = System.currentTimeMillis()): List<String> {
    val (start, _) = weekRange(offset, now)
    val calendar = Calendar.getInstance().apply {
        timeInMillis = isoFormat.parse(start)!!.time
    }
    return (0 until 7).map { index ->
        if (index > 0) calendar.add(Calendar.DAY_OF_MONTH, 1)
        formatIso(calendar)
    }
}

fun daysInMonth(year: Int, month: Int): Int {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
}

/** "2024-04-06" → 2024；无法解析返回 null。 */
fun yearOf(iso: String?): Int? =
    iso?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "00:00"
    val minutes = seconds / 60
    val remain = seconds % 60
    return "%02d:%02d".format(minutes, remain)
}
