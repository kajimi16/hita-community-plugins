package com.stupidtree.hita.ui.anime

import com.stupidtree.hita.ui.anime.data.HitaniApi
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
