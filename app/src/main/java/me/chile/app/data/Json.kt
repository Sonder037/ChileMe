package me.chile.app.data

import me.chile.app.domain.*
import org.json.JSONArray
import org.json.JSONObject

fun parseOptions(array: JSONArray): List<String> {
    require(array.length() in 2..4){"请提供2至4个选项"}
    val options=(0 until array.length()).map { i->
        require(array.get(i) is String){"选项必须是文字"}
        array.getString(i).trim().also {require(it.isNotEmpty() && it.length<=40){"选项需为1至40字"}}
    }
    require(options.distinct().size==options.size){"选项不能重复"}
    return options
}
fun storedOptions(raw: String?): List<String> = if(raw==null)emptyList() else runCatching {parseOptions(JSONArray(raw))}.getOrDefault(emptyList())

fun Profile.json() = JSONObject().put("sex",sex).put("age",age).put("height",height).put("weight",weight).put("date",date).put("activity",activity?:JSONObject.NULL)
fun profileJson(s: String) = JSONObject(s).let { Profile(it.getString("sex"),it.getInt("age"),it.getDouble("height"),it.getDouble("weight"),it.getString("date"),it.opt("activity") as? String) }
fun FoodItem.json() = JSONObject().put("name",name).put("grams",grams ?: JSONObject.NULL).put("kcal",kcal).put("category",category).put("quantity",quantity ?: JSONObject.NULL).put("unit",unit).put("nutrients",nutrients.json())
fun Meal.json() = JSONObject().put("id",id).put("date",date).put("title",title).put("items",JSONArray(items.map { it.json() })).put("photo",photo ?: JSONObject.NULL).put("source",source).put("time",time ?: JSONObject.NULL).put("replacesId",replacesId ?: JSONObject.NULL).put("replacesDraftId",replacesDraftId ?: JSONObject.NULL).put("beforeItems",beforeItems?.let {JSONArray(it.map {f->f.json()})} ?: JSONObject.NULL)
fun mealJson(s: String): Meal = JSONObject(s).let { j ->
    val a=j.getJSONArray("items")
    val items=(0 until a.length()).map { i -> a.getJSONObject(i).let { FoodItem(it.getString("name"),if(it.isNull("grams")) null else it.getDouble("grams"),it.getInt("kcal"),normalizeCategory(it.getString("name"),it.optString("category","其他")).takeIf {c->c in foodCategories}?:"其他",if(it.isNull("quantity"))null else it.getDouble("quantity"),it.optString("unit","g"),parseNutrients(it)) } }
    Meal(j.getString("id"),j.getString("date"),j.getString("title"),items,j.optString("photo").takeUnless { it == "null" || it.isBlank() },j.getString("source"),j.optString("time").takeUnless {it.isBlank() || it=="null"},j.optString("replacesId").takeUnless {it.isBlank() || it=="null"},j.optString("replacesDraftId").takeUnless {it.isBlank() || it=="null"},if(j.isNull("beforeItems"))null else parseItems(JSONObject().put("items",j.getJSONArray("beforeItems"))))
}
fun parseItems(j: JSONObject): List<FoodItem> {
    val a = j.getJSONArray("items")
    require(a.length() in 1..30) { "识别项目数量不正确，请手动记录或重试" }
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        fun numeric(value: Any?) = value is Number || (value is String && value.toDoubleOrNull()!=null)
        require(o.opt("name") is String && numeric(o.opt("kcal")) && (o.isNull("grams") || numeric(o.opt("grams")))) { "食物名称、热量与份量格式不正确" }
        val kcal = o.getDouble("kcal")
        require(kcal.isFinite() && kcal in 0.0..10000.0 && kcal % 1 == 0.0) { "热量格式不正确" }
        val name=o.getString("name").trim()
        require(o.isNull("category") || o.opt("category") is String){"食品分类必须是文字"}
        val category=normalizeCategory(name,if(o.isNull("category"))"其他" else o.getString("category").trim())
        require(category in foodCategories){"食品分类无效，请使用约定分类"}
        val quantity=if(o.isNull("quantity"))null else {
            require(numeric(o.opt("quantity"))){"份量必须是数值，单位请单独填写"}
            o.getDouble("quantity")
        }
        val unit=if(o.isNull("unit")) {require(quantity==null){"请补充份量单位"};"g"} else {
            require(o.opt("unit") is String){"份量单位必须是文字"}
            when(val raw=o.getString("unit").trim().lowercase()) {"克"->"g";"毫升"->"ml";else->raw}
        }
        val grams=if(o.isNull("grams"))null else o.getDouble("grams")
        require(grams==null || (unit=="g" && (quantity==null || quantity==grams))){"份量字段冲突，请只使用quantity和unit；毫升不能直接当作克"}
        FoodItem(name,grams,kcal.toInt(),category,quantity,unit,parseNutrients(o)).also {require(it.nutrients.valid(it.kcal)){"nutrients：营养素供能明显高于kcal，请核对整份与每100克口径"}}.also {require(it.valid()){"识别结果包含无效食物数据"}}

    }.also { require(it.sumOf { f -> f.kcal } <= 30000) { "识别总热量异常，请手动核对" } }
}
fun ModelConfig.json() = JSONObject().put("baseUrl",baseUrl).put("chatModel",chatModel).put("visionModel",visionModel).put("offline",offline)
fun configJson(s: String) = JSONObject(s).let { ModelConfig(it.getString("baseUrl"),it.getString("chatModel"),it.getString("visionModel"),it.getBoolean("offline")) }


// Guard JSON syntax before Android's lenient parser can coerce octal values or overwrite keys.
private val jsonLiteral=Regex("""(?:null|true|false|-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)""")
fun checkedJsonObject(raw: String): JSONObject {
    require(raw.length<=1_048_576) { "模型响应过大" }
    var quoted=false;var escaped=false;var start=0;var keyColon=-1;var previous=' '
    val fields=mutableListOf<MutableSet<String>?>()
    val literal=StringBuilder()
    fun finishLiteral() {
        if(literal.isNotEmpty()) {
            require(jsonLiteral.matches(literal)){"JSON数值或文字格式无效"}
            literal.setLength(0);previous='v'
        }
    }
    fun valueStart() {require(previous in " {[:,"){"JSON值之间缺少分隔符"}}
    for((index,c) in raw.withIndex()) {
        if(quoted) {
            require(c.code>=32){"JSON文字中包含未转义的控制字符"}
            if(escaped) {
                require(c in "\"\\/bfnrtu"){"JSON转义无效"}
                if(c=='u')require(index+4<raw.length && raw.substring(index+1,index+5).all {it in "0123456789abcdefABCDEF"}){"JSON Unicode转义无效"}
                escaped=false
            } else if(c=='\\')escaped=true else if(c=='"') {
                quoted=false;previous='"'
                var next=index+1
                while(next<raw.length && raw[next] in " \t\r\n")next++
                if(next<raw.length && raw[next]==':') {
                    val key=org.json.JSONTokener(raw.substring(start,index+1)).nextValue() as String
                    require(fields.lastOrNull()?.add(key)==true){"模型响应含重复或无效字段：${key.take(80)}"}
                    keyColon=next
                }
            }
        } else {
            if(c in "{}[],:\" \t\r\n")finishLiteral()
            when(c) {
                '"'->{valueStart();quoted=true;start=index}
                '{','['->{valueStart();fields.add(if(c=='{')mutableSetOf() else null);require(fields.size<=32){"模型响应结构过于复杂"};previous=c}
                '}',']'->{
                    require(fields.isNotEmpty() && (fields.last()!=null)==(c=='}') && previous !in ",:"){"模型响应格式不完整"}
                    fields.removeAt(fields.lastIndex);previous=c
                }
                ':'->{require(index==keyColon){"JSON字段名称必须使用双引号"};previous=c}
                ','->{require(previous in "\"v}]"){"JSON分隔符无效"};previous=c}
                ' ','\t','\r','\n'->Unit
                else->{if(literal.isEmpty())valueStart();literal.append(c)}
            }
        }
    }
    finishLiteral()
    require(!quoted && fields.isEmpty()){"模型响应格式不完整"}
    val tokens=org.json.JSONTokener(raw)
    val result=tokens.nextValue()
    require(result is JSONObject && tokens.nextClean()=='\u0000'){"模型响应必须是单个JSON对象"}
    return result
}

fun Nutrients.json() = JSONObject().put("protein_g",proteinG?:JSONObject.NULL).put("carbs_g",carbsG?:JSONObject.NULL).put("fat_g",fatG?:JSONObject.NULL)
fun parseNutrients(food: JSONObject): Nutrients {
    if(food.isNull("nutrients"))return Nutrients()
    val n=food.getJSONObject("nutrients")
    require(n.keys().asSequence().all {it in listOf("protein_g","carbs_g","fat_g")}){"营养素字段无效"}
    fun grams(key: String): Double? {
        if(n.isNull(key))return null
        require(n.opt(key) is Number){"$key 必须为克数或null，不能包含单位"}
        return n.getDouble(key).also {require(it.isFinite() && it in 0.0..2500.0){"$key 超出范围"}}
    }
    return Nutrients(grams("protein_g"),grams("carbs_g"),grams("fat_g"))
}
fun NutritionDay.json(): JSONObject = JSONObject().put("reference_energy_kcal",reference?.energyKcal?:JSONObject.NULL)
    .put("reference_basis","营养比例依据WS/T 578.1—2017第5.1条：蛋白质10%–15%、碳水50%–65%、脂肪20%–30%能量，按4/4/9换算克数。总能量优先使用静息×所选活动系数，含日常活动与运动；旧资料未选活动时保留NASEM2023成人非活跃EER参考。不是减脂目标。reference_g为参考范围上沿，不是必须吃满的目标或医学安全上限。仅19至78岁一般成人参考，不适用于孕哺期或特殊疾病。碳水不等于糖；《中国居民膳食指南（2022）》建议添加糖每天不超过50g、最好25g以下，烹调油25–30g不等于总脂肪；本工具没有单独统计添加糖或烹调油摄入。")
    .put("reference_sources",JSONArray(listOf("https://www.nhc.gov.cn/wjw/yingyang/201710/fdade20feb8144ba921b412944ffb779.shtml","https://dg.cnsoc.org/article/04/ApX3_ozGTmSoqQaFFh5z_Q.html")))
    .put("parts",JSONArray(parts.map {JSONObject().put("name",it.name).put("grams",it.grams?:JSONObject.NULL).put("reference_g",it.target?:JSONObject.NULL)
        .put("reference_min_g",it.minimum?:JSONObject.NULL).put("reference_max_g",it.target?:JSONObject.NULL)
        .put("known_items",it.knownItems).put("total_items",it.totalItems).put("incomplete",it.incomplete)}))
