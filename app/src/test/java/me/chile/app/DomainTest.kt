package me.chile.app

import me.chile.app.domain.*
import me.chile.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    @Test fun zeroAmountCannotCarryCaloriesInEitherLegacyOrCurrentFormat() {
        assertFalse(FoodItem("米饭",0.0,130).valid())
        assertFalse(FoodItem("米饭",null,130,quantity=0.0).valid())
        assertTrue(FoodItem("米饭",0.0,0).valid())
        assertTrue(FoodItem("米饭",null,0,quantity=0.0).valid())
        assertTrue(recognitionResult("""{"items":[{"name":"米饭","grams":0,"kcal":130}]}""").items.isEmpty())
    }
    @Test fun malformedOrDeepModelOutputDegradesWithoutCreatingMeals() {
        listOf("不是JSON", "{", "null", "[]", """{"items":null}""", """{"items":[{"name":"未知","grams":"约一碗","kcal":null}]}""", "[".repeat(10000)+"]".repeat(10000)).forEach {
            val result=recognitionResult(it);assertTrue(result.items.isEmpty());assertTrue(result.summary.isNotBlank())
        }
        assertThrows(IllegalArgumentException::class.java){checkedJsonObject("[".repeat(40)+"]".repeat(40))}
        assertEquals("[食物]",checkedJsonObject("""{"name":"[食物]"}""").getString("name"))
    }

    @Test fun deepSeekRequestUsesExplicitNonThinkingAndImageJson() {
        val body=requestPayload(ModelConfig(),"deepseek-flash",org.json.JSONArray(),true)
        assertEquals("disabled",body.getJSONObject("thinking").getString("type"))
        assertEquals(8192,body.getInt("max_tokens"))
        assertEquals("json_object",body.getJSONObject("response_format").getString("type"))
        assertFalse(requestPayload(ModelConfig(baseUrl="https://example.com/v1"),"custom",org.json.JSONArray()).has("thinking"))
        assertEquals("deepseek-flash",ModelConfig().visionModel)
        assertFalse(ModelConfig().offline)
    }
    @Test fun recognitionKeepsSummaryAndHandlesNoFood() {
        val result=recognitionResult("""{"summary":"核对一下份量","items":[{"name":"饭","grams":150,"kcal":200}]}""")
        assertEquals(200,recognitionResult("""{"items":[{"name":"稀有食物","grams":"150","kcal":"200"}]}""").items.single().kcal)
        assertEquals("核对一下份量",result.summary);assertEquals(200,result.items.single().kcal)
        assertTrue(recognitionResult("""{"items":[]}""").items.isEmpty())
        assertTrue(recognitionResult("""{"items":[{"name":"饭","grams":150,"kcal":-1}]}""").items.isEmpty())
    }

    @Test fun restingHasExplicitApplicability() {
        assertEquals(1649,Profile("male",30,175.0,70.0).resting())
        assertEquals(1483,Profile("female",30,175.0,70.0).resting())
        assertNull(Profile("unspecified",30,175.0,70.0).resting())
        assertNull(Profile("male",18,175.0,70.0).resting())
        assertFalse(Profile("male",30,Double.NaN,70.0).valid())
    }
    @Test fun averagesUseRecordedDaysNotMealsOrMissingDays() {
        val meals=listOf(Meal(date="2026-09-21",items=listOf(FoodItem("A",null,500))),Meal(date="2026-09-21",items=listOf(FoodItem("B",null,700))),Meal(date="2026-09-23",items=listOf(FoodItem("C",null,0))))
        val result=trend(meals,Period.WEEK,LocalDate.parse("2026-09-24"))
        assertEquals(2,result.days);assertEquals(1200,result.total);assertEquals(600,result.average)
        assertNull(result.buckets[1].average);assertEquals(0,result.buckets[2].average)
    }
    @Test fun calendarHandlesLeapYearsAndYearBoundary() {
        assertEquals(29,trend(emptyList(),Period.MONTH,LocalDate.parse("2024-02-10")).buckets.size)
        assertEquals(LocalDate.parse("2025-12-29"),trend(emptyList(),Period.WEEK,LocalDate.parse("2026-01-01")).from)
    }
    @Test fun parserRejectsUntrustedNumericsAndOversizedLists() {
        assertEquals(195,recognitionResult("""{"items":[{"name":"米饭","grams":150,"kcal":195}]}""").items.single().kcal)
        listOf("-1","1.5","10001","1e99").forEach {value->
            assertTrue(recognitionResult("""{"items":[{"name":"A","kcal":$value}]}""").items.isEmpty())
        }
        val item="""{"name":"米饭","quantity":100,"unit":"g","kcal":130}"""
        assertTrue(recognitionResult("""{"items":[${List(31){item}.joinToString(",")}] }""").items.isEmpty())
        assertTrue(recognitionResult("x".repeat(50001)).items.isEmpty())
    }
    @Test fun endpointRequiresHttpsAndNoEmbeddedCredentials() {
        assertEquals("https://example.com/v1/chat/completions",endpoint("https://example.com/v1/").toString())
        listOf("http://example.com","https://key@example.com","https://example.com?key=x").forEach {assertThrows(Exception::class.java){endpoint(it)}}
    }
    @Test fun mealRoundTripPreservesMissingGramsAndIdentity() {
        val meal=Meal(items=listOf(FoodItem("米饭",null,200)))
        assertEquals(meal,mealJson(meal.json().toString()))
        assertFalse(meal.copy(items=emptyList()).valid())
    }
}
