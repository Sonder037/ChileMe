package me.chile.app
import me.chile.app.data.*
import me.chile.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class ActivityLevelTest {
 @Test fun fourFitnessLevelsStartAtSedentaryAndUseTheSameFactorEverywhere() {
  assertEquals(listOf(1.2,1.375,1.55,1.725),activityLevels.map {it.factor})
  val base=Profile("male",30,175.0,70.0)
  activityLevels.forEach {level->
   val p=base.copy(activity=level.id)
   assertTrue(p.valid());assertEquals(kotlin.math.floor(p.resting()!!*level.factor+0.5).toInt(),p.totalEnergy())
   assertEquals(p.totalEnergy()!!.toDouble(),nutritionReference(p)!!.energyKcal,0.0)
   validateToolArguments(activityToolDefinitions(),"update_profile","""{"activity":"${level.id}"}""")
  }
 }
 @Test fun profileAndDailyChangesHaveStrictAdvertisedContracts() {
  validateToolArguments(activityToolDefinitions(),"update_profile","""{"weight":68}""")
  validateToolArguments(activityToolDefinitions(),"set_daily_activity","""{"date":"2026-09-21","activity":"light"}""")
  assertTrue(runCatching {validateToolArguments(activityToolDefinitions(),"update_profile","""{"weight":"68"}""")}.isFailure)
 }
 @Test fun selectedActivityChangesTotalWithoutDoubleCountingExercise() {
  val p=profileJson("""{"sex":"male","age":30,"height":175,"weight":70,"date":"2026-09-20","activity":"light"}""")
  val d=LocalDate.parse("2026-09-21")
  val food=listOf(Meal(date=d.toString(),items=listOf(FoodItem("饭",null,2000))))
  val exercise=Exercise(date=d.toString(),time="12:00",name="快走",minutes=30,activeKcal=200,source="设备",messageId="m")
  val day=energyProgress(food,listOf(exercise),listOf(p),d,d).days.single()
  assertEquals(2000-kotlin.math.round(p.resting()!!*1.375).toInt(),day.balance)
  assertEquals("light",p.json().getString("activity"))
 }

 @Test fun datedOverrideDoesNotChangeDefaultOrOtherDays() {
  val old=Profile("male",30,175.0,70.0,"2026-09-20","light")
  val newer=old.copy(date="2026-09-25",activity="high")
  val profiles=listOf(newer,old)
  val overrides=mapOf("2026-09-21" to "moderate")
  assertEquals("moderate",profileForDay(profiles,"2026-09-21",overrides)!!.activity)
  assertEquals("light",profileForDay(profiles,"2026-09-22",overrides)!!.activity)
  assertEquals("high",profileForDay(profiles,"2026-09-26",overrides)!!.activity)
  assertNull(profileForDay(profiles,"2026-09-19",overrides))
  val p=profileForDay(profiles,"2026-09-21",overrides)!!
  assertEquals(p.totalEnergy()!!.toDouble(),nutritionReference(p)!!.energyKcal,0.0)
 }
 @Test fun malformedChangesDoNotCreateProposalsAndReadsExposeNoSecret() {
  val p=Profile("male",30,175.0,70.0,"2026-09-20","light")
  val now=java.time.LocalDateTime.parse("2026-09-29T12:00")
  fun tools()=SettingsTools(listOf(p),emptyMap(),EditableSettings(),emptyList(),emptyList(),now)
  assertEquals("error",tools().execute("update_profile","""{"weight":68}""").getString("status"))
  for((name,args) in listOf("update_profile" to """{"weight":"68"}""","update_profile" to """{"weight":-1}""",
   "set_daily_activity" to """{"date":"2026-09-30","activity":"light"}""",
   "set_daily_activity" to """{"date":"2026-09-29","activity":"1.8"}""",
   "set_daily_activity" to """{"date":"2026-09-01","activity":"high"}""",
   "update_settings" to """{"api_key":"never-accept"}""")) {
   val t=tools();val read=t.execute("query_settings","{}")
   assertFalse(read.toString().contains("api_key"))
   assertEquals("error",t.execute(name,args).getString("status"));assertNull(t.proposal)
  }
  val t=tools();t.execute("query_settings","{}")
  assertEquals("pending_change",t.execute("update_profile","""{"weight":68}""").getString("status"))
  assertEquals(p,profileJson(t.proposal!!.before!!))
  val changed=profileJson(t.proposal!!.after!!)
  assertEquals(68.0,changed.weight,0.0);assertEquals("light",changed.activity);assertEquals("2026-09-29",changed.date)
  assertEquals(t.proposal,changeJson(t.proposal!!.json().toString()))
 }
 @Test fun exerciseEditsRecalculateAndUnknownTypeAsksForCalories() {
  val p=Profile("male",30,175.0,70.0,"2026-09-20","light")
  val ex=Exercise(id="e",date=p.date,time="12:00",name="快走",minutes=30,activeKcal=140,source="设备",messageId="m")
  fun tools()=SettingsTools(listOf(p),emptyMap(),EditableSettings(),emptyList(),listOf(ex),java.time.LocalDateTime.parse("2026-09-29T12:00"))
  val t=tools();assertEquals("pending_change",t.execute("edit_exercise","""{"exercise_id":"e","minutes":60}""").getString("status"))
  val changed=exerciseJson(t.proposal!!.after!!);assertEquals(279,changed.activeKcal);assertEquals(ex.id,changed.id)
  assertEquals("error",tools().execute("edit_exercise","""{"exercise_id":"e","name":"未知项目"}""").getString("status"))
  assertEquals("error",tools().execute("delete_record","""{"kind":"exercise","record_id":"invented"}""").getString("status"))
 }

 @Test fun memoryRequiresTargetedReadAndOnlyPreparesRequestedChange() {
  val memory=Memory("preference:tea","我喜欢喝茶","user:1")
  val t=SettingsTools(emptyList(),emptyMap(),EditableSettings(includeMemory=false),emptyList(),emptyList(),java.time.LocalDateTime.now(),listOf(memory))
  assertEquals("error",t.execute("edit_memory","""{"key":"preference:tea","text":"我喜欢咖啡"}""").getString("status"))
  assertNull(t.proposal)
  assertEquals(1,t.execute("query_memories","""{"keyword":"茶"}""").getJSONArray("memories").length())
  assertEquals("pending_change",t.execute("edit_memory","""{"key":"preference:tea","text":"我喜欢咖啡"}""").getString("status"))
  assertEquals(memory.text,t.proposal!!.before);assertEquals("我喜欢咖啡",t.proposal!!.after)
 }
}
