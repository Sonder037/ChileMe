package me.chile.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.math.roundToInt

data class Profile(val sex: String, val age: Int, val height: Double, val weight: Double, val date: String = LocalDate.now().toString(), val activity: String? = null) {
    fun valid() = (activity==null || activityLevels.any {it.id==activity}) && sex in listOf("male", "female", "unspecified") && age in 1..120 &&
        height.isFinite() && height in 80.0..250.0 && weight.isFinite() && weight in 15.0..350.0
    fun totalEnergy(): Int? = activityLevels.firstOrNull {it.id==activity}?.let {level->resting()?.let {(it*level.factor).roundToInt()}}
    fun resting(): Int? = if (!valid() || age !in 19..78 || sex == "unspecified") null
        else (10 * weight + 6.25 * height - 5 * age + if (sex == "male") 5 else -161).roundToInt()
}
data class FoodItem(val name: String, val grams: Double?, val kcal: Int, val category: String = "其他", val quantity: Double? = null, val unit: String = "g", val nutrients: Nutrients = Nutrients()) {
    fun valid() = name.isNotBlank() && name.length <= 100 && kcal in 0..10000 &&
        (grams == null || (grams.isFinite() && (grams > 0 || (grams == 0.0 && kcal == 0)) && grams <= 10000)) &&
        (quantity == null || (quantity.isFinite() && (quantity > 0 || (quantity == 0.0 && kcal == 0)) && quantity <= 10000)) && unit in foodUnits && category in foodCategories && nutrients.valid(kcal)
    val amount: Double? get()=quantity?:grams
    fun portion(): String = amount?.let {"${java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()} ${if(quantity==null)"g" else unit}"}?:"份量待补充"
}
data class Meal(val id: String = UUID.randomUUID().toString(), val date: String = LocalDate.now().toString(),
    val title: String = "这一餐", val items: List<FoodItem>, val photo: String? = null, val source: String = "手动", val time: String? = null, val replacesId: String? = null, val replacesDraftId: String? = null, val beforeItems: List<FoodItem>? = null) {
    val kcal: Int get() = items.sumOf { it.kcal }
    fun valid() = runCatching { LocalDate.parse(date) }.isSuccess && title.isNotBlank() && title.length<=100 && (time==null || runCatching { java.time.LocalTime.parse(time) }.isSuccess) &&
        items.size in 1..30 && items.all { it.valid() } && kcal <= 30000
}
data class ChatMessage(val id: String = UUID.randomUUID().toString(), val role: String, val text: String, val photo: String? = null, val mealId: String? = null, val createdAt: String? = java.time.LocalDateTime.now().toString(), val exerciseId: String?=null, val options: List<String> = emptyList())
data class Memory(val key: String, val text: String, val source: String)
data class ModelConfig(val baseUrl: String = "https://api.deepseek.com", val chatModel: String = "deepseek-flash",
    val visionModel: String = "deepseek-flash", val offline: Boolean = false)
enum class Period { WEEK, MONTH }
data class Bucket(val label: String, val sum: Int, val days: Int) { val average: Int? get() = if (days == 0) null else (sum.toDouble() / days).roundToInt() }
data class Trend(val from: LocalDate, val to: LocalDate, val buckets: List<Bucket>) {
    val total get() = buckets.sumOf { it.sum }
    val days get() = buckets.sumOf { it.days }
    val average get() = if (days == 0) null else (total.toDouble() / days).roundToInt()
}
fun trend(meals: List<Meal>, period: Period, anchor: LocalDate): Trend {
    val from = when (period) { Period.WEEK -> anchor.minusDays((anchor.dayOfWeek.value - 1).toLong()); Period.MONTH -> anchor.withDayOfMonth(1) }
    val to = when (period) { Period.WEEK -> from.plusDays(6); Period.MONTH -> YearMonth.from(from).atEndOfMonth() }
    val daily = meals.filter { it.date >= from.toString() && it.date <= to.toString() }.groupBy { LocalDate.parse(it.date) }.mapValues { (_, v) -> v.sumOf { it.kcal } }
    val buckets = (0..java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt()).map { i ->
        val date=from.plusDays(i.toLong())
        val label=if(period==Period.WEEK) listOf("一","二","三","四","五","六","日")[i] else "${date.dayOfMonth}日"
        val total=daily[date]
        Bucket(label,total?:0,if(total==null)0 else 1)
    }
    return Trend(from,to,buckets)
}


val mealTypes=listOf("早餐","午餐","晚餐","夜宵","加餐")
val foodCategories=listOf("主食","蔬菜","水果","肉蛋鱼","奶类","豆类","饮品","零食","其他")
fun inferMealType(time: java.time.LocalTime, explicit: String?=null): String {
    val normalized=mapOf("早饭" to "早餐","午饭" to "午餐","晚饭" to "晚餐")[explicit]?:explicit
    if(normalized in mealTypes)return normalized!!
    return when(time.hour) { in 5..10->"早餐";in 11..14->"午餐";in 17..20->"晚餐";in 21..23,in 0..4->"夜宵";else->"加餐" }
}

val foodUnits=listOf("g","ml","份","个","杯","碗","瓶","盒","片")
fun normalizeCategory(name: String,category: String): String {
    val milk=name.trim() in listOf("牛奶","纯牛奶","鲜奶","酸奶","奶酪") || Regex(".*(?:纯牛奶|鲜牛奶|全脂牛奶|脱脂牛奶|低脂牛奶)$").matches(name.trim())
    if(milk)return "奶类"
    if(category=="奶豆")return if(Regex(".*(?:豆浆|豆奶|豆腐|豆干|黄豆|黑豆)$").matches(name.trim()))"豆类" else "其他"
    return category
}
