package me.chile.app

import android.app.Application
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import me.chile.app.ui.ChileApp
import me.chile.app.ui.ChileTheme
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.LocalStore
import me.chile.app.data.messagePage
import me.chile.app.domain.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ChatHistoryTest {
    @Test fun prependingHistoryKeepsVisibleMessageAtSamePosition() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val database="chile-history-anchor-test.db";app.deleteDatabase(database)
        LocalStore(app,database).use {db->
            db.put("onboarded","true")
            repeat(50) {db.addMessage(ChatMessage(role="user",text="很早以前$it",createdAt=LocalDateTime.now().minusDays(20).toString()))}
            db.addMessage(ChatMessage(role="user",text="保持这条的位置"))
        }
        val owner=ViewModelStore();val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun find(n: AccessibilityNodeInfo?,label: String): AccessibilityNodeInfo? {
            if(n==null)return null
            if(n.text?.toString()==label || n.contentDescription?.toString()==label)return n
            for(i in 0 until n.childCount)find(n.getChild(i),label)?.let {return it}
            return null
        }
        fun node(label: String): AccessibilityNodeInfo {
            repeat(60) {instrumentation.waitForIdleSync();find(instrumentation.uiAutomation.rootInActiveWindow,label)?.let {return it};Thread.sleep(50)}
            error("Missing $label")
        }
        try {ActivityScenario.launch<PreviewActivity>(Intent(app,PreviewActivity::class.java)).use {scenario->
            lateinit var vm: AppViewModel
            scenario.onActivity {activity->
                vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]
                activity.setContent {ChileTheme {ChileApp(vm)}}
            }
            val before=android.graphics.Rect();node("保持这条的位置").getBoundsInScreen(before)
            var button: AccessibilityNodeInfo?=node("更早消息")
            while(button!=null && !button.isClickable)button=button.parent
            assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val deadline=System.nanoTime()+5_000_000_000L
            do {Thread.sleep(50);var ready=false;instrumentation.runOnMainSync {ready=!vm.loadingOlder && vm.data.messages.size==41};if(ready)break} while(System.nanoTime()<deadline)
            instrumentation.runOnMainSync {assertEquals(41,vm.data.messages.size)}
            Thread.sleep(350)
            val after=android.graphics.Rect();node("保持这条的位置").getBoundsInScreen(after)
            assertTrue("History insertion must preserve reading position: $before -> $after",kotlin.math.abs(before.top-after.top)<=4)
            LocalStore(app,database).use {it.addMessage(ChatMessage(role="assistant",text="收到新的回复"))}
            instrumentation.runOnMainSync {vm.toggleMemory()}
            val latest=android.graphics.Rect();node("收到新的回复").getBoundsInScreen(latest)
            Thread.sleep(500)
            node("收到新的回复").getBoundsInScreen(latest)
            val composer=android.graphics.Rect();node("消息输入").getBoundsInScreen(composer)
            assertTrue("New replies must remain above the composer",latest.bottom<=composer.top)
            assertTrue("History padding must not leave a blank screen below new replies",composer.top-latest.bottom<400)
        }}finally{instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
    @Test fun startupLoadsOnlyRecentBoundedMessages() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val database="chile-history-test.db";app.deleteDatabase(database)
        val now=LocalDateTime.now()
        LocalStore(app,database).use {db->
            repeat(50) {db.addMessage(ChatMessage(id="old$it",role="user",text="旧消息$it",createdAt=now.minusDays(20).toString()))}
            repeat(45) {db.addMessage(ChatMessage(id="recent$it",role="user",text="新消息$it",createdAt=now.toString()))}
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation();val owner=ViewModelStore()
        lateinit var vm: AppViewModel
        fun awaitState(condition: ()->Boolean) {
            val deadline=System.nanoTime()+5_000_000_000L
            do {var ready=false;instrumentation.runOnMainSync {ready=condition()};if(ready)return;Thread.sleep(30)} while(System.nanoTime()<deadline)
            fail("History state did not settle")
        }
        try {
            instrumentation.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]}
            awaitState {!vm.loading}
            instrumentation.runOnMainSync {
                assertFalse(vm.loading)
                assertEquals(40,vm.data.messages.size)
                assertEquals("recent5",vm.data.messages.first().id)
                assertEquals("recent44",vm.data.messages.last().id)
                assertTrue(vm.data.hasOlderMessages)
                vm.loadOlderMessages()
            }
            awaitState {!vm.loadingOlder && vm.data.messages.size==80}
            instrumentation.runOnMainSync {vm.loadOlderMessages()}
            awaitState {!vm.loadingOlder && vm.data.messages.size==95}
            instrumentation.runOnMainSync {
                assertEquals(95,vm.data.messages.map {it.id}.distinct().size)
                assertFalse(vm.data.hasOlderMessages)
                assertEquals("old0",vm.data.messages.first().id)
                vm.toggleMemory()
            }
            awaitState {!vm.data.memoryEnabled}
            instrumentation.runOnMainSync {assertEquals(95,vm.data.messages.size)}
            LocalStore(app,database).use {db->
                db.addMessage(ChatMessage(id="latest",role="user",text="最新"))
                val first=db.messagePage()
                val second=db.messagePage(before=first.firstSeq,older=true)
                assertEquals("latest",first.messages.last().id)
                assertTrue(first.messages.map {it.id}.intersect(second.messages.map {it.id}.toSet()).isEmpty())
            }
        } finally {instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
    @Test fun historyOutsideSevenDaysAndUnknownTimestampsRemainLoadable() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val database="chile-history-old-test.db";app.deleteDatabase(database)
        try {LocalStore(app,database).use {db->
            db.addMessage(ChatMessage(id="old",role="user",text="以前",createdAt=LocalDateTime.now().minusDays(8).toString()))
            db.addMessage(ChatMessage(id="unknown",role="user",text="旧版本",createdAt=null))
            assertTrue(db.messagePage().messages.isEmpty())
            assertTrue(db.messagePage().hasOlder)
            assertEquals(listOf("old","unknown"),db.messagePage(older=true).messages.map {it.id})
        }}finally{app.deleteDatabase(database)}
    }
}
