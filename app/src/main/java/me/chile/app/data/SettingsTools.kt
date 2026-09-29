package me.chile.app.data

import me.chile.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

val settingsToolNames=setOf("query_settings","update_profile","set_daily_activity","edit_exercise","delete_record","update_settings","query_memories","edit_memory")
fun settingsToolDefinitions(): JSONArray {
    val all=JSONArray(checkNotNull(ModelConfig::class.java.getResourceAsStream("/tools.json")).bufferedReader(Charsets.UTF_8).use {it.readText()})
    return JSONArray((0 until all.length()).map {all.getJSONObject(it)}.filter {it.getJSONObject("function").getString("name") in settingsToolNames})
}
data class EditableSettings(val config: ModelConfig=ModelConfig(),val memoryEnabled: Boolean=true,val includeMemory: Boolean=false)
data class PendingChange(val kind: String,val target: String,val before: String?,val after: String?,val summary: String) {
    fun json()=JSONObject().put("kind",kind).put("target",target).put("before",before?:JSONObject.NULL).put("after",after?:JSONObject.NULL).put("summary",summary)
}
fun changeJson(raw: String)=JSONObject(raw).let {PendingChange(it.getString("kind"),it.getString("target"),it.opt("before") as? String,it.opt("after") as? String,it.getString("summary"))}
fun EditableSettings.json()=JSONObject().put("config",config.json()).put("memory_enabled",memoryEnabled).put("include_memory",includeMemory)
fun editableSettingsJson(raw: String)=JSONObject(raw).let {EditableSettings(configJson(it.getJSONObject("config").toString()),it.getBoolean("memory_enabled"),it.getBoolean("include_memory"))}

/** Read-only tools prepare one immutable change. Only the local confirmation button commits it. */
class SettingsTools(private val profiles: List<Profile>,private val overrides: Map<String,String>,private val settings: EditableSettings,
    private val meals: List<Meal>,private val exercises: List<Exercise>,private val now: LocalDateTime,private val localMemories: List<Memory> = emptyList()) {
    var proposal: PendingChange?=null;private set
    private var settingsRead=false
    private val readMemories=mutableSetOf<String>()
    fun execute(name: String,raw: String): JSONObject=try {
        val a=validateToolArguments(settingsToolDefinitions(),name,raw)
        val current=profileOn(profiles,now.toLocalDate().toString())
        fun date(value: String): String {val d=LocalDate.parse(value);require(d.year>=1900 && !d.isAfter(now.toLocalDate())){"日期需在1900年至今天之间"};return d.toString()}
        if(name=="query_memories") {
            val keyword=a.getString("keyword").trim();require(keyword.isNotBlank()){"请输入记忆关键词"}
            val found=localMemories.filter {it.text.contains(keyword,ignoreCase=true)}
            val rows=found.take(20);readMemories.addAll(rows.map {it.key})
            JSONObject().put("status","ok").put("memories",JSONArray(rows.map {JSONObject().put("key",it.key).put("text",it.text)})).put("truncated",found.size>20)
        }else if(name=="query_settings") {
            settingsRead=true
            JSONObject().put("status","ok").put("profile",current?.json()?:JSONObject.NULL).put("settings",settings.json())
                .put("daily_activity",JSONObject(overrides)).put("activity_levels",JSONArray(activityLevels.map {JSONObject().put("id",it.id).put("label",it.label).put("factor",it.factor).put("description",it.detail)}))
        } else {
            require(proposal==null){"本轮已有修改，请先确认"}
            if(name in setOf("update_profile","set_daily_activity","update_settings"))require(settingsRead){"请先query_settings读取当前值"}
            val change=when(name) {
                "edit_memory"->{
                    val key=a.getString("key");require(key in readMemories){"请先query_memories定位原记忆"}
                    val old=localMemories.firstOrNull {it.key==key}?:error("记忆不存在")
                    val text=(a.opt("text") as? String)?.trim()
                    require(text==null || text.isNotBlank()){"记忆内容不能为空"}
                    PendingChange("memory",key,old.text,text,if(text==null)"忘记：${old.text}" else "原记忆：${old.text}\n改为：$text")
                }
                "update_profile"->{
                    require(a.length()>0){"没有指定修改内容"}
                    val old=current?:error("请先在身体资料中完成初次填写")
                    val p=old.copy(sex=a.optString("sex",old.sex),age=if(a.has("age"))a.getInt("age") else old.age,
                        height=if(a.has("height"))a.getDouble("height") else old.height,weight=if(a.has("weight"))a.getDouble("weight") else old.weight,
                        activity=if(a.has("activity"))a.getString("activity") else old.activity,date=now.toLocalDate().toString())
                    require(p.valid()){"身体资料无效"}
                    val lines=buildList {
                        if(p.sex!=old.sex)add("性别：${if(p.sex=="male")"男" else "女"}")
                        if(p.age!=old.age)add("年龄：${old.age} → ${p.age} 岁")
                        if(p.height!=old.height)add("身高：${old.height} → ${p.height} cm")
                        if(p.weight!=old.weight)add("体重：${old.weight} → ${p.weight} kg")
                        if(p.activity!=old.activity)add("默认活动：${activityLevels.first {it.id==p.activity}.label} · 从今天起")
                    };require(lines.isNotEmpty()){"数据没有变化"}
                    PendingChange("profile",p.date,old.json().toString(),p.json().toString(),lines.joinToString("\n"))
                }
                "set_daily_activity"->{
                    val day=date(a.getString("date"));require(profileOn(profiles,day)!=null){"该日期没有身体资料"}
                    val id=a.getString("activity");val after=id.takeUnless {it=="default"}
                    require(overrides[day]!=after){"当天活动没有变化"}
                    PendingChange("activity",day,overrides[day],after,"$day · ${activityLevels.firstOrNull {it.id==after}?.label?:"恢复默认活动"}")
                }
                "edit_exercise"->{
                    require(a.length()>1){"没有指定修改内容"}
                    val old=exercises.firstOrNull {it.id==a.getString("exercise_id")}?:error("运动记录不存在，请重新查询")
                    val day=date(a.optString("date",old.date));val time=a.optString("time",old.time);LocalTime.parse(time)
                    val title=a.optString("name",old.name);val minutes=if(a.has("minutes"))a.getInt("minutes") else old.minutes
                    val recalc=title!=old.name || minutes!=old.minutes || day!=old.date
                    val kcal=if(a.has("active_kcal"))a.getInt("active_kcal") else if(recalc)estimateActiveKcal(title,minutes,profileOn(profiles,day)?:error("缺少该日身体资料，请提供活动千卡")) else old.activeKcal
                    val updated=old.copy(date=day,time=time,name=title,minutes=minutes,activeKcal=kcal,source=if(a.has("active_kcal"))"用户提供 · 活动千卡" else if(recalc)"MET 估算 · 净活动消耗" else old.source)
                    require(updated.valid()){"运动数据无效"}
                    PendingChange("exercise",old.id,old.json().toString(),updated.json().toString(),"$day $time · $title\n${old.minutes} → $minutes 分钟 · ${old.activeKcal} → $kcal kcal")
                }
                "delete_record"->{
                    val id=a.getString("record_id")
                    if(a.getString("kind")=="meal") {
                        val old=meals.firstOrNull {it.id==id}?:error("餐饮记录不存在，请查询已确认记录")
                        PendingChange("meal",id,old.json().toString(),null,"删除 ${old.date} ${old.title} · ${old.kcal} kcal\n${old.items.joinToString("、"){it.name}}")
                    } else {
                        val old=exercises.firstOrNull {it.id==id}?:error("运动记录不存在")
                        PendingChange("exercise",id,old.json().toString(),null,"删除 ${old.date} ${old.name} · ${old.minutes} 分钟")
                    }
                }
                "update_settings"->{
                    require(a.length()>0){"没有指定修改内容"}
                    val c=settings.config.copy(baseUrl=a.optString("base_url",settings.config.baseUrl).trim(),chatModel=a.optString("chat_model",settings.config.chatModel).trim(),visionModel=a.optString("vision_model",settings.config.visionModel).trim())
                    endpoint(c.baseUrl);require(c.chatModel.isNotBlank() && c.visionModel.isNotBlank()){"模型名称不能为空"}
                    val next=settings.copy(config=c,memoryEnabled=if(a.has("memory_enabled"))a.getBoolean("memory_enabled") else settings.memoryEnabled,includeMemory=if(a.has("include_memory"))a.getBoolean("include_memory") else settings.includeMemory)
                    require(next!=settings){"设置没有变化"}
                    val labels=mapOf("base_url" to "服务地址","chat_model" to "聊天模型","vision_model" to "视觉模型","memory_enabled" to "本地记忆","include_memory" to "附带记忆")
                    val summary=a.keys().asSequence().joinToString("\n") {"${labels[it]}：${when(val value=a.get(it)){true->"开启";false->"关闭";else->value}}"}+
                        if(c.baseUrl.trimEnd('/')!=settings.config.baseUrl.trimEnd('/'))"\n更换地址后清除旧密钥，请到连接 AI 填写新密钥" else ""
                    PendingChange("settings","settings",settings.json().toString(),next.json().toString(),summary)
                }
                else->error("未知修改工具")
            }
            proposal=change
            JSONObject().put("status","pending_change").put("change",change.json()).put("message","等待用户点击确认，尚未保存")
        }
    }catch(e: Exception){JSONObject().put("status","error").put("message",e.message?.take(200)?:"修改参数无效")}
}

fun LocalStore.activityOverrides(): Map<String,String> = readableDatabase.rawQuery("SELECT key,value FROM kv WHERE key LIKE 'activity:%'",null).use {c->buildMap {while(c.moveToNext())put(c.getString(0).removePrefix("activity:"),c.getString(1))}}
fun LocalStore.setDailyActivity(date: String,level: String?) {
    requireRecordedDate(date);require(profileOn(profiles(),date)!=null){"该日期没有身体资料"}
    require(level==null || activityLevels.any {it.id==level})
    if(level==null)writableDatabase.delete("kv","key=?",arrayOf("activity:$date")) else put("activity:$date",level)
}
fun LocalStore.editableSettings()=EditableSettings(get("config")?.let(::configJson)?:ModelConfig(),get("memory")!="false",get("includeMemory")=="true")
fun LocalStore.pendingChanges(): Map<String,PendingChange> = readableDatabase.rawQuery("SELECT key,value FROM kv WHERE key LIKE 'change:%'",null).use {c->buildMap {while(c.moveToNext())put(c.getString(0).removePrefix("change:"),changeJson(c.getString(1)))}}
fun LocalStore.applyChange(change: PendingChange) {
    // Compare typed values rather than JSON key order. Called inside the confirmation transaction.
    fun unchanged(ok: Boolean){require(ok){"原数据已变化，请重新提出修改，避免覆盖新内容"}}
    when(change.kind) {
        "memory"->{val old=memories().firstOrNull {it.key==change.target};unchanged(old!=null && old.text==change.before)
            if(change.after==null)forget(change.target) else {require(change.after.isNotBlank() && change.after.length<=1000);saveMemory(old!!.copy(text=change.after))}}
        "profile"->{unchanged(profileOn(profiles(),LocalDate.now().toString())==change.before?.let(::profileJson));val p=profileJson(checkNotNull(change.after));requireRecordedDate(p.date);saveProfile(p)}
        "activity"->{unchanged(activityOverrides()[change.target]==change.before);setDailyActivity(change.target,change.after)}
        "exercise"->{unchanged(exercises().firstOrNull {it.id==change.target}==change.before?.let(::exerciseJson));if(change.after==null)deleteExercise(change.target) else saveExercise(exerciseJson(change.after))}
        "meal"->{unchanged(meals().firstOrNull {it.id==change.target}==change.before?.let(::mealJson));require(change.after==null);deleteMeal(change.target)}
        "settings"->{unchanged(editableSettings()==change.before?.let(::editableSettingsJson));val next=editableSettingsJson(checkNotNull(change.after));endpoint(next.config.baseUrl);put("config",next.config.json().toString());put("memory",next.memoryEnabled.toString());put("includeMemory",next.includeMemory.toString())}
        else->error("未知修改类型")
    }
}
