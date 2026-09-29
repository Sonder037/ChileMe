package me.chile.app

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.domain.FoodItem
import me.chile.app.domain.Meal
import me.chile.app.ui.ChileTheme
import me.chile.app.ui.TrendsScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CalendarUpdateTest {
    @Test fun monthHeightUsesFourFiveOrSixRowsWithoutStretchingCells() {
        val ui=ProfileUiTest()
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {activity->activity.setContent {ChileTheme {TrendsScreen(emptyList(),initiallyMonthly=true,today=LocalDate.parse("2021-05-31"))}}}
            fun bounds(label: String): Rect {
                val node=ui.node(label)!!;Thread.sleep(350);node.refresh()
                return Rect().also {node.getBoundsInScreen(it)}
            }
            val cell=bounds("2021-05-31，热量差 暂无数据")
            val six=bounds("2021-05-31").top
            val gap=5*ui.inst.targetContext.resources.displayMetrics.density
            val middleCell=bounds("2021-05-15，热量差 暂无数据")
            val width=ui.inst.targetContext.resources.displayMetrics.widthPixels
            val down=SystemClock.uptimeMillis()
            fun touch(action: Int,x: Float) {
                val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,middleCell.exactCenterY(),0)
                try {ui.inst.uiAutomation.injectInputEvent(event,true)} finally {event.recycle()}
            }
            touch(MotionEvent.ACTION_DOWN,width*.3f)
            for(step in 1..12){touch(MotionEvent.ACTION_MOVE,width*(.3f+step*.025f));Thread.sleep(20)}
            try {
                val halfway=bounds("2021-05-31").top
                assertTrue("Height must follow an unfinished swipe: $six -> $halfway",halfway<six-5 && halfway>six-cell.height()-gap+5)
                ui.screenshot("calendar-resize-mid")
            } finally {touch(MotionEvent.ACTION_CANCEL,width*.6f)}
            Thread.sleep(700)
            ui.tap(ui.node("上个月")!!)
            val five=bounds("2021-04-01").top
            assertTrue("Six to five rows must remove one cell and gap: $six -> $five",kotlin.math.abs(six-five-cell.height()-gap)<5)
            val april=bounds("2021-04-30，热量差 暂无数据")
            assertTrue("Cells stay square",kotlin.math.abs(april.width()-april.height())<=2)
            assertTrue("Cell size must stay unchanged",kotlin.math.abs(cell.height()-april.height())<=2)
            ui.tap(ui.node("上个月")!!)
            bounds("2021-03-01")
            ui.tap(ui.node("上个月")!!)
            val four=bounds("2021-02-01").top
            assertTrue("February with four weeks must remove another row",kotlin.math.abs(five-four-cell.height()-gap)<5)
            assertTrue(ui.node("2021-02-28，热量差 暂无数据")!=null)
        }
    }
    @Test fun currentMonthAdvancesButHistoricalMonthDoesNot() {
        val inst=InstrumentationRegistry.getInstrumentation();val ui=ProfileUiTest()
        val today=mutableStateOf(LocalDate.parse("2026-09-30"))
        val meals=listOf(Meal(date="2024-01-01",items=listOf(FoodItem("历史餐",null,100))))
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {activity->activity.setContent {ChileTheme {TrendsScreen(meals,initiallyMonthly=true,today=today.value)}}}
            assertTrue(ui.node("2026 年 9 月")!=null)
            scenario.onActivity {today.value=LocalDate.parse("2026-10-01")}
            assertTrue("Current month must advance even when old records keep the pager origin fixed",ui.node("2026 年 10 月")!=null)
            ui.tap(ui.node("上个月")!!)
            assertTrue(ui.node("2026 年 9 月")!=null)
            scenario.onActivity {today.value=LocalDate.parse("2026-11-01")}
            Thread.sleep(400)
            assertTrue("Historical month must remain selected",ui.node("2026 年 9 月")!=null)
        }
    }
    @Test fun currentWeekFollowsMidnightWhileBrowsedHistoryStaysPut() {
        val inst=InstrumentationRegistry.getInstrumentation();val ui=ProfileUiTest()
        val today=mutableStateOf(LocalDate.parse("2026-09-27"))
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {activity->activity.setContent {ChileTheme {TrendsScreen(emptyList(),today=today.value)}}}
            assertTrue(ui.node("9/21 — 9/27")!=null)
            scenario.onActivity {today.value=LocalDate.parse("2026-09-28")}
            assertTrue("Current week must advance at Monday midnight",ui.node("9/28 — 10/4")!=null)
            ui.tap(ui.node("上一周")!!)
            assertTrue(ui.node("9/21 — 9/27")!=null)
            scenario.onActivity {today.value=LocalDate.parse("2026-10-05")}
            Thread.sleep(400)
            assertTrue("Browsing history must not jump on a date refresh",ui.node("9/21 — 9/27")!=null)
        }
    }
    @Test fun addingOlderRecordKeepsVisibleMonth() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val automation=instrumentation.uiAutomation
        val today=LocalDate.now()
        val label="${today.year} 年 ${today.monthValue} 月"
        val selected=today.withDayOfMonth(1).toString()
        val meals=mutableStateOf(emptyList<Meal>())
        fun texts(node: AccessibilityNodeInfo?): List<String> {
            if(node==null)return emptyList()
            return listOfNotNull(node.text?.toString(),node.contentDescription?.toString())+(0 until node.childCount).flatMap {texts(node.getChild(it))}
        }
        fun selectFirstDay(node: AccessibilityNodeInfo?): Boolean {
            if(node==null)return false
            if(node.contentDescription?.startsWith("$selected，")==true) {
                val bounds=Rect();node.getBoundsInScreen(bounds)
                val now=SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                    try {automation.injectInputEvent(event,true)} finally {event.recycle()}
                }
                return true
            }
            return (0 until node.childCount).any {selectFirstDay(node.getChild(it))}
        }
        fun assertMonth() {
            Thread.sleep(700);instrumentation.waitForIdleSync()
            var visible=emptyList<String>()
            val deadline=System.nanoTime()+3_000_000_000L
            do {Thread.sleep(100);instrumentation.waitForIdleSync();visible=texts(automation.rootInActiveWindow)} while(label !in visible && System.nanoTime()<deadline)
            assertTrue("Expected current month $label; visible=$visible",label in visible)
        }
        ActivityScenario.launch<PreviewActivity>(Intent(instrumentation.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {activity->activity.setContent {ChileTheme {TrendsScreen(meals.value,initiallyMonthly=true)}}}
            assertMonth()
            assertTrue("Calendar day must be clickable: ${texts(automation.rootInActiveWindow)}",selectFirstDay(automation.rootInActiveWindow))
            Thread.sleep(300)
            assertTrue(selected in texts(automation.rootInActiveWindow))
            scenario.onActivity {meals.value=listOf(Meal(date=today.minusYears(3).toString(),items=listOf(FoodItem("米饭",100.0,130))))}
            assertMonth()
            assertTrue("Backfilling must retain the selected day",selected in texts(automation.rootInActiveWindow))
            scenario.onActivity {meals.value=emptyList()}
            assertMonth()
            assertTrue("Removing the oldest record must retain the selected day",selected in texts(automation.rootInActiveWindow))
        }
    }
}
