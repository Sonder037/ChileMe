package me.chile.app.data

import me.chile.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

fun mealToolDefinitions(): JSONArray {
    fun string(description: String)=JSONObject().put("type","string").put("description",description)
    fun schema(properties: JSONObject,required: List<String>)=JSONObject().put("type","object").put("properties",properties).put("required",JSONArray(required)).put("additionalProperties",false)
    fun tool(name: String,description: String,parameters: JSONObject)=JSONObject().put("type","function").put("function",JSONObject().put("name",name).put("description",description).put("parameters",parameters))
    fun fields()=JSONObject().put("date",string("用餐日期 YYYY-MM-DD，省略为今天。用户提到昨天等相对日期时必须换算。"))
        .put("time",string("用餐时间 HH:mm；优先用户明确时间，省略为当前设备时间。"))
        .put("meal_type",string("优先用户明确餐次；不明确则省略，由本机按时间推断。可选早餐、午餐、晚餐、夜宵、加餐。"))
        .put("items",JSONObject().put("type","array").put("minItems",1).put("maxItems",30).put("items",schema(JSONObject()
            .put("name",string("食物名；不能编造不认识的食品"))
            .put("quantity",JSONObject().put("type",JSONArray(listOf("number","null"))).put("minimum",0).put("maximum",10000).put("description","实际摄入份量，数值与单位分开；未知为null，不把ml当作g；0只用于未摄入该项且kcal为0"))
            .put("unit",string("固体优先g，液体优先ml；包装数量可用份、个、杯、碗、瓶、盒、片").put("enum",JSONArray(foodUnits)))
            .put("kcal",JSONObject().put("type","integer").put("minimum",0).put("maximum",10000).put("description","该份食物的估算热量，不是每100克；无法估算先询问"))
            .put("nutrients",schema(JSONObject()
                .put("protein_g",JSONObject().put("type",JSONArray(listOf("number","null"))).put("minimum",0).put("maximum",2500).put("description","整份蛋白质克数，未知为null"))
                .put("carbs_g",JSONObject().put("type",JSONArray(listOf("number","null"))).put("minimum",0).put("maximum",2500).put("description","整份总碳水克数，不是糖，未知为null"))
                .put("fat_g",JSONObject().put("type",JSONArray(listOf("number","null"))).put("minimum",0).put("maximum",2500).put("description","整份脂肪克数，未知为null")),listOf("protein_g","carbs_g","fat_g")))
            .put("category",string("食品分类；纯牛奶、酸奶属于奶类；豆浆、豆腐属于豆类；茶、咖啡属于饮品。不确定选其他").put("enum",JSONArray(foodCategories))),listOf("name","quantity","unit","kcal","category"))))
    return JSONArray()
        .put(tool("ask_user","缺少影响正确记录的关键信息时，提供一个简短问题和2至4个可点击选项。等待用户选择后继续；不代替餐饮确认，不与记录工具同时调用。",schema(JSONObject()
            .put("question",string("一个必要问题，最多120字").put("maxLength",120))
            .put("options",JSONObject().put("type","array").put("minItems",2).put("maxItems",4).put("uniqueItems",true)
                .put("items",string("简短、互不重复的答案，最多40字").put("maxLength",40))),listOf("question","options"))))
        .put(tool("query_meals","按日期查询本地餐饮、逐项蛋白质/碳水/脂肪克数及未确认估算草稿，state区分。编辑前先查询取得真实meal_id；一次最多31天、20条，truncated时用next_offset继续查询。",schema(JSONObject().put("from",string("起始日期 YYYY-MM-DD" )).put("to",string("结束日期 YYYY-MM-DD")).put("offset",JSONObject().put("type","integer").put("minimum",0).put("description","省略从0开始；继续查询时使用上次返回的next_offset，保持日期范围相同")),listOf("from","to"))))
        .put(tool("record_meal","用户明确说已吃/喝或要求记录时创建待确认草稿。仅讨论建议、食谱或假设时禁止记录。一次会话最多创建或编辑一餐。",schema(fields(),listOf("items"))))
        .put(tool("estimate_meal","饭前照片或仅询问热量时准备估算卡，尚未食用，不计入记录；可合理估计份量，回复仅提示核对卡片。",schema(fields(),listOf("items"))))
        .put(tool("edit_meal","基于已查询到的meal_id修改已确认记录或待确认卡片（state=draft），草稿原卡更新。items必须是修改后完整食物列表，保留未变项目；不确定要先询问。仅确认后覆盖原记录。",schema(fields().put("meal_id",string("query_meals返回的真实meal_id")),listOf("meal_id","items"))))
}

// No writes here: tools can only read a bounded date range or prepare one proposal.
class MealTools(private val existing: List<Meal>,private val now: LocalDateTime,private val drafts: List<Meal> = emptyList()) {
    var clarification: ChatMessage?=null;private set
    var proposal: Meal?=null;private set
    var baseline: Meal?=null;private set
    private val queriedIds=mutableSetOf<String>()
    fun execute(name: String,raw: String): JSONObject = try {
        require(raw.length<=50000){"工具参数过长"}
        val args=checkedJsonObject(raw)
        require(clarification==null){"正在等待用户选择，不能继续执行工具"}
        fun optionalText(key: String): String? {
            if(args.isNull(key))return null
            require(args.opt(key) is String){"$key 必须是文字"}
            return args.getString(key).trim().takeIf {it.isNotEmpty()}
        }
        when(name) {
            "ask_user"->{
                require(proposal==null){"本轮已有餐饮卡片，不再提出选项"}
                val question=optionalText("question")?:error("请提供问题")
                require(question.length<=120){"问题最多120字"}
                val options=parseOptions(args.getJSONArray("options"))
                clarification=ChatMessage(role="assistant",text=question,options=options)
                JSONObject().put("status","awaiting_answer")
            }
            "query_meals"->{
                val from=LocalDate.parse(args.getString("from"));val to=LocalDate.parse(args.getString("to"))
                require(!to.isBefore(from) && java.time.temporal.ChronoUnit.DAYS.between(from,to)<31){"查询范围需为1至31天"}
                val rawOffset=if(args.isNull("offset"))0 else args.get("offset")
                require(rawOffset is Number && rawOffset.toDouble() in 0.0..Int.MAX_VALUE.toDouble() && rawOffset.toDouble()%1==0.0){"offset必须为非负整数"}
                val offset=rawOffset.toInt()
                val found=(existing+drafts.filter {d->existing.none {it.id==d.id}}).filter {it.date>=from.toString() && it.date<=to.toString()}.sortedByDescending {it.date}
                val rows=found.drop(offset).take(20);queriedIds.addAll(rows.map{it.id})
                val next=offset+rows.size
                JSONObject().put("status","ok").put("truncated",next<found.size).put("next_offset",if(next<found.size)next else JSONObject.NULL).put("meals",JSONArray(rows.map {it.json().apply {put("meal_id",it.id);put("state",if(existing.any {m->m.id==it.id})"confirmed" else "draft");remove("photo");remove("replacesId")}}))
            }
            "record_meal","edit_meal","estimate_meal"->{
                require(proposal==null){"本轮已有待确认草稿，请先让用户确认"}
                val old=if(name=="edit_meal") {
                    val id=args.getString("meal_id");require(id in queriedIds){"请先查询对应餐次，不要猜测ID"}
                    existing.firstOrNull {it.id==id}?:drafts.firstOrNull {it.id==id}?:error("原记录不存在，请重新查询")
                }else null
                val date=optionalText("date")?:old?.date?:now.toLocalDate().toString()
                val parsedDate=LocalDate.parse(date)
                require(!parsedDate.isBefore(LocalDate.of(1900,1,1)) && !parsedDate.isAfter(now.toLocalDate().plusDays(1))){"用餐日期超出可记录范围，请核对日期"}
                require(name=="estimate_meal" || !parsedDate.isAfter(now.toLocalDate())){"未来餐饮只能估算，不能记录为已摄入"}
                val time=optionalText("time")?:old?.time?:now.toLocalTime().withSecond(0).withNano(0).toString()
                require(Regex("\\d{2}:\\d{2}").matches(time)){"时间需使用HH:mm格式"}
                val explicit=optionalText("meal_type")
                require(explicit==null || explicit in mealTypes || explicit in listOf("早饭","午饭","晚饭")){"餐次无效，请核对早餐、午餐、晚餐、夜宵或加餐"}
                val title=inferMealType(LocalTime.parse(time),explicit?:old?.title)
                val editingDraft=old!=null && existing.none {it.id==old.id}
                val meal=Meal(id=if(editingDraft)old!!.id else java.util.UUID.randomUUID().toString(),date=date,title=title,items=parseItems(args),photo=old?.photo,source=if(name=="estimate_meal")"饭前估算 · 尚未食用" else if(old==null)"聊天记录 · 用户确认" else "聊天修改 · 待确认",time=time,replacesId=if(editingDraft)old?.replacesId else old?.id,replacesDraftId=if(editingDraft)old?.replacesDraftId else null,beforeItems=old?.beforeItems?:old?.items)
                require(meal.valid()){"餐次数据无效，请询问用户补充"}
                proposal=meal;baseline=old
                JSONObject().put("status","pending_confirmation").put("instruction","草稿已准备，尚未保存。请让用户核对下方卡片并确认，禁止声称已入账。").put("title",title).put("kcal",meal.kcal)
            }
            else->JSONObject().put("status","error").put("message","未知工具，不执行")
        }
    } catch(e: Exception) {JSONObject().put("status","error").put("message",when(e) {
        is org.json.JSONException->"工具参数格式不正确，请补齐字段，不要猜测热量"
        is java.time.DateTimeException->"日期或时间格式不正确，请询问用户"
        else->e.message?.take(180)?:"无法执行，请向用户说明"
    })}
}
