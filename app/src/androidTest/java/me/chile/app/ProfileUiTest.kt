package me.chile.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import me.chile.app.ui.ChileTheme
import me.chile.app.ui.ProfileValueDialog
import java.util.concurrent.atomic.AtomicReference
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileUiTest {
        val inst=InstrumentationRegistry.getInstrumentation()
        fun find(node: AccessibilityNodeInfo?,label: String): AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.contentDescription?.toString()==label || node.text?.toString()==label)return node
            for(i in 0 until node.childCount)find(node.getChild(i),label)?.let {return it}
            return null
        }
        fun node(label: String): AccessibilityNodeInfo? {
            repeat(60) {
                if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache()
                find(inst.uiAutomation.rootInActiveWindow,label)?.let {return it};Thread.sleep(100)
            }
            return null
        }
        fun tap(node: AccessibilityNodeInfo) {
            // Wait for the window entrance / wheel snap before injecting a physical tap.
            Thread.sleep(600)
            val label=node.contentDescription?.toString()?:node.text?.toString()
            if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache()
            val current=label?.let {find(inst.uiAutomation.rootInActiveWindow,it)}?:node
            // IME insets can move the composer without invalidating the cached node bounds.
            current.refresh()
            val rect=Rect();current.getBoundsInScreen(rect)
            val now=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
                inst.uiAutomation.injectInputEvent(event,true);event.recycle()
            }
        }
        fun screenshot(name: String) {
            val bitmap=inst.uiAutomation.takeScreenshot()
            File(inst.targetContext.filesDir,"render-$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        fun advance(root: AccessibilityNodeInfo?): Boolean {
            if(root==null)return false
            if(root.className=="android.widget.NumberPicker")return root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            root.actionList.firstOrNull {it.label?.toString()=="增加"}?.let {return root.performAction(it.id)}
            for(i in 0 until root.childCount)if(advance(root.getChild(i)))return true
            return false
        }
        fun setText(root: AccessibilityNodeInfo?,value: String): Boolean {
            if(root==null)return false
            if(root.isEditable)return root.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)})
            for(i in 0 until root.childCount)if(setText(root.getChild(i),value))return true
            return false
        }
    @Test fun onboardingUsesStepsAndKeepsDraftWhenGoingBack() {
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java).putExtra("onboarding",true)).use {scenario->
            assertNotNull("First run must start with a dedicated card",node("先认识一下你"))
            Thread.sleep(650)
            screenshot("onboarding-sex")
            tap(node("男")!!)
            assertNotNull(node("年龄整数"))
            assertTrue(advance(node("年龄整数")))
            assertNotNull(node("26 岁"))
            tap(node("继续")!!)
            assertNotNull(node("身高整数"))
            tap(node("上一步")!!)
            assertNotNull(node("26 岁"))
            scenario.recreate()
            assertNotNull("Activity recreation must retain the chosen age",node("26 岁"))
            Thread.sleep(650)
            screenshot("onboarding-age")
            tap(node("继续")!!)
            assertNotNull(node("身高整数"))
            tap(node("继续")!!)
            assertNotNull(node("体重整数"))
            tap(node("继续")!!)
            tap(node("中等活动 · 1.55×")!!)
            Thread.sleep(350)
            screenshot("onboarding-activity")
            tap(node("连接 AI")!!)
            assertNotNull(node("稍后连接，先本地记录"))
            me.chile.app.data.LocalStore(inst.targetContext,"chile-render-test.db").use {db->
                assertEquals("moderate",db.profiles().first().activity)
                assertEquals(26,db.profiles().first().age)
                assertEquals(170.0,db.profiles().first().height,0.0)
            }
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            assertNotNull("API setup must allow returning to activity data",node("中等活动 · 1.55×"))
            tap(node("连接 AI")!!)
            tap(node("稍后连接，先本地记录")!!)
            assertNotNull("Completed onboarding must open chat",node("消息输入"))
        }
    }
    @Test fun profileOpensWheelAndCancelKeepsExistingValue() {
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java).putExtra("profile",true)).use {
            val card=node("调整体重")
            assertNotNull("Body data must open a wheel instead of a keyboard",card)
            screenshot("profile")
            tap(card!!)
            Thread.sleep(650)
            screenshot("profile-wheel")
            assertNotNull(node("体重整数"))
            assertNull(find(inst.uiAutomation.rootInActiveWindow,"体重小数"))
            screenshot("profile-wheel")
            assertNotNull(node("70 kg"))
            assertTrue(advance(node("体重整数")))
            assertNotNull("Single wheel must update the displayed value",node("71 kg"))
            tap(node("取消")!!)
            assertNotNull(node("调整体重"))
            assertNull(find(inst.uiAutomation.rootInActiveWindow,"体重整数"))
            tap(node("调整体重")!!)
            assertNotNull("Cancel must preserve the original weight",node("70 kg"))
            assertTrue(advance(node("体重整数")))
            assertNotNull(node("71 kg"))
            tap(node("完成")!!)
            assertNotNull(node("71"))
            tap(node("保存身体数据")!!)
            var saved=0.0
            repeat(40) {
                saved=me.chile.app.data.LocalStore(inst.targetContext,"chile-render-test.db").use {db->db.profiles().first().weight}
                if(saved!=71.0)Thread.sleep(100)
            }
            assertEquals("Confirmed wheel value must reach storage",71.0,saved,0.0)
        }
    }

    @Test fun onboardingCanContinueWhileWheelIsSettling() {
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java).putExtra("onboarding",true)).use {
            tap(node("女")!!)
            assertNotNull("Selecting sex must advance without another tap",node("年龄整数"))
            for(label in listOf("年龄整数","身高整数","体重整数")) {
                val wheel=node(label)!!
                var button=node("继续")!!
                while(!button.isClickable && button.parent!=null)button=button.parent
                assertTrue(advance(wheel))
                Thread.sleep(30)
                button.refresh()
                assertTrue("Continue must not wait for the wheel animation",button.isEnabled)
                assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            tap(node("久坐少动 · 1.2×")!!)
            tap(node("连接 AI")!!)
            assertNotNull(node("稍后连接，先本地记录"))
            me.chile.app.data.LocalStore(inst.targetContext,"chile-render-test.db").use {db->
                val saved=db.profiles().first()
                assertEquals("sedentary",saved.activity)
                assertEquals("female",saved.sex)
                assertTrue(saved.valid())
            }
        }
    }
    @Test fun upperBoundaryStopsAtMaximum() {
        val result=AtomicReference<String>()
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {ProfileValueDialog("体重","350",{}) {result.set(it)}}}}
            assertNotNull(node("350 kg"))
            Thread.sleep(650)
            screenshot("profile-boundary")
            assertNotNull(node("350 kg"))
            assertFalse("Wheel must not exceed the profile's maximum",advance(node("体重整数")))
            tap(node("完成")!!)
            inst.waitForIdleSync()
            assertEquals("350",result.get())
        }
    }

    @Test fun existingDecimalOpensRoundedWheelAndInvalidInputCannotConfirm() {
        val result=AtomicReference<String>()
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {ProfileValueDialog("体重","70.25",{}) {result.set(it)}}}}
            assertNotNull(node("70 kg"))
            tap(node("完成")!!)
            inst.waitForIdleSync()
            assertEquals("70",result.get())
            tap(node("键盘输入")!!)
            assertNotNull(node("体重 · kg"))
            assertTrue(setText(inst.uiAutomation.rootInActiveWindow,"351"))
            assertNotNull(node("请输入 15–350 kg 的整数"))
            tap(node("完成")!!)
            inst.waitForIdleSync()
            assertEquals("Invalid input must not be confirmed","70",result.get())
        }
    }

    @Test fun touchDragChangesWeightAndSettlesBeforeConfirmation() {
        val result=AtomicReference<String>()
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {ProfileValueDialog("体重","70.0",{}) {result.set(it)}}}}
            val bounds=Rect();node("体重整数")!!.getBoundsInScreen(bounds)
            val down=SystemClock.uptimeMillis()
            for(step in 0..13) {
                val action=when(step){0->MotionEvent.ACTION_DOWN;13->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE}
                val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY()+bounds.height()*.25f-step.coerceAtMost(12)*bounds.height()*.04f,0)
                inst.uiAutomation.injectInputEvent(event,true);event.recycle();Thread.sleep(30)
            }
            // Allow the platform fling and snap to finish, then use the actual confirmation button.
            Thread.sleep(1200)
            tap(node("完成")!!)
            inst.waitForIdleSync()
            assertNotNull(result.get())
            assertTrue("Finger drag must update the weight",result.get().toDouble()>70.0)
        }
    }

}
