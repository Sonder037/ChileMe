package me.chile.app

import me.chile.app.data.*
import me.chile.app.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StreamingTest {
    @Test fun jsonFallbackRejectsOversizedTextAndArgumentsBeforeUiCallback()=runBlocking {
        var payload=""
        val http=OkHttpClient.Builder().addInterceptor {chain->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload.toResponseBody("application/json".toMediaType())).build()}.build()
        try {
            for(tools in listOf(false,true)) {
                val message=JSONObject().put("content",if(tools)"准备查询" else "x".repeat(50001))
                if(tools)message.put("tool_calls",org.json.JSONArray().put(JSONObject().put("id","a").put("type","function").put("function",JSONObject().put("name","query_meals").put("arguments","x".repeat(50001)))))
                payload=JSONObject().put("choices",org.json.JSONArray().put(JSONObject().put("finish_reason",if(tools)"tool_calls" else "stop").put("message",message))).toString()
                var delivered=false
                assertTrue("Oversized JSON response must be rejected",runCatching {ChatTransport(http).message(ModelConfig(),"synthetic","test",org.json.JSONArray(),onText={delivered=true})}.isFailure)
                assertFalse("Rejected response must not enter the UI",delivered)
            }
        } finally {http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()}
    }
    @Test fun jsonFallbackAlsoRequiresCompleteUnambiguousToolResponses()=runBlocking {
        var payload=""
        val http=OkHttpClient.Builder().addInterceptor {chain->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload.toResponseBody("application/json".toMediaType())).build()}.build()
        val call=JSONObject("""{"id":"a","type":"function","function":{"name":"query_meals","arguments":"{}"}}""")
        fun response(finish: String?,duplicate: Boolean=false): String {
            val calls=org.json.JSONArray().put(call);if(duplicate)calls.put(call)
            return JSONObject().put("choices",org.json.JSONArray().put(JSONObject().put("finish_reason",finish?:JSONObject.NULL).put("message",JSONObject().put("content","准备记录").put("tool_calls",calls)))).toString()
        }
        try {
            val transport=ChatTransport(http)
            for(finish in listOf(null,"stop","length","unknown")) {
                payload=response(finish)
                assertTrue("Incomplete tool response accepted: $finish",runCatching {transport.message(ModelConfig(),"synthetic","test",org.json.JSONArray(),onText={})}.isFailure)
            }
            payload=response("tool_calls",true)
            assertTrue(runCatching {transport.message(ModelConfig(),"synthetic","test",org.json.JSONArray())}.isFailure)
            payload=response("tool_calls")
            assertEquals(1,transport.message(ModelConfig(),"synthetic","test",org.json.JSONArray()).getJSONArray("tool_calls").length())
        } finally {http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()}
    }
    @Test fun cancelledRequestCannotDeliverLateTextAndNextRequestStillWorks()=runBlocking {
        val started=CountDownLatch(1);val release=CountDownLatch(1);val finished=CountDownLatch(1)
        val count=AtomicInteger()
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val first=count.incrementAndGet()==1
            if(first) {
                started.countDown()
                check(release.await(5,TimeUnit.SECONDS))
            }
            try {Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((frame("""{"content":"${if(first)"迟到的旧回复" else "新的回复"}"}""")+frame("{}","stop")+"data: [DONE]\n\n")
                    .toResponseBody("text/event-stream".toMediaType())).build()
            } finally {if(first)finished.countDown()}
        }.build()
        val client=AiClient(http)
        val updates=java.util.Collections.synchronizedList(mutableListOf<String>())
        val first=async(Dispatchers.Default) {client.agentChat(ModelConfig(),"local-test",listOf(ChatMessage(role="user",text="第一条")),emptyList(),emptyList(),onText={updates.add(it)})}
        try {
            assertTrue(started.await(5,TimeUnit.SECONDS))
            first.cancelAndJoin();release.countDown()
            assertTrue(finished.await(5,TimeUnit.SECONDS))
            val next=client.agentChat(ModelConfig(),"local-test",listOf(ChatMessage(role="user",text="下一条")),emptyList(),emptyList(),onText={updates.add(it)})
            assertTrue(first.isCancelled)
            assertFalse(updates.any {it.contains("迟到")})
            assertEquals("新的回复",next.text);assertEquals("新的回复",updates.last())
        } finally {release.countDown();first.cancelAndJoin();http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()}
    }
    private fun frame(delta: String,finish: String?=null)="data: {\"choices\":[{\"delta\":$delta,\"finish_reason\":${finish?.let {"\"$it\""}?:"null"}}]}\n\n"
    @Test fun toolChunksRetainIdentityAcrossNullPlaceholders() {
        val first=frame("""{"tool_calls":[{"index":0,"id":"a","type":"function","function":{"name":"query_meals","arguments":"{"}}]}""")
        val second=frame("""{"tool_calls":[{"index":0,"id":null,"type":null,"function":{"name":null,"arguments":"}"}}]}""")
        val result=readChatStream(Buffer().writeUtf8(first+second+frame("{}","tool_calls")+"data: [DONE]\n\n")){}
        val call=result.getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("a",call.getString("id"));assertEquals("{}",call.getJSONObject("function").getString("arguments"))
    }
    @Test fun ambiguousToolChunkMetadataIsRejected() {
        val ordinary="""{"index":0,"id":"a","type":"function","function":{"name":"query_meals","arguments":"{}"}}"""
        val end=frame("{}","tool_calls")+"data: [DONE]\n\n"
        for(bad in listOf(ordinary.replace("\"index\":0","\"index\":0.5"),ordinary.replace("\"index\":0","\"index\":\"0\""),ordinary.replace("\"type\":\"function\"","\"type\":\"custom\""))) {
            assertTrue("Invalid chunk metadata accepted: $bad",runCatching {readChatStream(Buffer().writeUtf8(frame("{\"tool_calls\":[$bad]}")+end)) {}}.isFailure)
        }
        val changedId=frame("{\"tool_calls\":[$ordinary]}")+frame("""{"tool_calls":[{"index":0,"id":"b"}]}""")+end
        assertTrue("A call ID cannot change within one tool index",runCatching {readChatStream(Buffer().writeUtf8(changedId)) {}}.isFailure)
    }
    @Test fun invalidChunkMetadataUsesTheFormatRepairError()=runBlocking {
        val payload=frame("""{"tool_calls":[{"index":"0"}]}""")+frame("{}","tool_calls")+"data: [DONE]\n\n"
        val http=OkHttpClient.Builder().addInterceptor {chain->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("local fixture").body(payload.toResponseBody("text/event-stream".toMediaType())).build()}.build()
        try {
            val error=runCatching {ChatTransport(http).message(ModelConfig(),"synthetic","test",org.json.JSONArray(),onText={})}.exceptionOrNull()
            assertTrue("Malformed chunk must enter bounded format repair: $error",error is ModelResponseFormatException)
        } finally {http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()}
    }
    @Test fun contentArrivesBeforeCompletionAndToolArgumentsReassemble() {
        val frames=frame("""{"content":"你好"}""")+frame("""{"content":"，吃了么"}""")+frame("{}","stop")+"data: [DONE]\n\n"
        val input=Buffer().writeUtf8(frames);val updates=mutableListOf<String>()
        val result=readChatStream(input){updates.add(it);if(updates.size==1)assertTrue(input.size>0)}
        assertEquals("你好",updates.first());assertEquals("你好，吃了么",updates.last());assertEquals(updates.last(),result.getString("content"))
        val tools=frame("""{"tool_calls":[{"index":0,"id":"a","function":{"name":"query_progress","arguments":"{\"from\":"}}]}""")+
            frame("""{"tool_calls":[{"index":0,"function":{"arguments":"\"2026-09-20\",\"to\":\"2026-09-26\"}"}}]}""")+frame("{}","tool_calls")+"data: [DONE]\n\n"
        val call=readChatStream(Buffer().writeUtf8(tools)){}.getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("a",call.getString("id"));assertEquals("2026-09-26",JSONObject(call.getJSONObject("function").getString("arguments")).getString("to"))
    }
    @Test fun incompleteAndLengthLimitedStreamsNeverReturnExecutableTools() {
        for(raw in listOf(frame("""{"content":"一半"}"""),frame("{}","length")+"data: [DONE]\n\n",frame("{}","stop"))) {
            assertTrue(runCatching {readChatStream(Buffer().writeUtf8(raw)) {}}.isFailure)
        }
        val duplicate=frame("""{"tool_calls":[{"index":0,"id":"same","function":{"name":"query_meals","arguments":"{}"}},{"index":1,"id":"same","function":{"name":"query_meals","arguments":"{}"}}]}""")+frame("{}","tool_calls")+"data: [DONE]\n\n"
        assertTrue(runCatching {readChatStream(Buffer().writeUtf8(duplicate)) {}}.isFailure)
        assertTrue(runCatching {readChatStream(Buffer().writeUtf8(frame("{}","tool_calls")+"data: [DONE]\n\n")) {}}.isFailure)
    }
    @Test fun agentRequestsRealSseAndExposesPartialText()=runBlocking {
        var sent: JSONObject?=null
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val b=Buffer();chain.request().body!!.writeTo(b);sent=JSONObject(b.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((frame("""{"content":"先喝口水。"}""")+frame("{}","stop")+"data: [DONE]\n\n").toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        val updates=mutableListOf<String>()
        val reply=AiClient(http).agentChat(ModelConfig(),"test",listOf(ChatMessage(role="user",text="刚运动完")),emptyList(),emptyList(),onText={updates.add(it)})
        assertTrue(sent!!.getBoolean("stream"));assertTrue(updates.contains("先喝口水。"));assertEquals("先喝口水。",reply.text)
    }
}
