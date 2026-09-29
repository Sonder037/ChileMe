package me.chile.app

import android.app.Application
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.LocalStore
import me.chile.app.domain.ChatMessage
import me.chile.app.domain.Meal
import me.chile.app.domain.FoodItem
import me.chile.app.ui.ChileApp
import me.chile.app.ui.ChileTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ChatStartTest {
    @Test fun opensAtLatestMessageWithTimeOutsideBody() = verifyLatest("最新回复","最新回复")
    @Test fun legacyBeforeMealCardShowsEnabledConfirmation() = verifyLatest(
        "核对后可以直接确认。","确认记录",
        Meal(items=listOf(FoodItem("米饭",150.0,195)),source="饭前估算 · 尚未食用"))
    @Test fun longMarkdownReplyOpensAtItsEndWithoutLosingFormatting() {
        val reply=(1..300).joinToString("\n\n") {"### 第 $it 项\n\n- **摄入**：约 300 kcal\n- 食物：米饭、牛奶\n\n`份量只是估算`"}+"\n\n**长回复末尾**"
        verifyLatest(reply,"长回复末尾")
    }
    private fun verifyLatest(reply: String,expected: String,meal: Meal?=null) {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val automation=instrumentation.uiAutomation
        val database="chile-chat-start-test.db";app.deleteDatabase(database)
        val today=LocalDate.now()
        LocalStore(app,database).use {db->
            db.put("onboarded","true")
            repeat(40){db.addMessage(ChatMessage(role="user",text="旧消息 $it",createdAt="${today}T09:00"))}
            meal?.let {db.stageProposal(it,null)}
            db.addMessage(ChatMessage(role="assistant",text="[消息时间：${today}T12:34] $reply",createdAt="${today}T12:34",mealId=meal?.id))
        }
        val owner=ViewModelStore()
        fun texts(node: AccessibilityNodeInfo?): List<String> = if(node==null)emptyList() else
            listOfNotNull(node.text?.toString())+(0 until node.childCount).flatMap {texts(node.getChild(it))}
        try {
            ActivityScenario.launch<PreviewActivity>(Intent(app,PreviewActivity::class.java)).use {scenario->
                scenario.onActivity {activity->
                    val vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]
                    activity.setContent {ChileTheme {ChileApp(vm)}}
                }
                val deadline=System.nanoTime()+5_000_000_000L
                var visible=emptyList<String>()
                do {Thread.sleep(100);instrumentation.waitForIdleSync();visible=texts(automation.rootInActiveWindow)}
                while("12:34" !in visible && System.nanoTime()<deadline)
                assertTrue("Latest message must be visible: ${visible.map {it.take(80)}}",visible.any {it.contains(expected)})
                assertTrue("Timestamp must be a separate text node","12:34" in visible)
                assertFalse(visible.any {it.contains("消息时间")})
                assertFalse("Must not remain at the beginning","旧消息 0" in visible)
                fun find(node: AccessibilityNodeInfo?,label: String): AccessibilityNodeInfo? {
                    if(node==null)return null
                    if(node.text?.toString()==label || node.contentDescription?.toString()==label)return node
                    for(i in 0 until node.childCount)find(node.getChild(i),label)?.let {return it}
                    return null
                }
                val timeBounds=android.graphics.Rect();val inputBounds=android.graphics.Rect()
                find(automation.rootInActiveWindow,"12:34")!!.getBoundsInScreen(timeBounds)
                find(automation.rootInActiveWindow,"消息输入")!!.getBoundsInScreen(inputBounds)
                assertTrue("Latest timestamp must stay above composer",timeBounds.bottom<=inputBounds.top)
                if(meal!=null) {
                    assertFalse(visible.any {it.contains("等待餐后")})
                    var confirm=find(automation.rootInActiveWindow,"确认记录")
                    while(confirm!=null && !confirm.isClickable)confirm=confirm.parent
                    assertNotNull("Confirmation must be clickable",confirm)
                    assertTrue("Before-meal card must not be locked",confirm!!.isEnabled)
                }
                if(expected=="长回复末尾")scenario.onActivity {activity->
                    val view=activity.window.decorView
                    val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(android.graphics.Canvas(bitmap))
                        java.io.File(activity.filesDir,"render-long-markdown.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                    } finally {bitmap.recycle()}
                }
            }
        } finally {instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
}
