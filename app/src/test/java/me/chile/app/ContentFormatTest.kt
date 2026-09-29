package me.chile.app

import me.chile.app.data.*
import me.chile.app.domain.*
import me.chile.app.ui.markdownText
import me.chile.app.ui.markdownSections
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ContentFormatTest {
    @Test fun longMarkdownKeepsTextAndStylesWhenDividedForLayout() {
        val source="**"+(1..100).joinToString("\n") {"第 $it 行，保留跨行加粗和中文。"}+"**\n\n`代码`\n\n- 最后一个列表项"
        val whole=markdownText(source)
        val sections=markdownSections(whole)
        assertTrue("Long messages should reuse completed layout sections",sections.size>1)
        assertEquals(whole.text,sections.joinToString("\n"){it.text})
        var offset=0
        for(section in sections) {
            for(i in section.indices)if(section[i]!='\n') {
                assertEquals(whole.spanStyles.filter {offset+i in it.start until it.end}.map {it.item},
                    section.spanStyles.filter {i in it.start until it.end}.map {it.item})
            }
            offset+=section.length+1
        }
        val longer=markdownSections(markdownText(source+"\n\n再补一句。"))
        assertEquals(sections.dropLast(1),longer.take(sections.size-1))
        assertEquals(listOf(markdownText("简短回复")),markdownSections(markdownText("简短回复")))
    }
    @Test fun zeroConsumptionCanBeEditedWithoutContradictingToolSchema() {
        val food=FoodItem("牛奶",null,0,"奶类",0.0,"ml")
        val meal=Meal(id="all-left",date="2026-09-25",items=listOf(food))
        val definitions=mealToolDefinitions()
        val edit=(0 until definitions.length()).map {definitions.getJSONObject(it).getJSONObject("function")}.single {it.getString("name")=="edit_meal"}
        val quantity=edit.getJSONObject("parameters").getJSONObject("properties").getJSONObject("items").getJSONObject("items").getJSONObject("properties").getJSONObject("quantity")
        val value=food.amount!!
        assertTrue("Zero-consumption rows must be expressible in edit_meal",(!quantity.has("minimum") || value>=quantity.getDouble("minimum")) && (!quantity.has("exclusiveMinimum") || value>quantity.getDouble("exclusiveMinimum")))
        val tools=MealTools(listOf(meal),LocalDateTime.parse("2026-09-25T12:00"))
        tools.execute("query_meals","""{"from":"2026-09-25","to":"2026-09-25"}""")
        assertEquals("pending_confirmation",tools.execute("edit_meal","""{"meal_id":"all-left","items":[${food.json()}]}""").getString("status"))
        assertEquals(0,tools.proposal!!.kcal)
        assertTrue(runCatching {parseItems(JSONObject("""{"items":[{"name":"牛奶","quantity":0,"unit":"ml","kcal":10,"category":"奶类"}]}"""))}.isFailure)
    }
    @Test fun liquidUnitsSurviveConfirmationSerialization() {
        val food=parseItems(JSONObject("""{"items":[{"name":"纯牛奶","quantity":250,"unit":"ml","kcal":155,"category":"奶豆"}]}""")).single()
        assertEquals("奶类",food.category);assertNull(food.grams);assertEquals("250 ml",food.portion())
        val meal=Meal(items=listOf(food))
        assertEquals(meal,mealJson(meal.json().toString()))
        val old="""{"id":"old","date":"2026-09-25","title":"早餐","source":"手动","items":[{"name":"豆浆","grams":250,"kcal":80,"category":"奶豆"}]}"""
        assertEquals("豆类",mealJson(old).items.single().category)
        assertEquals("250 g",mealJson(old).items.single().portion())
    }
    @Test fun ambiguousAmountsAndInvalidCategoriesAreRejected() {
        listOf(
            JSONObject().put("quantity",250).put("unit","ml").put("grams",250),
            JSONObject().put("grams",250).put("unit","ml"),
            JSONObject().put("grams",2).put("unit","份"),
            JSONObject().put("quantity","一杯").put("unit","ml"),
            JSONObject().put("quantity",250),
            JSONObject().put("quantity",-2).put("unit","g"),
            JSONObject().put("quantity",20).put("unit","公斤")
        ).forEach {fields->
            fields.put("name","饮料").put("kcal",100)
            assertThrows("Must reject semantically invalid quantities: $fields",IllegalArgumentException::class.java) {
                parseItems(JSONObject().put("items",org.json.JSONArray().put(fields)))
            }
        }
        assertTrue(runCatching {parseItems(JSONObject("""{"items":[{"name":"米饭","kcal":100,"category":"随便"}]}"""))}.isFailure)
        assertEquals("2.5 g",FoodItem("米饭",2.5,3).portion())
    }
    @Test fun optionalNullsWorkButInvalidMealTimeDoesNotSilentlyPass() {
        val now=LocalDateTime.parse("2026-09-25T08:30")
        val base=""""items":[{"name":"牛奶","quantity":250,"unit":"ml","category":"奶类","kcal":155}]"""
        assertEquals("pending_confirmation",MealTools(emptyList(),now).execute("record_meal","""{$base,"date":null,"time":null,"meal_type":null}""").getString("status"))
        for(extra in listOf("\"time\":\"8:30\"","\"meal_type\":\"不清楚\"","\"date\":25")) {
            assertEquals("error",MealTools(emptyList(),now).execute("record_meal","{$base,$extra}").getString("status"))
        }
    }
    @Test fun markdownKeepsBoldListsCodeAndLiteralHtml() {
        val text=markdownText("## 建议\n\n**早餐**\n\n- 牛奶\n- 鸡蛋\n\n3. 一项\n4. 两项\n\n`**原样**`\n\n<script>文字</script>")
        assertTrue(text.text.contains("• 牛奶"));assertTrue(text.text.contains("3. 一项"))
        assertTrue(text.text.contains("**原样**"));assertTrue(text.text.contains("<script>文字</script>"))
        val start=text.text.indexOf("早餐")
        assertTrue(text.spanStyles.any {it.start==start && it.end==start+2 && it.item.fontWeight==FontWeight.Bold})
        assertEquals("没写完 **加粗",markdownText("没写完 **加粗").text)
    }
}
