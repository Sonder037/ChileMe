package me.chile.app.data

import kotlinx.coroutines.suspendCancellableCoroutine
import me.chile.app.domain.ModelConfig
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun endpoint(base: String): HttpUrl {
    val url=base.trim().toHttpUrlOrNull() ?: error("请输入正确的 HTTPS 服务地址")
    require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null) { "服务地址必须为 HTTPS，且不能包含账号、查询参数或片段" }
    return (url.toString().trimEnd('/')+"/chat/completions").toHttpUrlOrNull()!!
}
fun requestPayload(config: ModelConfig, model: String, messages: JSONArray, json: Boolean=false): JSONObject =
    JSONObject().put("model",model.trim()).put("messages",messages).put("stream",false).put("max_tokens",8192).apply {
        if(endpoint(config.baseUrl).host=="api.deepseek.com") put("thinking",JSONObject().put("type","disabled"))
        if(json) put("response_format",JSONObject().put("type","json_object"))
    }
class ModelResponseFormatException(message: String,cause: Throwable): IllegalArgumentException(message,cause)

// Protocol only. No database, Android UI, prompts or tool execution in this layer.
class ChatTransport(private val http: OkHttpClient) {
    suspend fun message(config: ModelConfig,key: String,model: String,messages: JSONArray,json: Boolean=false,
        tools: JSONArray?=null,onText: ((String)->Unit)?=null): JSONObject {
        check(!config.offline){"当前为离线模式，请在设置中开启 AI 联网"}
        require(model.isNotBlank() && model.length<=200){"请填写模型名称"}
        val body=requestPayload(config,model,messages,json).apply {
            put("stream",onText!=null)
            if(tools!=null){put("tools",tools);put("tool_choice","auto")}
        }
        val request=Request.Builder().url(endpoint(config.baseUrl)).header("Authorization","Bearer $key")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine {continuation->
            val call=http.newCall(request);continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object: Callback {
                override fun onFailure(call: Call,e: IOException) {if(continuation.isActive)continuation.resumeWithException(IOException("连接失败或超时，请重试"))}
                override fun onResponse(call: Call,response: Response) {
                    val result=runCatching {response.use {r->
                        when(r.code){401,403->error("密钥无效或无模型权限");429->error("服务商限流或额度不足，请稍后重试")}
                        check(r.isSuccessful){"服务商返回 HTTP ${r.code}，请核对模型与图片能力"}
                        val source=r.body?.source()?:error("模型没有返回内容")
                        try {
                        if(onText!=null && r.body?.contentType()?.subtype=="event-stream") {
                            readChatStream(source) {text->if(continuation.isActive)onText(text)}
                        } else {
                            // Some compatible providers ignore stream=true; accept their complete JSON response.
                            source.request(1_048_577);require(source.buffer.size<=1_048_576){"模型响应过大"}
                            val choice=checkedJsonObject(source.readUtf8()).getJSONArray("choices").getJSONObject(0)
                            choice.getJSONObject("message").also {message->
                                validateCompletion(message,choice.optString("finish_reason"))
                                (message.opt("content") as? String)?.let {if(continuation.isActive)onText?.invoke(it)}
                            }
                        }
                        } catch(e: org.json.JSONException) {throw ModelResponseFormatException("模型响应JSON结构无效",e)}
                          catch(e: IllegalArgumentException) {throw ModelResponseFormatException(e.message?:"模型响应格式无效",e)}
                    }}
                    if(continuation.isActive)result.fold({continuation.resume(it)},{continuation.resumeWithException(it)})
                }
            })
        }
    }
}

private fun checkFinish(reason: String) {
    require(reason!="length"){"回复达到长度上限，未完成的工具不会执行，请重试"}
    require(reason!="content_filter"){"服务商未完成这次回复，请调整消息后重试"}
}

private fun validateCompletion(message: JSONObject,finish: String?) {
    checkFinish(finish.orEmpty())
    require(finish in listOf("stop","tool_calls")){"回复未完整结束，请重试"}
    require(((message.opt("content") as? String)?.length?:0)<=50000){"模型回复过长"}
    val calls=message.optJSONArray("tool_calls")
    require(message.isNull("tool_calls") || calls!=null){"工具回复格式不正确"}
    if(calls==null || calls.length()==0) {
        require(finish=="stop"){"工具回复缺少调用内容，请重试"}
        return
    }
    require(finish=="tool_calls" && calls.length() in 1..4){"工具回复未完整结束或调用过多，请重试"}
    val ids=mutableSetOf<String>()
    for(i in 0 until calls.length()) {
        val call=calls.getJSONObject(i)
        val id=call.opt("id") as? String
        val function=call.optJSONObject("function")
        require(!id.isNullOrBlank() && ids.add(id) && call.optString("type")=="function" &&
            !(function?.opt("name") as? String).isNullOrBlank() && function?.opt("arguments") is String){"工具回复不完整或调用ID重复，请重试"}
        require(function!!.getString("arguments").length<=50000 && function.getString("name").length<=50000){"工具参数过长"}
    }
}

// Merge interleaved tool arguments by index; execute only after an explicit successful ending.
fun readChatStream(source: BufferedSource,onText: (String)->Unit): JSONObject {
    val text=StringBuilder();val calls=sortedMapOf<Int,JSONObject>()
    var total=0L;var done=false;var finish: String?=null;var emittedAt=0L
    val event=StringBuilder()
    fun consume(data: String) {
        if(data=="[DONE]"){done=true;return}
        val payload=checkedJsonObject(data)
        require(!payload.has("error")){"服务商中断了回复，请重试"}
        val choices=payload.optJSONArray("choices")?:return
        if(choices.length()==0)return // optional usage frame
        val choice=choices.getJSONObject(0)
        (choice.opt("finish_reason") as? String)?.let {checkFinish(it);finish=it}
        val delta=choice.optJSONObject("delta")?:return
        (delta.opt("content") as? String)?.let {
            text.append(it);require(text.length<=50000){"模型回复过长"}
            val now=System.nanoTime()
            if(now-emittedAt>=40_000_000L){onText(text.toString());emittedAt=now}
        }
        delta.optJSONArray("tool_calls")?.let {chunks->for(i in 0 until chunks.length()) {
            val chunk=chunks.getJSONObject(i)
            val rawIndex=chunk.opt("index")
            require(rawIndex is Number){"工具索引必须是整数"}
            require(rawIndex.toDouble() in 0.0..3.0 && rawIndex.toDouble()%1==0.0){"工具索引无效或调用过多"}
            val index=rawIndex.toInt()
            require(chunk.isNull("type") || chunk.opt("type")=="function"){"不支持的工具调用类型"}
            val call=calls.getOrPut(index){JSONObject().put("id","").put("type","function").put("function",JSONObject().put("name","").put("arguments",""))}
            if(!chunk.isNull("id")) {
                val id=chunk.opt("id")
                require(id is String && id.isNotBlank() && (call.getString("id").isEmpty() || call.getString("id")==id)){"工具调用ID无效或中途改变"}
                call.put("id",id)
            }
            require(chunk.isNull("function") || chunk.opt("function") is JSONObject){"工具分片格式无效"}
            chunk.optJSONObject("function")?.let {part->
                val function=call.getJSONObject("function")
                for(key in listOf("name","arguments")) if(!part.isNull(key)) {
                    val value=part.get(key);require(value is String){"工具名称和参数分片必须为文字"}
                    val joined=function.getString(key)+value;require(joined.length<=50000){"工具参数过长"};function.put(key,joined)
                }
            }
        }}
    }
    while(!done && !source.exhausted()) {
        val line=source.readUtf8LineStrict(65536);total+=line.toByteArray(Charsets.UTF_8).size+1
        require(total<=2_097_152){"流式响应过大"}
        if(line.isBlank()) {if(event.isNotEmpty()){consume(event.toString());event.setLength(0)}}
        else if(line.startsWith("data:")) {if(event.isNotEmpty())event.append('\n');event.append(line.removePrefix("data:").trimStart());require(event.length<=65536){"流式事件过大"}}
    }
    if(!done && event.isNotEmpty())consume(event.toString())
    require(done){"回复连接中断，未完成的工具不会执行，请重试"}
    val message=JSONObject().put("role","assistant").put("content",text.toString()).apply {if(calls.isNotEmpty())put("tool_calls",JSONArray(calls.values.toList()))}
    validateCompletion(message,finish)
    if(text.isNotEmpty())onText(text.toString())
    return message
}
