package com.stupidtree.hita.ui.anime

import com.stupidtree.hita.ui.anime.data.CalendarDay
import com.stupidtree.hita.ui.anime.data.HitaniApi
import com.stupidtree.hita.ui.anime.data.ImageSet
import com.stupidtree.hita.ui.anime.data.buildAiringSchedule
import com.stupidtree.hita.ui.anime.data.SubjectDetail
import com.stupidtree.hita.ui.anime.data.SubjectSummary
import com.stupidtree.hita.ui.anime.data.formatIso
import com.stupidtree.hita.ui.anime.data.todayWeekday
import com.stupidtree.hita.ui.anime.data.weekDates
import com.stupidtree.hita.ui.anime.data.weekRange
import com.stupidtree.hita.ui.anime.data.weekdayCn
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DataLayerTest {

    @Test
    fun weekRangeStartsOnMondayAndContainsSevenDays() {
        val (start, end) = weekRange(0)
        val startCalendar = Calendar.getInstance().apply {
            time = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(start)!!
        }
        assertEquals(Calendar.MONDAY, startCalendar.get(Calendar.DAY_OF_WEEK))
        assertTrue(start <= end)

        val dates = weekDates(0)
        assertEquals(7, dates.size)
        assertEquals(start, dates.first())
        assertEquals(end, dates.last())
        assertEquals(7, weekDates(1).size)
        assertTrue(weekDates(1).first() > dates.last())
    }

    @Test
    fun weekdayNamesMapOneToMondayAndSevenToSunday() {
        assertEquals("周一", weekdayCn(1))
        assertEquals("周日", weekdayCn(7))
    }

    @Test
    fun todayWeekdayIsInRange() {
        assertTrue(todayWeekday() in 1..7)
    }

    @Test
    fun subjectSummaryParsesRatingAndDates() {
        val summary = SubjectSummary.fromJson(
            JSONObject(
                """
                {"id":8,"type":2,"name":"コードギアス","name_cn":"反叛的鲁路修",
                 "images":{"common":"https://example.com/c.jpg","large":"https://example.com/l.jpg"},
                 "date":"2008-04-06","rating":{"score":8.3,"rank":84,"total":19020},
                 "tags":[{"name":"原创","count":1656}]}
                """.trimIndent(),
            ),
        )
        assertEquals(8, summary.id)
        assertEquals("反叛的鲁路修", summary.displayName)
        assertEquals("https://example.com/c.jpg", summary.images.cover)
        assertEquals(8.3, summary.score!!, 0.001)
        assertEquals(84, summary.rank)
        assertEquals("2008-04-06", summary.airDate)
        assertEquals("原创", summary.tags.single().name)
    }

    @Test
    fun subjectDetailParsesHistogramAndInfobox() {
        val detail = SubjectDetail.fromJson(
            JSONObject(
                """
                {"id":8,"type":2,"name":"コードギアス","name_cn":"反叛的鲁路修",
                 "summary":"summary","date":"2008-04-06","platform":"TV","eps":25,
                 "images":{"large":"https://example.com/l.jpg"},
                 "tags":[{"name":"SUNRISE","count":2378}],
                 "infobox":[{"key":"中文名","value":"鲁路修"},
                            {"key":"别名","value":[{"v":"叛逆的鲁鲁修"},{"v":"R2"}]}],
                 "rating":{"score":8.3,"rank":84,"total":100,"count":{"10":5,"9":10,"8":20}},
                 "collection":{"wish":1,"collect":2,"doing":3,"on_hold":4,"dropped":5}}
                """.trimIndent(),
            ),
        )
        assertEquals(25, detail.eps)
        assertEquals(5, detail.ratingCounts[0])
        assertEquals(20, detail.ratingCounts[2])
        assertEquals(0, detail.ratingCounts.last())
        assertEquals("鲁路修", detail.infobox[0].value)
        assertEquals("叛逆的鲁鲁修 / R2", detail.infobox[1].value)
        assertEquals(3, detail.collection["doing"])
    }

    @Test
    fun airingScheduleKeepsLongRunningShowsAndAllSevenDays() {
        val longRunning = SubjectSummary(
            id = 1,
            type = 2,
            name = "ONE PIECE",
            nameCn = "航海王",
            images = ImageSet(),
            score = 8.7,
            rank = null,
            airDate = "1999-10-20",
        )
        val newShow = SubjectSummary(
            id = 2,
            type = 2,
            name = "New Show",
            nameCn = "新番",
            images = ImageSet(),
            score = 9.0,
            rank = null,
            airDate = "2026-10-03",
        )
        val dates = listOf(
            "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05",
            "2026-11-06", "2026-11-07", "2026-11-08",
        )
        val schedule = buildAiringSchedule(
            calendarDays = listOf(CalendarDay(3, "周三", listOf(longRunning, newShow))),
            weekOffset = 0,
            dates = dates,
        )
        assertEquals(7, schedule.days.size)
        assertEquals("2026-11-04", schedule.days[2].date)
        assertEquals(2, schedule.days[2].items.size)
        // 按评分降序
        assertEquals("新番", schedule.days[2].items[0].displayName)
        assertEquals("航海王", schedule.days[2].items[1].displayName)
        assertEquals(0, schedule.days[0].items.size)
        assertEquals("2026-11-02", schedule.weekStart)
        assertEquals("2026-11-08", schedule.weekEnd)
    }

    @Test
    fun hitaniBaseNormalizesTrailingSlashAndBlank() {
        assertEquals("https://ani.kajimi.cc", HitaniApi.normalizeBase("https://ani.kajimi.cc/"))
        assertEquals(HitaniApi.DEFAULT_BASE, HitaniApi.normalizeBase("   "))
        assertEquals("http://10.0.2.2:3100", HitaniApi.normalizeBase(" http://10.0.2.2:3100/ "))
    }

    @Test
    fun isoDateRoundTripsThroughCalendar() {
        val calendar = Calendar.getInstance()
        val iso = formatIso(calendar)
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(iso))
    }
}
