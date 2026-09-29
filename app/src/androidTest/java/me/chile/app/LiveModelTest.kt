package me.chile.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.chile.app.data.AiClient
import me.chile.app.data.KeyVault
import me.chile.app.data.LocalStore
import me.chile.app.data.configJson
import me.chile.app.data.json
import me.chile.app.domain.ChatMessage
import me.chile.app.domain.ModelConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in only: uses the app's configured provider with synthetic inputs, without saving meals. */
@RunWith(AndroidJUnit4::class)
class LiveModelTest {
    @Test fun chatNaturallyAndPrepareSyntheticMilkRecord()=runBlocking {
        assumeTrue("Real provider tests require explicit liveModel=true",InstrumentationRegistry.getArguments().getString("liveModel")=="true")
        val app=ApplicationProvider.getApplicationContext<Application>()
        val key=KeyVault(app).read()?:error("请先在模拟器应用中配置 API Key")
        val config=LocalStore(app).use {it.get("config")?.let(::configJson)?:ModelConfig()}
        val client=AiClient()
        val results=JSONArray()
        try {
            run {
                val input="你又偷懒了？"
                val reply=client.agentChat(config,key,listOf(ChatMessage(role="user",text=input)),emptyList(),emptyList(),onText={})
                results.put(JSONObject().put("scenario","native_chat").put("input",input).put("reply",reply.text))
                assertTrue("Chat reply must contain text",reply.text.isNotBlank())
                assertNull("Small talk must not create a meal",reply.proposal)
                assertNull("Small talk must not record exercise",reply.exercise)
            }
            val input="我刚喝了250毫升纯牛奶，包装写着这一瓶155千卡，帮我记录。"
            val reply=client.agentChat(config,key,listOf(ChatMessage(role="user",text=input)),emptyList(),emptyList(),onText={})
            results.put(JSONObject().put("scenario","milk_record").put("input",input).put("reply",reply.text).put("proposal",reply.proposal?.json()?:JSONObject.NULL))
            val meal=requireNotNull(reply.proposal){"模型没有为明确记录请求准备卡片"}
            assertTrue(meal.valid());assertEquals(155,meal.kcal)
            assertEquals(1,meal.items.size)
            assertEquals("奶类",meal.items.single().category)
            assertEquals("ml",meal.items.single().unit)
            assertEquals(250.0,meal.items.single().amount!!,0.01)
        } finally {
            File(app.filesDir,"live-model-audit.json").writeText(JSONObject().put("model",config.chatModel).put("results",results).toString(2))
        }
    }
}
