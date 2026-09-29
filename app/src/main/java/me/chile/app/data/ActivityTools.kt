package me.chile.app.data

import me.chile.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

fun activityToolDefinitions(): JSONArray {
    fun field(type: String,description: String)=JSONObject().put("type",type).put("description",description)
    fun tool(name: String,description: String,fields: JSONObject,required: List<String>)=JSONObject().put("type","function").put("function",JSONObject().put("name",name).put("description",description)
        .put("parameters",JSONObject().put("type","object").put("properties",fields).put("required",JSONArray(required)).put("additionalProperties",false)))
    fun dates()=JSONObject().put("from",field("string","开始日期 YYYY-MM-DD")).put("to",field("string","结束日期 YYYY-MM-DD"))
    return JSONArray().put(tool("record_exercise","用户明确已完成运动或要求记录时使用；计划或建议禁止记录。本机自动保存，聊天卡片可编辑删除。缺信息先问。",JSONObject()
        .put("name",field("string","实际运动名称；估算支持散步、快走、慢跑，不能把未知运动映射为这些类型"))
        .put("minutes",field("integer","时长，1至1440分钟"))
        .put("active_kcal",field("integer","可选：用户明确提供的活动千卡，0至10000，不含静息；不允许模型自编数值。没有则省略，由本机MET估算"))
        .put("date",field("string","运动日期 YYYY-MM-DD，省略今天"))
        .put("time",field("string","运动时间 HH:mm，省略当前时间")),listOf("name","minutes")))
        .put(tool("query_exercises","查询1至31天本地运动记录，最多50条；不猜已记录数据",dates(),listOf("from","to")))
        .put(tool("query_progress","汇总1至31天全部已确认餐饮、运动和当时身体数据；七天总结及营养素查询优先使用，包含每日蛋白质、碳水、脂肪的已知量、缺项数和参考量，以及缺失天数和本机算好的差值、脂肪能量当量",dates(),listOf("from","to")))
        .apply {val extra=settingsToolDefinitions();for(i in 0 until extra.length())put(extra.getJSONObject(i))}
}

class ActivityTools(private val meals: List<Meal>,private val exercises: List<Exercise>,private val profiles: List<Profile>,
    private val messageId: String,private val now: LocalDateTime,private val overrides: Map<String,String> = emptyMap()) {
    var proposal: Exercise?=null;private set
    fun execute(name: String,raw: String): JSONObject=try {
        require(raw.length<=10000){"参数过长"};val a=checkedJsonObject(raw)
        fun text(key: String,fallback: String?=null): String=if(!a.has(key))fallback?:error("缺少$key") else {
            require(a.opt(key) is String){"$key 必须是文字"};a.getString(key).trim()
        }
        fun integer(key: String): Int {val n=a.opt(key);require(n is Number){"$key 必须是整数"};val v=n.toDouble();require(v.isFinite() && v%1==0.0 && v in 0.0..10000.0){"$key 超出范围"};return v.toInt()}
        if(name=="record_exercise") {
            require(proposal==null){"本轮已有运动记录"}
            require(exercises.none {it.messageId==messageId}){"这条消息已经记录过运动，请在原卡片编辑"}
            val date=LocalDate.parse(text("date",now.toLocalDate().toString()))
            require(!date.isAfter(now.toLocalDate()) && date.year>=1900){"不能记录尚未发生的运动"}
            val time=text("time",now.toLocalTime().withSecond(0).withNano(0).toString());LocalTime.parse(time)
            val activity=text("name");val minutes=integer("minutes");require(minutes in 1..1440){"时长需为1至1440分钟"}
            val provided=a.has("active_kcal") && !a.isNull("active_kcal")
            val kcal=if(provided)integer("active_kcal") else estimateActiveKcal(activity,minutes,profileOn(profiles,date.toString())?:error("请先补充身体资料或提供设备活动千卡"))
            val exercise=Exercise(id="exercise:$messageId",date=date.toString(),time=time,name=activity,minutes=minutes,activeKcal=kcal,
                source=if(provided)"用户提供 · 活动千卡" else "MET 估算 · 净活动消耗",messageId=messageId)
            require(exercise.valid()){"请检查运动名称、时长与热量"};proposal=exercise
            JSONObject().put("status","ready_to_save").put("exercise",exercise.json())
        } else {
            val from=LocalDate.parse(text("from"));val to=LocalDate.parse(text("to"))
            require(!to.isAfter(now.toLocalDate())){"不能查询未来进度"}
            require(java.time.temporal.ChronoUnit.DAYS.between(from,to) in 0..30){"查询范围需为1至31天"}
            when(name) {
                "query_exercises"->{val rows=exercises.filter {it.date>=from.toString() && it.date<=to.toString()};JSONObject().put("status","ok").put("truncated",rows.size>50).put("exercises",JSONArray(rows.take(50).map {it.json()}))}
                "query_progress"->{
                    val p=energyProgress(meals,exercises,profiles,from,to,overrides)
                    JSONObject().put("status","ok").put("from",from).put("to",to).put("requested_days",p.days.size)
                    .put("recorded_days",p.recordedDays).put("comparable_days",p.comparableDays).put("recorded_intake_kcal",p.intake).put("recorded_active_kcal",p.active)
                    .put("balance_kcal",p.balance?:JSONObject.NULL).put("fat_energy_equivalent_kg",p.fatEquivalentKg?:JSONObject.NULL)
                    .put("definition","balance=已记录摄入−全天消耗参考。已选活动等级时total=静息×活动系数，包含日常与运动，不再加已记录运动。未选活动等级的旧资料total为空，沿用静息+已记录运动旧口径，不是全天消耗。脂肪能量当量=-balance/7700，不是实际减掉脂肪。缺失饮食或身体资料不计入差值；已记录不代表全天完整。")
                    .put("nutrition",JSONArray(p.days.map {nutritionDay(meals,profileForDay(profiles,it.date,overrides),it.date).json().put("date",it.date)}))
                    .put("reference_as_of",now.toString())
                    .put("today_daily_activity_reference_kcal",dailyActivityReference(profileForDay(profiles,now.toLocalDate().toString(),overrides),now.toLocalTime())?:JSONObject.NULL)
                    .put("days",JSONArray(p.days.map {JSONObject().put("date",it.date).put("intake",it.intake?:JSONObject.NULL).put("resting",it.resting?:JSONObject.NULL).put("active",it.active).put("total",it.total?:JSONObject.NULL).put("activity_level",profileForDay(profiles,it.date,overrides)?.activity?:JSONObject.NULL).put("balance",it.balance?:JSONObject.NULL)}))
                }
                else->error("未知工具")
            }
        }
    }catch(e: Exception){JSONObject().put("status","error").put("message",if(e is org.json.JSONException)"参数格式错误，请补齐运动信息" else e.message?.take(180)?:"无法执行")}
}
