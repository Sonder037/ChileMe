package me.chile.app

import me.chile.app.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedToolsTest {
    @Test fun bundledToolContractMatchesAndroid() {
        val expected=mealToolDefinitions().apply {
            val activity=activityToolDefinitions()
            for(i in 0 until activity.length())put(activity.getJSONObject(i))
        }
        val shared=checkNotNull(javaClass.getResourceAsStream("/tools.json")).bufferedReader(Charsets.UTF_8).use {JSONArray(it.readText())}
        assertEquals("Update the bundled contract when Android tools change",value(expected),value(shared))
    }
    private fun value(raw: Any?): Any? = when(raw) {
        is JSONObject -> raw.keys().asSequence().associateWith { value(raw.get(it)) }
        is JSONArray -> (0 until raw.length()).map { value(raw.get(it)) }
        JSONObject.NULL -> null
        else -> raw
    }
}
