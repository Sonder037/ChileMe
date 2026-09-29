package me.chile.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.*
import me.chile.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.io.File
import android.content.Intent
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario

@RunWith(AndroidJUnit4::class)
class HistoryVolumeTest {
    @Test fun severalYearsOfRecordsStillLoadMessagesInPages() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val inst=InstrumentationRegistry.getInstrumentation()
        val name="history-volume-test.db";app.deleteDatabase(name)
        val owner=ViewModelStore()
        val now=LocalDateTime.now()
        try {
            LocalStore(app,name).use {db->db.transaction {
                db.put("onboarded","true")
                db.saveProfile(Profile("male",30,175.0,70.0,now.minusYears(2).toLocalDate().toString(),"sedentary"))
                repeat(500) {i->
                    val meal=Meal(id="volume-meal-$i",date=now.minusDays((499-i).toLong()).toLocalDate().toString(),items=listOf(FoodItem("合成餐饮",100.0,500)))
                    db.saveMeal(meal,false)
                    db.put("card:${meal.id}",meal.json().toString());db.put("cardState:${meal.id}","confirmed")
                }
                db.put("cardState:volume-meal-1","deleted")
                db.put("cardState:volume-meal-2","superseded")
                db.writableDatabase.delete("kv","key=?",arrayOf("cardState:volume-meal-3"))
                val change=PendingChange("meal","volume-meal-4",db.meals().first {it.id=="volume-meal-4"}.json().toString(),null,"删除记录")
                for(status in listOf("pending","confirmed","cancelled")) {
                    db.put("change:volume-$status",change.json().toString())
                    if(status!="pending")db.put("changeState:volume-$status",status)
                }
                repeat(5000) {i->db.addMessage(ChatMessage(id="volume-$i",role="user",text="历史演示 $i",photo=if(i==0)"older-photo.jpg" else null,createdAt=now.minusDays(((4999-i)/8).toLong()).toString()))}
            }}
            LocalStore(app,name).use {db->
                assertTrue("Photo ownership must include unloaded old messages",db.hasMessagePhoto("older-photo.jpg"))
                assertFalse(db.hasMessagePhoto("unused-photo.jpg"))
            }
            lateinit var vm: AppViewModel
            fun awaitState(condition: ()->Boolean) {
                val deadline=SystemClock.uptimeMillis()+15000
                do {
                    var ready=false;inst.runOnMainSync {ready=condition()}
                    if(ready)return
                    Thread.sleep(20)
                } while(SystemClock.uptimeMillis()<deadline)
                fail("Large local history did not finish loading")
            }
            val start=SystemClock.elapsedRealtime()
            inst.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),name)}})[AppViewModel::class.java]}
            awaitState {!vm.loading}
            val loaded=SystemClock.elapsedRealtime()-start
            inst.runOnMainSync {
                assertNull(vm.error)
                assertEquals(500,vm.data.meals.size)
                assertEquals(500,vm.data.cards.size)
                assertEquals("deleted",vm.data.cardStates["volume-meal-1"])
                assertEquals("superseded",vm.data.cardStates["volume-meal-2"])
                assertEquals("draft",vm.data.cardStates["volume-meal-3"])
                assertEquals("confirmed",vm.data.cardStates["volume-meal-4"])
                for(status in listOf("pending","confirmed","cancelled"))assertEquals(status,vm.data.changeStates["volume-$status"])
                assertEquals(40,vm.data.messages.size)
                assertEquals("volume-4999",vm.data.messages.last().id)
                assertTrue(vm.data.hasOlderMessages)
                vm.loadOlderMessages()
            }
            awaitState {!vm.loadingOlder}
            inst.runOnMainSync {
                assertEquals(80,vm.data.messages.size)
                assertEquals(80,vm.data.messages.map {it.id}.distinct().size)
                assertEquals("volume-4920",vm.data.messages.first().id)
                assertEquals("volume-4999",vm.data.messages.last().id)
            }
            ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
                scenario.onActivity {activity->activity.setContent {me.chile.app.ui.ChileTheme {me.chile.app.ui.ChileApp(vm)}}}
                val ui=ProfileUiTest()
                assertNotNull("Loaded history must open at the latest message",ui.node("历史演示 4999"))
                fun swipe(left: Boolean) {
                    val down=SystemClock.uptimeMillis()
                    val start=if(left)900f else 180f;val distance=if(left)-720f else 720f
                    for(step in 0..13) {
                        val action=when(step){0->MotionEvent.ACTION_DOWN;13->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE}
                        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,start+distance*(step.coerceAtMost(12)/12f),500f,0)
                        try {inst.uiAutomation.injectInputEvent(event,true)}finally {event.recycle()}
                        Thread.sleep(25)
                    }
                }
                swipe(true);assertNotNull(ui.node("热量看板"))
                ui.tap(ui.node("本月")!!)
                assertNotNull(ui.node("${now.year} 年 ${now.monthValue} 月"))
                ui.tap(ui.node("上个月")!!)
                val previous=now.minusMonths(1)
                assertNotNull(ui.node("${previous.year} 年 ${previous.monthValue} 月"))
                ui.tap(ui.node("本周")!!)
                assertNotNull(ui.node("kcal / 日均热量差"))
                swipe(false);assertNotNull(ui.node("历史演示 4999"))
                swipe(false);assertNotNull(ui.node("饮食记录"))
                swipe(true);assertNotNull(ui.node("历史演示 4999"))
            }
            File(app.filesDir,"history-volume-metrics.txt").writeText("5000 messages; 500 meals/cards; initial snapshot ${loaded} ms; loaded 40 then 80 messages")
        } finally {inst.runOnMainSync {owner.clear()};app.deleteDatabase(name)}
    }
}
