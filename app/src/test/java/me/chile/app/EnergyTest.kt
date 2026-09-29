package me.chile.app

import me.chile.app.domain.*
import me.chile.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EnergyTest {
    @Test fun queryRangesRejectReverseOverlongAndFutureDates() {
        val tools=ActivityTools(emptyList(),emptyList(),emptyList(),"m",LocalDateTime.parse("2026-09-26T18:00"))
        for(name in listOf("query_exercises","query_progress")) {
            for((from,to) in listOf("2026-09-26" to "2026-09-25","2026-08-25" to "2026-09-26","2026-09-26" to "2026-09-27")) {
                assertEquals("error",tools.execute(name,"""{"from":"$from","to":"$to"}""").getString("status"))
            }
            assertEquals("ok",tools.execute(name,"""{"from":"2026-08-27","to":"2026-09-26"}""").getString("status"))
        }
    }
    private val profile=Profile("male",30,175.0,70.0,"2026-09-20")
    private val now=LocalDateTime.parse("2026-09-26T18:00")
    @Test fun progressExcludesMissingDaysAndNeverUsesFutureWeight() {
        val meals=listOf(Meal(date="2026-09-21",items=listOf(FoodItem("饭",null,1300))),Meal(date="2026-09-19",items=listOf(FoodItem("饭",null,1500))))
        val ex=Exercise(date="2026-09-21",time="17:00",name="散步",minutes=30,activeKcal=100,source="设备",messageId="m")
        val p=energyProgress(meals,listOf(ex),listOf(profile.copy(weight=90.0,date="2026-09-26"),profile),LocalDate.parse("2026-09-19"),LocalDate.parse("2026-09-25"))
        assertEquals(2,p.recordedDays);assertEquals(1,p.comparableDays)
        assertEquals(1300-profile.resting()!!-100,p.balance)
        assertEquals(-p.balance!!/7700.0,p.fatEquivalentKg!!,.000001)
        assertNull(p.days.first().balance);assertNull(p.days.last().intake)
    }
    @Test fun netActivityExcludesRestAndRoundTrips() {
        assertEquals(279,estimateActiveKcal("快走",60,profile))
        val tools=ActivityTools(emptyList(),emptyList(),listOf(profile),"message",now)
        assertEquals("ready_to_save",tools.execute("record_exercise","""{"name":"快走","minutes":60}""").getString("status"))
        val ex=tools.proposal!!;assertEquals(279,ex.activeKcal);assertEquals(ex,exerciseJson(ex.json().toString()))
        assertEquals("error",tools.execute("record_exercise","""{"name":"快走","minutes":60}""").getString("status"))
        val retry=ActivityTools(emptyList(),listOf(ex),listOf(profile),"message",now)
        assertEquals("error",retry.execute("record_exercise","""{"name":"快走","minutes":60}""").getString("status"))
    }
    @Test fun invalidOrUnknownActivityDoesNotCrashOrInventCalories() {
        for(raw in listOf("{}","""{"name":"冷门运动","minutes":20}""","""{"name":"散步","minutes":-3}""","""{"name":"散步","minutes":2.5}""","""{"name":"散步","minutes":30,"date":"2026-09-27"}""","""{"name":"散步","minutes":30,"active_kcal":"200"}""")) {
            val tools=ActivityTools(emptyList(),emptyList(),listOf(profile),"m",now)
            assertEquals("error",tools.execute("record_exercise",raw).getString("status"));assertNull(tools.proposal)
        }
        val noProfile=ActivityTools(emptyList(),emptyList(),emptyList(),"m",now)
        assertEquals("ready_to_save",noProfile.execute("record_exercise","""{"name":"游泳","minutes":30,"active_kcal":230}""").getString("status"))
    }
    @Test fun summaryUsesAllMealsRatherThanTwentyRowQueryLimit() {
        val meals=(1..25).map {Meal(date="2026-09-25",items=listOf(FoodItem("点心",null,10)))}
        val tools=ActivityTools(meals,emptyList(),listOf(profile),"m",now)
        val result=tools.execute("query_progress","""{"from":"2026-09-20","to":"2026-09-26"}""")
        assertEquals(250,result.getInt("recorded_intake_kcal"));assertEquals(1,result.getInt("recorded_days"))
        assertTrue(result.getString("definition").contains("不是实际"))
        assertEquals("error",tools.execute("query_progress","""{"from":"2025-09-20","to":"2026-09-26"}""").getString("status"))
    }
}
