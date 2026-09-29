package me.chile.app

import me.chile.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class MealBreakdownTest {
    @Test fun explicitMealWinsOverTimeAndFoodCategory() {
        val meal=Meal(date="2026-09-26",title="午饭",time="22:00",items=listOf(FoodItem("饼干",null,180,"零食")))
        assertEquals(listOf("午餐" to 180),mealBreakdown(listOf(meal),meal.date))
    }
    @Test fun aliasesTimeFallbackMissingTimeAndDateFilteringPreserveTotals() {
        fun meal(title: String,time: String?,kcal: Int,date: String="2026-09-26")=Meal(date=date,title=title,time=time,items=listOf(FoodItem("食物",null,kcal)))
        val rows=listOf(meal("早饭",null,200),meal("早餐",null,100),meal("加餐",null,80),
            meal("记录","23:00",50),meal("记录",null,90),meal("晚餐",null,500,"2026-09-25"))
        assertEquals(listOf("早餐" to 300,"零食" to 80,"夜宵" to 50,"其他" to 90),mealBreakdown(rows,"2026-09-26"))
        assertEquals(520,mealBreakdown(rows,"2026-09-26").sumOf {it.second})
        assertTrue(mealBreakdown(rows,"2026-09-24").isEmpty())
    }
}
