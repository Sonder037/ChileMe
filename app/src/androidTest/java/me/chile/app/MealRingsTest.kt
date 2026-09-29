package me.chile.app

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.ui.ChileTheme
import me.chile.app.ui.MealEnergyRings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealRingsTest {
    @Test fun partialRingCapHasNoAntialiasSeamAcrossItsMiddle() {
        val inst=InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {MealEnergyRings(listOf("午餐" to 600),1000,true){}}}}}
            Thread.sleep(800);inst.waitForIdleSync()
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if(node==null)return null
                if(node.contentDescription?.startsWith("摄入：")==true)return node
                for(i in 0 until node.childCount)find(node.getChild(i))?.let {return it}
                return null
            }
            var ring: AccessibilityNodeInfo?=null
            val deadline=SystemClock.uptimeMillis()+4000
            do {
                if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache()
                ring=find(inst.uiAutomation.rootInActiveWindow)
                if(ring==null)Thread.sleep(100)
            } while(ring==null && SystemClock.uptimeMillis()<deadline)
            assertNotNull("Ring must be exposed before sampling pixels",ring)
            val bounds=Rect();ring!!.getBoundsInScreen(bounds)
            val density=inst.targetContext.resources.displayMetrics.density
            val radius=minOf(bounds.width()/2f-20*density,bounds.height()-42*density)
            val angle=Math.toRadians(288.0)
            val bitmap=inst.uiAutomation.takeScreenshot()!!
            try {
                var worst=0
                for(step in -12..12) {
                    val r=radius+step*density/2
                    val x=kotlin.math.round(bounds.exactCenterX()+kotlin.math.cos(angle)*r).toInt()
                    val y=kotlin.math.round(bounds.bottom-16*density+kotlin.math.sin(angle)*r).toInt()
                    val pixel=bitmap.getPixel(x,y)
                    worst=maxOf(worst,kotlin.math.abs(android.graphics.Color.red(pixel)-77),kotlin.math.abs(android.graphics.Color.green(pixel)-148),kotlin.math.abs(android.graphics.Color.blue(pixel)-245))
                }
                assertTrue("Cap center must stay solid meal blue; worst channel difference=$worst",worst<=3)
            } finally {bitmap.recycle()}
        }
    }

    @Test fun longPressIncludesRoundEndCapsAndClearsOnRelease() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val automation=instrumentation.uiAutomation
        fun find(node: AccessibilityNodeInfo?,match: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
            if(node==null)return null
            if(match(node))return node
            for(i in 0 until node.childCount)find(node.getChild(i),match)?.let {return it}
            return null
        }
        ActivityScenario.launch<PreviewActivity>(Intent(instrumentation.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {MealEnergyRings(listOf("早餐" to 300,"午餐" to 600,"晚餐" to 300),200,true,dailyReference=400){}}}}}
            Thread.sleep(800);instrumentation.waitForIdleSync()
            val bounds=Rect()
            find(automation.rootInActiveWindow){it.contentDescription?.startsWith("摄入：")==true}!!.getBoundsInScreen(bounds)
            val density=instrumentation.targetContext.resources.displayMetrics.density
            val originY=bounds.bottom-16*density
            val radius=minOf(bounds.width()/2f-20*density,bounds.height()-42*density)
            val points=listOf(Triple(bounds.exactCenterX()-radius,originY+3*density,"早餐"),Triple(bounds.exactCenterX(),originY-radius,"午餐"),Triple(bounds.exactCenterX()+radius,originY+3*density,"晚餐"))
            val innerRadius=radius-33*density
            val activityPoints=listOf(210.0 to "日常参考 400 kcal",255.0 to "已记运动 200 kcal").map {(angle,label)->
                val radians=Math.toRadians(angle)
                Triple(bounds.exactCenterX()+(kotlin.math.cos(radians)*innerRadius).toFloat(),originY+(kotlin.math.sin(radians)*innerRadius).toFloat(),label)
            }
            for((x,y,label) in points+activityPoints) {
                val now=SystemClock.uptimeMillis()
                fun event(action: Int) {val e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,x,y,0);try {automation.injectInputEvent(e,true)} finally {e.recycle()}}
                event(MotionEvent.ACTION_DOWN)
                try {
                    val deadline=SystemClock.uptimeMillis()+3000
                    do {Thread.sleep(100);instrumentation.waitForIdleSync()}
                    while(find(automation.rootInActiveWindow){it.text?.toString()==label}==null && SystemClock.uptimeMillis()<deadline)
                    assertNotNull("Long press on $label including round cap must select it",find(automation.rootInActiveWindow){it.text?.toString()==label})
                } finally {event(MotionEvent.ACTION_UP)}
                val deadline=SystemClock.uptimeMillis()+2500
                do {Thread.sleep(100);instrumentation.waitForIdleSync()}
                while(find(automation.rootInActiveWindow){it.text?.toString()==label}!=null && SystemClock.uptimeMillis()<deadline)
                assertNull("Selection must disappear after release",find(automation.rootInActiveWindow){it.text?.toString()==label})
            }
        }
    }
}
