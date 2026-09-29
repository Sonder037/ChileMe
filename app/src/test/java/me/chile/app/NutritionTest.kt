package me.chile.app

import me.chile.app.domain.*
import me.chile.app.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class NutritionTest {
    @Test fun chineseRangesAreReturnedToAgentRatherThanOneRequiredIntake() {
        val p=Profile("male",30,175.0,70.0)
        val json=nutritionDay(emptyList(),p,p.date).json()
        val energy=2552.67
        val parts=json.getJSONArray("parts")
        val shares=listOf(Triple(.10,.15,4.0),Triple(.50,.65,4.0),Triple(.20,.30,9.0))
        shares.forEachIndexed {index,(low,high,kcalPerGram)->
            val part=parts.getJSONObject(index)
            assertEquals(energy*low/kcalPerGram,part.getDouble("reference_min_g"),.001)
            assertEquals(energy*high/kcalPerGram,part.getDouble("reference_max_g"),.001)
            assertTrue(part.isNull("grams"))
        }
        assertEquals("碳水",parts.getJSONObject(1).getString("name"))
        assertTrue(json.getString("reference_basis").contains("WS/T 578.1"))
        assertTrue(json.getString("reference_basis").contains("添加糖"))
        val missing=nutritionDay(emptyList(),null,p.date).json().getJSONArray("parts")
        for(i in 0 until missing.length()) {
            assertTrue(missing.getJSONObject(i).isNull("reference_min_g"))
            assertTrue(missing.getJSONObject(i).isNull("reference_max_g"))
        }
    }
    @Test fun referencesUseAdultProfileAndUnknownIsNotZero() {
        val p=Profile("male",30,175.0,70.0,"2026-09-28")
        val target=nutritionReference(p)!!
        assertEquals(2552.67,target.energyKcal,.01)
        assertEquals(target.energyKcal*.15/4,target.proteinG,.001)
        assertNull(nutritionReference(p.copy(age=17)))
        assertNull(nutritionReference(p.copy(sex="unspecified")))
        val food=FoodItem("米饭",100.0,130,nutrients=Nutrients(2.5,28.0,.3))
        val day=nutritionDay(listOf(Meal(date=p.date,items=listOf(food,FoodItem("未知",null,100)))),p,p.date)
        assertEquals(1,day.parts.first().knownItems)
        assertEquals(2,day.parts.first().totalItems)
        assertEquals(2.5,day.parts.first().grams!!,.001)
        assertNull(nutritionDay(emptyList(),p,p.date).parts.first().grams)
        assertEquals(1,NutrientProgress("蛋白质",101.0,100.0,1,1).level)
        assertEquals(2,NutrientProgress("蛋白质",151.0,100.0,1,1).level)
    }
    @Test fun fatWithinChineseRangeDoesNotWarnAndUpperEdgeIsNotATargetToEat() {
        val p=Profile("female",35,160.0,55.0)
        val reference=nutritionReference(p)!!
        assertEquals(1898.80,reference.energyKcal,.01)
        fun progress(share: Double): NutrientProgress {
            val meal=Meal(date=p.date,items=listOf(FoodItem("测试食物",null,2000,nutrients=Nutrients(fatG=reference.energyKcal*share/9))))
            return nutritionDay(listOf(meal),p,p.date).parts.last()
        }
        assertEquals(0,progress(.28).level)
        assertEquals(1,progress(.31).level)
        assertEquals(2,progress(.46).level)
        assertEquals(reference.energyKcal*.20/9,progress(.28).minimum!!,.001)
        assertNull(nutritionReference(p.copy(age=79)))
        assertNull(nutritionReference(p.copy(weight=Double.NaN)))
    }
    @Test fun nutrientsSurviveStorageAndOrdinaryEditPreservesOriginalBaseline() {
        val meal=Meal(id="original",date="2026-09-28",items=listOf(FoodItem("米饭",100.0,130,nutrients=Nutrients(2.5,28.0,.3))))
        assertEquals(meal,mealJson(meal.json().toString()))
        val old=meal.json();old.getJSONArray("items").getJSONObject(0).remove("nutrients")
        assertNull(mealJson(old.toString()).items.single().nutrients.proteinG)
        val tools=MealTools(listOf(meal),LocalDateTime.parse("2026-09-28T12:00"))
        tools.execute("query_meals","""{"from":"2026-09-28","to":"2026-09-28"}""")
        tools.execute("edit_meal","""{"meal_id":"original","items":[{"name":"米饭","quantity":50,"unit":"g","kcal":65,"category":"主食","nutrients":{"protein_g":1.25,"carbs_g":14,"fat_g":0.15}}]}""")
        assertEquals(meal.items,tools.proposal!!.beforeItems)
        assertEquals(14.0,tools.proposal!!.items.single().nutrients.carbsG!!,.001)
    }
    @Test fun invalidNutrientTypesAndImpossibleEnergyFailWithoutProposal() {
        for(value in listOf("\"12g\"","-1","999999","true")) {
            val result=runCatching {parseItems(JSONObject("""{"items":[{"name":"饭","kcal":130,"nutrients":{"protein_g":$value}}]}"""))}
            assertTrue(value,result.isFailure)
        }
        assertFalse(FoodItem("饭",100.0,130,nutrients=Nutrients(100.0,100.0,100.0)).valid())
    }
    @Test fun toolSchemaRejectsUnknownFieldsAndStringNumbers() {
        val definitions=mealToolDefinitions()
        for(raw in listOf("""{"question":"选一个","options":["A","B"],"extra":1}""", """{"items":[{"name":"饭","quantity":"100","unit":"g","kcal":130,"category":"主食"}]}""")) {
            assertTrue(runCatching {validateToolArguments(definitions,if(raw.contains("question"))"ask_user" else "record_meal",raw)}.isFailure)
        }
    }
}
