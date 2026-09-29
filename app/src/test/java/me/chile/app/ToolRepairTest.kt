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

class ToolRepairTest {
    @Test fun malformedResponseEnvelopeIsRetriedWithoutExecutingTools()=runBlocking {
        var count=0
        val http=OkHttpClient.Builder().addInterceptor {chain->
            count++
            val body=if(count==1)"{\"choices\":null}" else """{"choices":[{"finish_reason":"stop","message":{"content":"可以，先说说你的口味。"}}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val reply=AiClient(http).agentChat(ModelConfig(),"synthetic-test",listOf(ChatMessage(role="user",text="晚饭吃什么")),emptyList(),emptyList())
        assertEquals(2,count);assertNull(reply.proposal);assertNull(reply.exercise)
        assertEquals("可以，先说说你的口味。",reply.text)
    }
    @Test fun malformedThenWrongTypeThenCorrectedMealCreatesExactlyOneProposal()=runBlocking {
        val requests=mutableListOf<JSONObject>()
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val buffer=okio.Buffer();chain.request().body!!.writeTo(buffer);requests.add(JSONObject(buffer.readUtf8()))
            val args=when(requests.size) {
                1->"{broken"
                2->"""{"items":[{"name":"米饭","quantity":100,"unit":"g","kcal":130,"category":"主食","nutrients":{"protein_g":"2.5g","carbs_g":28,"fat_g":0.3}}]}"""
                else->"""{"items":[{"name":"米饭","quantity":100,"unit":"g","kcal":130,"category":"主食","nutrients":{"protein_g":2.5,"carbs_g":28,"fat_g":0.3}}]}"""
            }
            val message=JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("id","call-${requests.size}").put("type","function")
                .put("function",JSONObject().put("name","record_meal").put("arguments",args))))
            val body=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","tool_calls").put("message",message)))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val reply=AiClient(http).agentChat(ModelConfig(),"synthetic-test",listOf(ChatMessage(role="user",text="吃了100克米饭")),emptyList(),emptyList())
        assertEquals(3,requests.size)
        assertEquals(28.0,reply.proposal!!.items.single().nutrients.carbsG!!,.001)
        assertTrue(requests[2].getJSONArray("messages").toString().contains("nutrients.protein_g"))
        assertNull(reply.baseline)
    }
    @Test fun progressReturnsNutrientsTogetherWithCaloriesAndCoverage() {
        val profile=Profile("female",30,165.0,60.0,"2026-09-28")
        val meal=Meal(date=profile.date,items=listOf(FoodItem("米饭",100.0,130,nutrients=Nutrients(2.5,28.0,.3)),FoodItem("旧餐",null,200)))
        val result=ActivityTools(listOf(meal),emptyList(),listOf(profile),"test",LocalDateTime.parse("2026-09-28T12:00"))
            .execute("query_progress","""{"from":"2026-09-27","to":"2026-09-28"}""")
        validateToolResult("query_progress",result)
        val days=result.getJSONArray("nutrition")
        assertTrue(days.getJSONObject(0).getJSONArray("parts").getJSONObject(0).isNull("grams"))
        val protein=days.getJSONObject(1).getJSONArray("parts").getJSONObject(0)
        assertEquals(2.5,protein.getDouble("grams"),.001)
        assertTrue(protein.getBoolean("incomplete"))
        assertEquals(330,result.getInt("recorded_intake_kcal"))
        assertTrue(runCatching {validateToolResult("query_progress",JSONObject().put("status","ok"))}.isFailure)
    }
}
