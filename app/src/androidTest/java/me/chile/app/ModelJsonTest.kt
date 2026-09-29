package me.chile.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.chile.app.data.checkedJsonObject
import me.chile.app.data.AiClient
import me.chile.app.domain.ChatMessage
import me.chile.app.domain.ModelConfig
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelJsonTest {
    @Test fun duplicateToolFieldIsRepairedBeforeCreatingMeal()=runBlocking {
        var calls=0
        val http=OkHttpClient.Builder().addInterceptor {chain->
            calls++
            val message=JSONObject().put("role","assistant").put("content",if(calls==3)"核对卡片即可。" else "")
            if(calls<3) {
                val args=if(calls==1)"""{"items":[{"name":"米饭","quantity":100,"unit":"g","category":"主食","kcal":130,"kcal":900}]}""" else """{"items":[{"name":"米饭","quantity":100,"unit":"g","category":"主食","kcal":130}]}"""
                message.put("tool_calls",JSONArray().put(JSONObject().put("id","local-$calls").put("type","function")
                    .put("function",JSONObject().put("name","record_meal").put("arguments",args))))
            }
            val body=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",if(calls<3)"tool_calls" else "stop").put("message",message)))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("local fixture")
                .body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val result=AiClient(http).agentChat(ModelConfig(),"local-only",listOf(ChatMessage(role="user",text="记一份米饭")),emptyList(),emptyList())
            assertEquals(3,calls);assertEquals(130,result.proposal!!.kcal)
        } finally {http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()}
    }
    @Test fun nonJsonSyntaxCannotBeCoercedIntoToolValues() {
        val invalid=listOf(
            """{"kcal":010}""", """{"kcal":0x10}""", """{"kcal":+10}""",
            """{"kcal":.5}""", """{"kcal":1.}""", """{"ok":TRUE}""",
            """{"name":true false}""", """{"name":milk}""",
            """{"kcal"=100}""", """{"kcal":100;}""", """{"kcal":100,}""",
            """{"items":[1,]}""", """{"items":[,1]}""", """{"items":[1,,2]}""",
            """{"kcal":/* hidden */100}""", """{"kcal":100}// ignored""",
            """{"name":"bad\q"}""", "{\"name\":\"a\nb\"}")
        for(raw in invalid)assertTrue("Non-JSON input must be rejected: $raw",runCatching {checkedJsonObject(raw)}.isFailure)
        val valid=checkedJsonObject("""{"n":-1.25e+2,"zero":0,"items":[],"nested":{},"ok":true,"unknown":null,"name":"a\n\t\u7c73\\\/\""}""")
        assertEquals(-125.0,valid.getDouble("n"),0.0)
        assertEquals(0,valid.getJSONArray("items").length())
    }
    @Test fun ambiguousAndIncompleteResponsesAreRejectedByAndroidParser() {
        val invalid=listOf(
            """{"kcal":100,"kcal":900}""",
            """{"kcal":100,"\u006bcal":900}""",
            """{"items":[{"name":"饭","kcal":100,"kcal":900}]}""",
            """{"kcal":100} {"kcal":900}""",
            """{"items":[{"name":"饭","kcal":100}""",
            "{\"a\":".repeat(40)+"0"+"}".repeat(40))
        for(raw in invalid)assertTrue("Must reject ambiguous or incomplete JSON: ${raw.take(100)}",runCatching {checkedJsonObject(raw)}.isFailure)
        assertEquals(100,checkedJsonObject("""{"item":{"kcal":100},"kcal":200}""").getJSONObject("item").getInt("kcal"))
        assertEquals(2,checkedJsonObject("""{"items":[{"kcal":100},{"kcal":200}],"note":"引用\"和冒号:，don't"}""").getJSONArray("items").length())
    }
}
