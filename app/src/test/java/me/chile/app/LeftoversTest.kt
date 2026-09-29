package me.chile.app

import me.chile.app.data.*
import me.chile.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

class LeftoversTest {
    private val now=LocalDateTime.parse("2026-09-25T12:30")
    private val before=Meal(id="before",date="2026-09-25",source="饭前估算 · 尚未食用",items=listOf(FoodItem("米饭",null,400,"主食",300.0),FoodItem("牛奶",null,150,"奶类",250.0,"ml")))
    private fun query(tools: MealTools) {assertEquals("ok",tools.execute("query_meals","""{"from":"2026-09-25","to":"2026-09-25"}""").getString("status"))}
    private fun edit(tools: MealTools,id: String,items: List<FoodItem>) = tools.execute("edit_meal",
        JSONObject().put("meal_id",id).put("items",org.json.JSONArray(items.map {it.json().apply {remove("grams")}})).toString())
    @Test fun ordinaryEditPreservesOriginalAndUpdatesPendingCard() {
        val tools=MealTools(emptyList(),now,listOf(before));query(tools)
        val eaten=listOf(before.items[0].copy(quantity=150.0,kcal=200),before.items[1])
        assertEquals("pending_confirmation",edit(tools,before.id,eaten).getString("status"))
        val pending=tools.proposal!!
        assertEquals(before.id,pending.id)
        assertEquals(before.items,pending.beforeItems)
        assertEquals(350,pending.kcal)
        assertEquals(pending,mealJson(pending.json().toString()))
        val again=MealTools(emptyList(),now,listOf(pending));query(again)
        edit(again,pending.id,listOf(before.items[0].copy(quantity=270.0,kcal=360),before.items[1]))
        assertEquals(510,again.proposal!!.kcal)
        assertEquals(before.items,again.proposal!!.beforeItems)
        assertEquals(pending.id,again.proposal!!.id)
    }
    @Test fun confirmedMealUsesReplacementAndNoSeparateLeftoverTool() {
        val tools=MealTools(listOf(before),now)
        val eaten=listOf(before.items[0].copy(quantity=0.0,kcal=0),before.items[1].copy(quantity=0.0,kcal=0))
        assertEquals("error",edit(tools,before.id,eaten).getString("status"))
        query(tools)
        assertEquals("pending_confirmation",edit(tools,before.id,eaten).getString("status"))
        assertEquals(before.id,tools.proposal!!.replacesId)
        assertEquals(0,tools.proposal!!.kcal)
        assertTrue(tools.proposal!!.valid())
        assertFalse(mealToolDefinitions().toString().contains("compare_leftovers"))
    }
    @Test fun multimodalAgentCarriesTwoPhotosTimestampsAndTools() = runBlocking {
        var sent: JSONObject?=null
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val buffer=okio.Buffer();chain.request().body!!.writeTo(buffer);sent=JSONObject(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"choices":[{"finish_reason":"stop","message":{"content":"这是同一餐吗？"}}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val messages=listOf(ChatMessage(id="a",role="user",text="饭前",createdAt="2026-09-25T12:00"),ChatMessage(id="b",role="user",text="剩下这些",createdAt="2026-09-25T12:30"))
        AiClient(http).agentChat(ModelConfig(visionModel="vision-test"),"fake-key",messages,emptyList(),emptyList(),images=mapOf("a" to "AA==","b" to "BB=="))
        val body=sent!!;assertEquals("vision-test",body.getString("model"));assertEquals(mealToolDefinitions().length()+activityToolDefinitions().length(),body.getJSONArray("tools").length())
        val history=body.getJSONArray("messages")
        assertTrue(history.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text").contains("12:00"))
        assertEquals("data:image/jpeg;base64,BB==",history.getJSONObject(2).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url"))
    }
}
