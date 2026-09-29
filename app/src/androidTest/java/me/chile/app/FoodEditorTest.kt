package me.chile.app

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.domain.FoodItem
import me.chile.app.ui.ChileTheme
import me.chile.app.ui.FoodGrid
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodEditorTest {
    @Test fun editingFoodSavesValuesAndCancellingPreservesLastSave() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val automation=instrumentation.uiAutomation
        val foods=mutableStateOf(listOf(FoodItem("糙米饭",150.0,174,"主食")))
        fun find(node: AccessibilityNodeInfo?,label: String): AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.text?.toString()==label || node.contentDescription?.toString()==label)return node
            for(i in 0 until node.childCount)find(node.getChild(i),label)?.let {return it}
            return null
        }
        fun node(label: String): AccessibilityNodeInfo {
            val deadline=SystemClock.uptimeMillis()+3000
            do {instrumentation.waitForIdleSync();find(automation.rootInActiveWindow,label)?.let {return it};Thread.sleep(100)} while(SystemClock.uptimeMillis()<deadline)
            error("Missing editor control: $label")
        }
        fun tap(label: String) {
            val bounds=Rect();var stable=0;val deadline=SystemClock.uptimeMillis()+4000
            do {
                val current=Rect();node(label).getBoundsInScreen(current)
                stable=if(current==bounds)stable+1 else 0
                bounds.set(current);Thread.sleep(100)
            } while(stable<3 && SystemClock.uptimeMillis()<deadline)
            assertTrue("Control must stop moving before tapping: $label",stable>=3)
            val now=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                try {automation.injectInputEvent(event,true)} finally {event.recycle()}
            }
            Thread.sleep(300)
        }
        fun enter(label: String,value: String) {
            tap(label)
            fun field(): AccessibilityNodeInfo {
                var current: AccessibilityNodeInfo?=node(label)
                while(current!=null && !current.isEditable)current=current.parent
                return requireNotNull(current){"Missing editable ancestor: $label"}
            }
            val focusDeadline=SystemClock.uptimeMillis()+3000
            while(!field().isFocused && SystemClock.uptimeMillis()<focusDeadline)Thread.sleep(50)
            assertTrue("Input must have focus before typing: $label",field().isFocused)
            val length=field().text.length
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MOVE_END)
            repeat(length){instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)}
            val emptyDeadline=SystemClock.uptimeMillis()+3000
            while(field().text?.isNotEmpty()==true && SystemClock.uptimeMillis()<emptyDeadline)Thread.sleep(50)
            assertEquals("Clear old value before replacement", "",field().text?.toString().orEmpty())
            instrumentation.sendStringSync(value)
            instrumentation.waitForIdleSync()
            val deadline=SystemClock.uptimeMillis()+3000
            while(field().text?.toString()!=value && SystemClock.uptimeMillis()<deadline)Thread.sleep(50)
            assertEquals("Input must contain the replacement before saving: $label",value,field().text?.toString())
        }
        ActivityScenario.launch<PreviewActivity>(Intent(instrumentation.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {FoodGrid(foods.value){foods.value=it}}}}
            tap("糙米饭");enter("热量 · kcal","175");enter("份量 · g","160");tap("完成")
            scenario.onActivity {assertEquals(175,foods.value.single().kcal);assertEquals(160.0,foods.value.single().amount!!,0.01)}
            tap("糙米饭");enter("热量 · kcal","999");tap("关闭食物编辑")
            scenario.onActivity {assertEquals(175,foods.value.single().kcal)}
            tap("糙米饭");tap("移除食物")
            scenario.onActivity {assertTrue(foods.value.isEmpty())}
        }
    }
}
