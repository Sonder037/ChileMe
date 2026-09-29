package me.chile.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Validate the actual advertised contract before dispatching a model's call. No coercion. */
fun validateToolArguments(definitions: JSONArray,name: String,raw: String): JSONObject {
    val function=(0 until definitions.length()).map {definitions.getJSONObject(it).getJSONObject("function")}
        .firstOrNull {it.getString("name")==name}?:error("未知工具：$name")
    val args=checkedJsonObject(raw)
    validateSchema(args,function.getJSONObject("parameters"),name)
    return args
}

private fun validateSchema(value: Any,schema: JSONObject,path: String) {
    val types=when(val t=schema.opt("type")) {is JSONArray->(0 until t.length()).map {t.getString(it)};is String->listOf(t);else->emptyList()}
    val matches=types.any {when(it) {
        "null"->value===JSONObject.NULL
        "object"->value is JSONObject
        "array"->value is JSONArray
        "string"->value is String
        "boolean"->value is Boolean
        "number"->value is Number && value.toDouble().isFinite()
        "integer"->value is Number && value.toDouble().isFinite() && value.toDouble()%1==0.0
        else->false
    }}
    require(matches){"$path 类型必须为 ${types.joinToString("/")}"}
    schema.optJSONArray("enum")?.let {allowed->require((0 until allowed.length()).any {allowed.get(it)==value}){"$path 不在允许选项中"}}
    when(value) {
        is JSONObject->{
            val fields=schema.getJSONObject("properties")
            val required=schema.optJSONArray("required")?:JSONArray()
            for(i in 0 until required.length())require(value.has(required.getString(i))){"$path 缺少 ${required.getString(i)}"}
            for(key in value.keys()) {
                require(fields.has(key) || schema.optBoolean("additionalProperties",true)){"$path.$key 是未知字段"}
                fields.optJSONObject(key)?.let {validateSchema(value.get(key),it,"$path.$key")}
            }
        }
        is JSONArray->{
            require(value.length()>=schema.optInt("minItems",0) && value.length()<=schema.optInt("maxItems",1000)){"$path 数量超出范围"}
            if(schema.optBoolean("uniqueItems"))require((0 until value.length()).map {value.get(it).toString()}.distinct().size==value.length()){"$path 不能重复"}
            for(i in 0 until value.length())validateSchema(value.get(i),schema.getJSONObject("items"),"$path[$i]")
        }
        is Number->require(value.toDouble()>=schema.optDouble("minimum",-Double.MAX_VALUE) && value.toDouble()<=schema.optDouble("maximum",Double.MAX_VALUE)){"$path 数值超出范围"}
        is String->require(value.length<=schema.optInt("maxLength",50000)){"$path 文字过长"}
    }
}

/** Local result envelopes are checked as well, so a broken tool cannot masquerade as success. */
fun validateToolResult(name: String,result: JSONObject) {
    when(result.optString("status")) {
        "error"->require(result.opt("message") is String)
        "pending_change"->require(name in settingsToolNames && result.opt("change") is JSONObject)
        "awaiting_answer"->require(name=="ask_user")
        "ready_to_save"->require(name=="record_exercise" && result.opt("exercise") is JSONObject)
        "pending_confirmation"->require(name in listOf("record_meal","estimate_meal","edit_meal"))
        "ok"->when(name) {
            "query_memories"->require(result.opt("memories") is JSONArray && result.opt("truncated") is Boolean)
            "query_settings"->require(result.opt("settings") is JSONObject && result.opt("daily_activity") is JSONObject)
            "query_meals"->require(result.opt("meals") is JSONArray && result.opt("truncated") is Boolean)
            "query_exercises"->require(result.opt("exercises") is JSONArray)
            "query_progress"->require(result.opt("days") is JSONArray && result.opt("nutrition") is JSONArray)
            else->error("未知查询结果")
        }
        else->error("工具返回状态无效")
    }
}
