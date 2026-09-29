package me.chile.app.domain

import java.time.LocalTime

/** Group whole meal records, never reclassify foods within an explicitly named meal. */
fun mealBreakdown(meals: List<Meal>,date: String): List<Pair<String,Int>> {
    fun label(meal: Meal): String {
        val aliases=listOf("早餐" to "早餐","早饭" to "早餐","午餐" to "午餐","午饭" to "午餐",
            "晚餐" to "晚餐","晚饭" to "晚餐","夜宵" to "夜宵","宵夜" to "夜宵","零食" to "零食","加餐" to "零食")
        aliases.firstOrNull {meal.title.contains(it.first)}?.let {return it.second}
        val time=runCatching {LocalTime.parse(meal.time)}.getOrNull() ?: return "其他"
        return inferMealType(time).let {if(it=="加餐")"零食" else it}
    }
    val grouped=meals.filter {it.date==date}.groupBy(::label).mapValues {(_,rows)->rows.sumOf {it.kcal}}
    return listOf("早餐","午餐","晚餐","零食","夜宵","其他").mapNotNull {name->grouped[name]?.let {name to it}}
}
