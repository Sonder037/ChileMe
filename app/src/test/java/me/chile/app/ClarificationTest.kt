package me.chile.app

import me.chile.app.data.MealTools
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ClarificationTest {
    @Test fun choicesPauseToolsWithoutCreatingAMeal() {
        val tools=MealTools(emptyList(),LocalDateTime.now())
        val result=tools.execute("ask_user","""{"question":"这是新的一餐吗？","options":["新增一餐","修改上一餐"]}""")
        assertEquals("awaiting_answer",result.getString("status"))
        assertNull(tools.proposal)
        assertEquals("error",tools.execute("record_meal","""{"items":[]}""").getString("status"))
    }
    @Test fun duplicateOrExcessiveChoicesAreRejected() {
        for(options in listOf("[]","[\"新餐\"]","[\"新餐\",\" 新餐 \"]","[\"1\",\"2\",\"3\",\"4\",\"5\"]")) {
            assertEquals("error",MealTools(emptyList(),LocalDateTime.now()).execute("ask_user","""{"question":"选一下","options":$options}""").getString("status"))
        }
    }
}
