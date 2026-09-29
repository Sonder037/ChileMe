package me.chile.app

import kotlinx.coroutines.runBlocking
import me.chile.app.data.*
import me.chile.app.domain.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class MealToolsTest {
    @Test fun truncatedDayCanBePagedToFindAndEditPendingCard() {
        val date="2026-09-25"
        val meals=(1..20).map {Meal(id="saved-$it",date=date,items=listOf(FoodItem("米饭",100.0,130)))}
        val pending=meals.first().copy(id="pending")
        val tools=MealTools(meals,LocalDateTime.parse("${date}T12:00"),listOf(pending))
        val args=JSONObject().put("from",date).put("to",date)
        val first=tools.execute("query_meals",args.toString())
        assertTrue(first.getBoolean("truncated"));assertEquals(20,first.getInt("next_offset"))
        val second=tools.execute("query_meals",args.put("offset",20).toString())
        assertFalse(second.getBoolean("truncated"));assertTrue(second.isNull("next_offset"))
        assertEquals("pending",second.getJSONArray("meals").getJSONObject(0).getString("meal_id"))
        assertEquals("draft",second.getJSONArray("meals").getJSONObject(0).getString("state"))
        assertEquals("pending_confirmation",tools.execute("edit_meal","""{"meal_id":"pending","items":[{"name":"米饭","quantity":150,"unit":"g","kcal":195,"category":"主食"}]}""").getString("status"))
        assertEquals("pending",tools.proposal!!.id)
        for(invalid in listOf(-1,1.5,"20")) {
            assertEquals("error",tools.execute("query_meals",args.put("offset",invalid).toString()).getString("status"))
        }
    }
    @Test fun queriedUnconfirmedCardCanBeEditedWithoutCreatingAnotherDraft() {
        val draft=Meal(id="before",date="2026-09-25",items=listOf(FoodItem("米饭",100.0,130)),source="饭前估算 · 尚未食用")
        val tools=MealTools(emptyList(),java.time.LocalDateTime.parse("2026-09-25T12:00"),listOf(draft))
        tools.execute("query_meals","""{"from":"2026-09-25","to":"2026-09-25"}""")
        val result=tools.execute("edit_meal","""{"meal_id":"before","items":[{"name":"米饭","quantity":150,"unit":"g","kcal":195,"category":"主食"}]}""")
        assertEquals("pending_confirmation",result.getString("status"))
        assertEquals(draft.id,tools.proposal!!.id)
        assertEquals(195,tools.proposal!!.kcal)
        assertNull(tools.proposal!!.replacesId)
        assertEquals(draft,tools.baseline)
    }
    private val now=LocalDateTime.parse("2026-09-25T08:30:00")
    private val items="""[{"name":"燕麦","quantity":50,"unit":"g","kcal":190,"category":"主食"}]"""
    @Test fun futureMealCannotBecomeActualIntakeButCanBeEstimated() {
        val old=Meal(id="existing",date="2026-09-24",items=listOf(FoodItem("燕麦",50.0,190)))
        for(name in listOf("record_meal","edit_meal")) {
            val tools=MealTools(listOf(old),now)
            tools.execute("query_meals","""{"from":"2026-09-24","to":"2026-09-24"}""")
            val result=tools.execute(name,"""{"meal_id":"existing","date":"2026-09-26","items":$items}""")
            assertEquals(name,"error",result.getString("status"))
            assertNull(tools.proposal)
            assertEquals("pending_confirmation",tools.execute(name,"""{"meal_id":"existing","date":"2026-09-25","items":$items}""").getString("status"))
        }
        val estimate=MealTools(emptyList(),now)
        assertEquals("pending_confirmation",estimate.execute("estimate_meal","""{"date":"2026-09-26","items":$items}""").getString("status"))
        assertTrue(estimate.proposal!!.source.startsWith("饭前估算"))
    }
    @Test fun creationInfersTimeAndRequiresConfirmation() {
        val tools=MealTools(emptyList(),now)
        val result=tools.execute("record_meal","""{"items":$items}""")
        assertEquals("pending_confirmation",result.getString("status"))
        assertEquals("早餐",tools.proposal!!.title);assertEquals("08:30",tools.proposal!!.time)
        assertEquals("主食",tools.proposal!!.items.single().category)
        assertEquals("error",tools.execute("record_meal","""{"items":$items}""").getString("status"))
        assertEquals("晚餐",inferMealType(LocalTime.of(8,0),"晚饭"))
        assertEquals("午餐",inferMealType(LocalTime.NOON));assertEquals("加餐",inferMealType(LocalTime.of(16,0)))
        assertEquals("晚餐",inferMealType(LocalTime.of(19,0)));assertEquals("夜宵",inferMealType(LocalTime.of(23,0)))
    }
    @Test fun editMustUseQueriedIdAndKeepsBaseline() {
        val old=Meal(id="existing",date="2026-09-24",title="午餐",time="12:00",items=listOf(FoodItem("米饭",100.0,130)))
        val tools=MealTools(listOf(old),now)
        val args="""{"meal_id":"existing","items":$items}"""
        assertEquals("error",tools.execute("edit_meal",args).getString("status"))
        assertEquals(1,tools.execute("query_meals","""{"from":"2026-09-24","to":"2026-09-24"}""").getJSONArray("meals").length())
        assertEquals("pending_confirmation",tools.execute("edit_meal",args).getString("status"))
        assertEquals(old,tools.baseline);assertEquals("existing",tools.proposal!!.replacesId)
        assertNotEquals(old.id,tools.proposal!!.id);assertEquals(old.date,tools.proposal!!.date)
    }
    @Test fun unknownInvalidAndExcessiveRequestsCannotCreateProposals() {
        val tools=MealTools(emptyList(),now)
        listOf("unknown" to "{}", "record_meal" to "{", "record_meal" to """{"items":[{"name":"未知","kcal":null}]}""", "query_meals" to """{"from":"2020-01-01","to":"2026-09-25"}""").forEach {(name,args)->assertEquals("error",tools.execute(name,args).getString("status"))}
        assertNull(tools.proposal)
    }
    @Test fun agentUsesActualToolProtocolAndFeedsQueryResultBack() = runBlocking {
        val old=Meal(id="known",date="2026-09-24",title="午餐",items=listOf(FoodItem("饭",100.0,130)))
        val requests=mutableListOf<JSONObject>()
        val client=OkHttpClient.Builder().addInterceptor {chain->
            val buffer=okio.Buffer();chain.request().body!!.writeTo(buffer)
            val body=checkedJsonObject(buffer.readUtf8());requests.add(body)
            val name=if(requests.size==1)"query_meals" else "edit_meal"
            val args=if(requests.size==1)"""{"from":"2026-09-24","to":"2026-09-24"}""" else """{"meal_id":"known","items":$items}"""
            val call=JSONObject().put("id","call_${requests.size}").put("type","function").put("function",JSONObject().put("name",name).put("arguments",args))
            val response=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","tool_calls").put("message",JSONObject().put("role","assistant").put("content",JSONObject.NULL).put("tool_calls",JSONArray().put(call)))))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val result=AiClient(client).agentChat(ModelConfig(),"synthetic-test-key",listOf(ChatMessage(role="user",text="修改昨天的午餐")),emptyList(),listOf(old))
        assertEquals(3,requests.size);assertEquals(mealToolDefinitions().length()+activityToolDefinitions().length(),requests.first().getJSONArray("tools").length())
        val history=requests[1].getJSONArray("messages")
        assertEquals("tool",history.getJSONObject(history.length()-1).getString("role"))
        assertEquals("known",result.proposal!!.replacesId);assertEquals(old,result.baseline)
    }
}
