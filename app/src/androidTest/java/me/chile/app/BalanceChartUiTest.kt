package me.chile.app

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BalanceChartUiTest {
    @Test fun largeFontChartReview() {
        val inst=InstrumentationRegistry.getInstrumentation();val ui=ProfileUiTest()
        val today=LocalDate.now()
        val profile=me.chile.app.domain.Profile("male",30,175.0,70.0,today.minusMonths(2).toString(),"sedentary")
        val meals=(0..27).map {i->me.chile.app.domain.Meal(date=today.minusDays(i.toLong()).toString(),items=listOf(me.chile.app.domain.FoodItem("演示餐",null,profile.totalEnergy()!!+listOf(-850,0,900)[i%3])))}
        for(monthly in listOf(false,true))ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {activity->activity.setContent {
                val density=androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {
                    me.chile.app.ui.ChileTheme {Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {me.chile.app.ui.TrendsScreen(meals,monthly,profiles=listOf(profile))}}
                }
            }}
            Thread.sleep(700);ui.screenshot(if(monthly)"calendar-large-font" else "week-large-font")
            if(!monthly) {
                val bounds=android.graphics.Rect();ui.node("+1000")!!.getBoundsInScreen(bounds)
                assertTrue("Axis tick must stay on one line at 2x font: $bounds",bounds.height()<90)
            } else {
                val label="$today，热量差 -850 千卡"
                ui.tap(ui.node(label)!!)
                assertNotNull("Full value remains readable in selected-day details",ui.node("热量差 -850 kcal"))
            }
        }
    }
    @Test fun calendarAndWeekShowTheSameSignedBalance() {
        val inst=InstrumentationRegistry.getInstrumentation();val ui=ProfileUiTest()
        val today=LocalDate.now()
        val delta=listOf(-850,-430,-160,120,560,900,210)[today.dayOfWeek.value-1]
        val signed=if(delta>0)"+$delta" else "$delta"
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java).putExtra("monthly",true)).use {
            assertNotNull(ui.node("${today}，热量差 $signed 千卡"))
            ui.screenshot("calendar-balanced")
            ui.tap(ui.node("本周")!!)
            assertNotNull(ui.node("kcal / 日均热量差"))
            ui.tap(ui.node("上一周")!!)
            Thread.sleep(400);ui.screenshot("week-balanced")
            ui.tap(ui.node("本月")!!)
            assertNotNull(ui.node("${today}，热量差 $signed 千卡"))
        }
    }
}
