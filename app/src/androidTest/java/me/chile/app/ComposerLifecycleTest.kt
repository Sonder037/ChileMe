package me.chile.app

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposerLifecycleTest {
    @Test fun hintsPauseInBackgroundAndResumeInForeground() {
        val ui=ProfileUiTest()
        val hints=setOf("发张外卖截图，帮你记餐","拍下这一餐，核对后记录","左滑查看数据","右滑查看记录","记一下刚才的运动","今天按久坐少动计算","把体重改为 65 公斤","看看这七天的进度","这份外卖大概多少热量？","说说今天吃了什么")
        fun find(node: AccessibilityNodeInfo?): String? {
            if(node==null)return null
            node.text?.toString()?.takeIf {it in hints}?.let {return it}
            for(i in 0 until node.childCount)find(node.getChild(i))?.let {return it}
            return null
        }
        fun visibleHint(): String {
            repeat(30) {
                ui.inst.waitForIdleSync()
                if(android.os.Build.VERSION.SDK_INT>=33)ui.inst.uiAutomation.clearCache()
                find(ui.inst.uiAutomation.rootInActiveWindow)?.let {return it}
                Thread.sleep(50)
            }
            error("Input hint is not visible")
        }
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {scenario->
            assertNotNull(ui.node("消息输入"))
            val before=visibleHint()
            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(6000)
            scenario.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(350) // Allow the visual crossfade to finish before reading its label.
            assertEquals("Background time must not advance the rotating hint",before,visibleHint())
            Thread.sleep(5500)
            assertNotEquals("Hints must resume rotating in the foreground",before,visibleHint())
        }
    }
}
