package me.chile.app

import kotlinx.coroutines.runBlocking
import me.chile.app.data.*
import me.chile.app.domain.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentPromptTest {
    @Test fun failedMutationCannotBecomeATextOnlySuccessReceipt()=runBlocking {
        var count=0
        val http=OkHttpClient.Builder().addInterceptor {chain->
            count++
            val msg=if(count==1)JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("id","bad").put("type","function")
                .put("function",JSONObject().put("name","update_profile").put("arguments","""{"weight":"65kg"}"""))))
                else JSONObject().put("content","已帮你修改好了")
            val body=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",if(count==1)"tool_calls" else "stop").put("message",msg)))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val result=runCatching {AiClient(http).agentChat(ModelConfig(),"local-test",listOf(ChatMessage(role="user",text="体重改65公斤")),emptyList(),emptyList())}
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("尚未新增或修改"))
    }
    @Test fun naturalLanguageProfileChangeReturnsLocalConfirmationRatherThanSaving()=runBlocking {
        val requests=mutableListOf<JSONObject>()
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val buffer=Buffer();chain.request().body!!.writeTo(buffer);requests.add(JSONObject(buffer.readUtf8()))
            val name=if(requests.size==1)"query_settings" else "update_profile"
            val args=if(requests.size==1)"{}" else """{"weight":65}"""
            val call=JSONObject().put("id","c${requests.size}").put("type","function").put("function",JSONObject().put("name",name).put("arguments",args))
            val body=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","tool_calls").put("message",JSONObject().put("tool_calls",JSONArray().put(call)))))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val p=Profile("male",30,175.0,70.0,activity="light")
        val reply=AiClient(http).agentChat(ModelConfig(),"local-test",listOf(ChatMessage(role="user",text="体重改成65公斤")),emptyList(),emptyList(),profiles=listOf(p))
        assertEquals(2,requests.size);assertNull(reply.proposal);assertNull(reply.exercise)
        assertEquals(65.0,profileJson(reply.change!!.after!!).weight,0.0)
        assertEquals(p,profileJson(reply.change!!.before!!))
        val history=requests.last().getJSONArray("messages")
        assertEquals("tool",history.getJSONObject(history.length()-1).getString("role"))
        assertTrue(reply.text.contains("确认"))
    }
    @Test fun askingForInformationMustBlockMutationsEvenWhenModelOrdersThemFirst()=runBlocking {
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val calls=JSONArray()
            for((name,args) in listOf("record_exercise" to """{"name":"快走","minutes":30,"active_kcal":140}""", "ask_user" to """{"question":"运动了多久？","options":["15分钟","30分钟"]}""")) {
                calls.put(JSONObject().put("id",name).put("type","function").put("function",JSONObject().put("name",name).put("arguments",args)))
            }
            val body=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","tool_calls").put("message",JSONObject().put("tool_calls",calls))))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val reply=AiClient(http).agentChat(ModelConfig(),"local-test",listOf(ChatMessage(role="user",text="刚刚去快走了")),emptyList(),emptyList())
        assertNull(reply.exercise);assertNull(reply.proposal);assertEquals(listOf("15分钟","30分钟"),reply.options)
    }
    @Test fun modelChoicesBecomeButtonsAndPhotoSurvivesTheNextTurn()=runBlocking {
        val requests=mutableListOf<JSONObject>()
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val buffer=Buffer();chain.request().body!!.writeTo(buffer);requests.add(JSONObject(buffer.readUtf8()))
            val message=if(requests.size==1)JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("id","ask").put("type","function")
                .put("function",JSONObject().put("name","ask_user").put("arguments","""{"question":"这是新的一餐吗？","options":["新增一餐","修改上一餐"]}"""))))
                else JSONObject().put("content","看到了新图片")
            val body=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",if(requests.size==1)"tool_calls" else "stop").put("message",message)))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val photo=ChatMessage(id="order",role="user",text="记一下",photo="order.jpg")
        val client=AiClient(http)
        val reply=client.agentChat(ModelConfig(),"local-test",listOf(photo),emptyList(),emptyList(),images=mapOf(photo.id to "synthetic-image"))
        assertEquals(listOf("新增一餐","修改上一餐"),reply.options);assertNull(reply.proposal);assertEquals(1,requests.size)
        val answer=ChatMessage(role="user",text=reply.text+"\n新增一餐")
        val context=buildChatContext(listOf(photo,ChatMessage(role="assistant",text=reply.text,options=reply.options),answer),emptyList(),emptyMap(),emptyMap(),answer)
        client.agentChat(ModelConfig(),"local-test",context.messages,emptyList(),emptyList(),images=context.images.associate {it.id to "synthetic-image"})
        val sent=requests.last().getJSONArray("messages")
        assertTrue(sent.toString().contains("data:image/jpeg;base64,synthetic-image"))
        assertEquals(answer.text,sent.getJSONObject(sent.length()-1).getString("content").substringAfter("] "))
    }
    @Test fun exhaustedInvalidRecordingRemainsRetryableAndReportsActualTool()=runBlocking {
        var requests=0
        val http=OkHttpClient.Builder().addInterceptor {chain->
            requests++
            val message=JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("id","bad-$requests").put("type","function")
                .put("function",JSONObject().put("name","record_meal").put("arguments","""{"items":[]}"""))))
            val response=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","tool_calls").put("message",message)))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body(response.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val result=runCatching { AiClient(http).agentChat(ModelConfig(),"local-test",listOf(ChatMessage(role="user",text="新加这个",photo="order.jpg")),emptyList(),emptyList(),images=mapOf("unused" to "fixture")) }
        assertTrue("Tool exhaustion must keep retry state, not persist a misleading assistant answer",result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("record_meal"))
        assertFalse(result.exceptionOrNull()!!.message!!.contains("缩小日期"))
        assertEquals(3,requests)
    }
    @Test fun legacyPersonaNeverReachesPlainChatAndStreamingStillWorks()=runBlocking {
        val requests=mutableListOf<JSONObject>()
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val buffer=Buffer();chain.request().body!!.writeTo(buffer)
            val body=JSONObject(buffer.readUtf8());requests.add(body)
            val system=body.getJSONArray("messages").getJSONObject(0).getString("content")
            val text="可以试试，"
            val chunks=listOf(text,"先说说你的口味。")
            val stream=chunks.joinToString("") {part->
                "data: "+JSONObject().put("choices",JSONArray().put(JSONObject()
                    .put("delta",JSONObject().put("content",part))))+"\n\n"
            }+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(stream.toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        val client=AiClient(http)
        val history=listOf(ChatMessage(role="assistant",text="旧的人设回复"),ChatMessage(role="user",text="晚饭吃什么好？"))
        for(mode in listOf("native","blue_fish","native")) {
            val config=configJson(ModelConfig().json().put("personaMode",mode).put("persona","NATIVE_MARKER").put("fishPersona","FISH_MARKER").toString())
            val updates=mutableListOf<String>()
            val reply=client.agentChat(config,"local-test",history,emptyList(),emptyList(),onText={updates.add(it)})
            val prefix="可以试试，"
            assertEquals(prefix+"先说说你的口味。",reply.text)
            assertTrue(updates.contains(prefix));assertEquals(reply.text,updates.last())
            assertNull(reply.proposal);assertNull(reply.exercise)
            val system=requests.last().getJSONArray("messages").getJSONObject(0).getString("content")
            assertFalse(system.contains("FISH_MARKER"))
            assertFalse(system.contains("NATIVE_MARKER"))
        }
        assertEquals(3,requests.size)
    }

    @Test fun legacyPersonaNeverReachesToolRequestsAndReplyIsNotReplaced()=runBlocking {
        for(mode in listOf("native","blue_fish")) {
            val requests=mutableListOf<JSONObject>()
            val updates=mutableListOf<String>()
            val expected="请核对卡片后确认。"
            val http=OkHttpClient.Builder().addInterceptor {chain->
                val buf=Buffer();chain.request().body!!.writeTo(buf)
                requests.add(JSONObject(buf.readUtf8()))
                val message=if(requests.size==1)JSONObject().put("tool_calls",JSONArray().put(JSONObject()
                    .put("id","meal1").put("type","function").put("function",JSONObject().put("name","record_meal")
                        .put("arguments","""{"meal_type":"午餐","items":[{"name":"米饭","quantity":100,"unit":"g","kcal":130,"category":"主食"}]}"""))))
                    else JSONObject().put("content",expected)
                val response=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",if(requests.size==1)"tool_calls" else "stop").put("message",message)))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(response.toString().toResponseBody("application/json".toMediaType())).build()
            }.build()
            val config=configJson(ModelConfig().json().put("personaMode",mode).put("persona","NATIVE_ONLY").put("fishPersona","FISH_ONLY").toString())
            val reply=AiClient(http).agentChat(config,"local-test",listOf(ChatMessage(role="user",text="午饭吃了100克米饭，记一下")),emptyList(),emptyList(),onText={updates.add(it)})
            assertEquals(2,requests.size)
            requests.forEach {body->
                val system=body.getJSONArray("messages").getJSONObject(0).getString("content")
                assertFalse(system.contains("FISH_ONLY"))
                assertFalse(system.contains("NATIVE_ONLY"))
            }
            assertFalse(requests.last().has("tools"))
            assertFalse(requests.last().has("tool_choice"))
            assertTrue(requests.last().getJSONArray("messages").toString().contains("pending_confirmation"))
            assertEquals(expected,reply.text);assertTrue(updates.contains(expected))
            assertEquals(130,reply.proposal!!.kcal)
        }
    }

    @Test fun failedFinalPhrasingKeepsExerciseAndDoesNotRepeatTool()=runBlocking {
        var requests=0
        val http=OkHttpClient.Builder().addInterceptor {chain->
            requests++
            val message=JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("id","exercise1").put("type","function")
                .put("function",JSONObject().put("name","record_exercise").put("arguments","""{"name":"快走","minutes":30,"active_kcal":140}"""))))
            val response=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","tool_calls").put("message",message)))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if(requests==1)200 else 500).message("test")
                .body(response.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val reply=AiClient(http).agentChat(ModelConfig(),"local-test",listOf(ChatMessage(role="user",text="刚快走30分钟，设备显示活动140千卡")),emptyList(),emptyList())
        assertEquals(2,requests);assertEquals(140,reply.exercise!!.activeKcal)
        assertTrue(reply.text.contains("140"));assertFalse(reply.text.contains("已记录"))
    }

}
