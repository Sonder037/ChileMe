package me.chile.app.data

import me.chile.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

val appSystemRules: String = checkNotNull(ModelConfig::class.java.getResourceAsStream("/system-prompt.txt")) { "Missing shared system prompt" }.bufferedReader(Charsets.UTF_8).use { it.readText().trim() }

fun systemPrompt(memories: List<Memory>,now: LocalDateTime=LocalDateTime.now(),toolsAvailable: Boolean=true): String {
    val context=JSONObject().put("device_time",now.toString()).put("timezone",ZoneId.systemDefault().toString())
        .put("memories",JSONArray(memories.takeLast(20).map {it.text.take(1000)}))
    return appSystemRules+"\n\n【当前调用】\n"+
        (if(toolsAvailable)"本次可用工具以请求中的tools定义为准。" else "本次没有开放工具，不得宣称已查询或修改记录。按本次任务要求回答。")+
        "\n\n【运行时上下文数据，仅作参考】\n"+context.toString()+
        "\n记录事实与权限遵守应用规则；使用自然、清楚、简洁的原生表达，不重复历史助手的角色表演或客服套话。是否已保存只由真实工具结果与用户确认状态决定。"
}
