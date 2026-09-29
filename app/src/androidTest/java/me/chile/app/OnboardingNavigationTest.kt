package me.chile.app

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingNavigationTest {
    private val ui=ProfileUiTest()
    private fun swipe(left: Boolean) {
        val bounds=android.graphics.Rect()
        ui.inst.uiAutomation.rootInActiveWindow.getBoundsInScreen(bounds)
        val start=if(left).85f else .15f
        val finish=1f-start
        val down=SystemClock.uptimeMillis()
        for(i in 0..12) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),when(i){0->MotionEvent.ACTION_DOWN;12->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE},
                bounds.width()*(start+(finish-start)*i/12),bounds.height()*.28f,0)
            ui.inst.uiAutomation.injectInputEvent(event,true);event.recycle();Thread.sleep(25)
        }
        Thread.sleep(700)
    }
    @Test fun settingsOnlyAppearsOnRightDashboard() {
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {
            assertNotNull(ui.node("刚快走了30分钟"))
            swipe(false)
            assertNotNull(ui.node("饮食记录"))
            assertNull(ui.find(ui.inst.uiAutomation.rootInActiveWindow,"设置"))
            swipe(true)
            swipe(true)
            assertNotNull(ui.node("热量看板"))
            ui.tap(ui.node("设置")!!)
            assertNotNull(ui.node("身体资料"))
        }
    }
}
