package me.chile.app

import me.chile.app.data.*
import me.chile.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SystemPromptTest {
    @Test fun everyAvailableToolHasInstructionsAndUnavailableToolsAreExplicit() {
        val schema=mealToolDefinitions().apply {val extra=activityToolDefinitions();for(i in 0 until extra.length())put(extra.getJSONObject(i))}
        for(i in 0 until schema.length())assertTrue(appSystemRules.contains(schema.getJSONObject(i).getJSONObject("function").getString("name")))
        assertTrue(appSystemRules.contains("不能直接打开数据库"))
        assertTrue(appSystemRules.contains("pending_confirmation"))
        assertTrue(systemPrompt(emptyList(),toolsAvailable=false).contains("本次没有开放工具"))
    }
}
