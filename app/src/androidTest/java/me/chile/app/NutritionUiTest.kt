package me.chile.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.*
import me.chile.app.domain.*
import me.chile.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class NutritionUiTest {
    @Test fun narrowCardPlacesBalanceBelowTheRings() {
        val inst=InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {Box(Modifier.width(300.dp).safeDrawingPadding()) {
                DailyEnergyCards(listOf(Meal(items=listOf(FoodItem("测试餐",null,1800)))),emptyList(),listOf(Profile("male",30,175.0,70.0)))
            }}}}
            fun find(node: AccessibilityNodeInfo?,match: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
                if(node==null)return null
                if(match(node))return node
                for(i in 0 until node.childCount)find(node.getChild(i),match)?.let {return it}
                return null
            }
            val label=ProfileUiTest().node("热量差 / kcal")!!
            val ring=find(inst.uiAutomation.rootInActiveWindow){it.contentDescription?.startsWith("蛋白质")==true}!!
            val textBounds=Rect();val ringBounds=Rect()
            label.getBoundsInScreen(textBounds);ring.getBoundsInScreen(ringBounds)
            assertTrue("Narrow card must keep its number outside the ring: $textBounds, $ringBounds",textBounds.top>=ringBounds.bottom)
        }
    }

    @Test fun nutritionPersistsAndLabelsOnlyAppearDuringLongPress() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val automation=instrumentation.uiAutomation
        val meal=Meal(items=listOf(FoodItem("合成测试餐",null,2070,nutrients=Nutrients(120.0,150.0,110.0))))
        val dbName="nutrition-test.db"
        context.deleteDatabase(dbName)
        LocalStore(context,dbName).use {it.saveMeal(meal,false);assertEquals(meal,it.meals().single())}
        context.deleteDatabase(dbName)
        fun find(node: AccessibilityNodeInfo?,match: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
            if(node==null)return null
            if(match(node))return node
            for(i in 0 until node.childCount)find(node.getChild(i),match)?.let {return it}
            return null
        }
        ActivityScenario.launch<PreviewActivity>(Intent(context,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                DailyEnergyCards(listOf(meal),emptyList(),listOf(Profile("male",30,175.0,70.0)))
            }}}}
            Thread.sleep(900);instrumentation.waitForIdleSync()
            val bounds=Rect()
            find(automation.rootInActiveWindow){it.contentDescription?.startsWith("蛋白质")==true}!!.getBoundsInScreen(bounds)
            val density=context.resources.displayMetrics.density
            val radius=minOf(bounds.width()/2f-20*density,bounds.height()-42*density)
            fun screenshot(name: String) {scenario.onActivity {activity->
                val view=activity.window.decorView
                val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image));File(activity.filesDir,name).outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            }}
            screenshot("render-nutrition.png")
            for((index,label) in listOf("蛋白质","碳水","脂肪").withIndex()) {
                assertNull(find(automation.rootInActiveWindow){it.text?.startsWith(label)==true})
                val angle=(210+60*index)*PI/180
                val x=bounds.exactCenterX()+cos(angle).toFloat()*radius
                val y=bounds.bottom-16*density+sin(angle).toFloat()*radius
                val start=SystemClock.uptimeMillis()
                fun event(action: Int) {val event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);try {automation.injectInputEvent(event,true)}finally {event.recycle()}}
                event(MotionEvent.ACTION_DOWN)
                try {
                    val deadline=SystemClock.uptimeMillis()+3000
                    do {Thread.sleep(100);instrumentation.waitForIdleSync()}
                    while(find(automation.rootInActiveWindow){it.text?.startsWith(label)==true}==null && SystemClock.uptimeMillis()<deadline)
                    assertNotNull(label,find(automation.rootInActiveWindow){it.text?.startsWith(label)==true})
                    val selected=find(automation.rootInActiveWindow){it.text?.startsWith(label)==true}!!.text.toString()
                    assertTrue("Long press must show a reference range",selected.contains("参考")&&selected.contains("–"))
                    if(index==1)screenshot("render-nutrition-pressed.png")
                } finally {event(MotionEvent.ACTION_UP)}
                val deadline=SystemClock.uptimeMillis()+2500
                do {Thread.sleep(100);instrumentation.waitForIdleSync()}
                while(find(automation.rootInActiveWindow){it.text?.startsWith(label)==true}!=null && SystemClock.uptimeMillis()<deadline)
                assertNull(find(automation.rootInActiveWindow){it.text?.startsWith(label)==true})
            }
        }
    }
}
