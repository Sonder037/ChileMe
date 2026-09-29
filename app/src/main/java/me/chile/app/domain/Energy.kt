package me.chile.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

data class Exercise(val id: String=UUID.randomUUID().toString(), val date: String,
    val time: String, val name: String, val minutes: Int, val activeKcal: Int,
    val source: String, val messageId: String) {
    fun valid()=name.isNotBlank() && name.length<=80 && minutes in 1..1440 && activeKcal in 0..10000 &&
        messageId.isNotBlank() && runCatching {LocalDate.parse(date);LocalTime.parse(time)}.isSuccess
}

// Latest applicable measurement, never apply a future weight retrospectively.
fun profileOn(profiles: List<Profile>,date: String): Profile? = profiles.filter {it.valid() && it.date<=date}.maxByOrNull {it.date}

// 2024 Adult Compendium: 17160, 17200, 12020. Net expenditure avoids counting rest twice.
val exerciseMets=mapOf("散步" to 3.5,"快走" to 4.8,"慢跑" to 7.5)
fun estimateActiveKcal(activity: String, minutes: Int, profile: Profile): Int {
    require(profile.valid() && profile.age in 19..59){"当前身体资料不适用运动估算，请提供设备的活动千卡"}
    require(minutes in 1..1440){"请提供有效运动时长"}
    val met=exerciseMets[activity]?:error("请补充运动类型、强度或设备显示的活动千卡")
    return ((met-1)*3.5*profile.weight/200*minutes).roundToInt()
}

// Common fitness estimation factors from ACE; these are not FAO PAL categories or measured needs.
// https://www.acefitness.org/certifiednewsarticle/2882/resting-metabolic-rate-best-ways-to-measure-it-and-raise-it-too/
data class ActivityLevel(val id: String,val label: String,val detail: String,val factor: Double)
val activityLevels=listOf(
    ActivityLevel("sedentary","久坐少动","办公久坐，几乎不运动",1.2),
    ActivityLevel("light","轻度活动","每周轻运动 1–3 天",1.375),
    ActivityLevel("moderate","中等活动","每周中等运动 3–5 天",1.55),
    ActivityLevel("high","高度活动","每周较高强度运动 6–7 天",1.725))
fun profileForDay(profiles: List<Profile>,date: String,overrides: Map<String,String>): Profile? =
    profileOn(profiles,date)?.let {p->overrides[date]?.let {p.copy(activity=it)}?:p}

data class EnergyDay(val date: String,val intake: Int?,val resting: Int?,val active: Int,val total: Int?=null) {
    // Legacy profiles retain the old reference until the user chooses an activity level.
    val balance: Int? get()=if(intake!=null && resting!=null)intake-(total?: (resting+active)) else null
}
data class EnergyProgress(val days: List<EnergyDay>) {
    val intake get()=days.mapNotNull {it.intake}.sum()
    val active get()=days.sumOf {it.active}
    val recordedDays get()=days.count {it.intake!=null}
    val comparableDays get()=days.count {it.balance!=null}
    val balance get()=days.mapNotNull {it.balance}.takeIf {it.isNotEmpty()}?.sum()
    val fatEquivalentKg get()=balance?.let {-it/7700.0}
}
fun energyProgress(meals: List<Meal>,exercises: List<Exercise>,profiles: List<Profile>,from: LocalDate,to: LocalDate,overrides: Map<String,String> = emptyMap()): EnergyProgress {
    val count=ChronoUnit.DAYS.between(from,to)
    require(count in 0..30){"查询范围需为1至31天"}
    val food=meals.groupBy {it.date};val activity=exercises.groupBy {it.date}
    return EnergyProgress((0..count.toInt()).map {offset->
        val date=from.plusDays(offset.toLong()).toString()
        val p=profileForDay(profiles,date,overrides)
        EnergyDay(date,food[date]?.sumOf {it.kcal},p?.resting(),activity[date]?.sumOf {it.activeKcal}?:0,p?.totalEnergy())
    })
}

// Linear clock-based reference, not sensor data or a completed exercise record.
fun dailyActivityReference(profile: Profile?,time: LocalTime): Int? {
    val resting=profile?.resting()?:return null
    val total=profile.totalEnergy()?:return null
    return ((total-resting)*time.toSecondOfDay()/86400.0).roundToInt()
}
