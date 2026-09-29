package me.chile.app

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileNavigationTest {
    @Test fun reopeningProfileReadsLatestSavedData() {
        val ui=ProfileUiTest()
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {scenario->
            assertNotNull(ui.node("这是我的午餐，帮我记一下。"))
            val down=SystemClock.uptimeMillis()
            fun touch(action: Int,x: Float) {
                val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,500f,0)
                try {ui.inst.uiAutomation.injectInputEvent(event,true)}finally {event.recycle()}
            }
            touch(MotionEvent.ACTION_DOWN,900f)
            for(step in 1..12){touch(MotionEvent.ACTION_MOVE,900f-step*60f);Thread.sleep(25)}
            touch(MotionEvent.ACTION_UP,180f)
            ui.tap(ui.node("设置")!!)
            ui.tap(ui.node("身体资料")!!)
            assertNotNull(ui.node("70"))
            ui.tap(ui.node("调整体重")!!)
            assertTrue(ui.advance(ui.node("体重整数")))
            assertNotNull(ui.node("71 kg"))
            ui.tap(ui.node("完成")!!)
            ui.tap(ui.node("帮助")!!)
            ui.tap(ui.node("返回")!!)
            assertNotNull("Opening help must preserve the unsaved form",ui.node("71"))
            ui.tap(ui.node("返回")!!)
            val saved=java.util.concurrent.atomic.AtomicBoolean(false)
            scenario.onActivity {activity->
                val vm=ViewModelProvider(activity)[AppViewModel::class.java]
                vm.saveProfile(vm.data.profiles.first().copy(weight=65.0)){saved.set(true)}
            }
            val deadline=SystemClock.uptimeMillis()+5000
            while(!saved.get() && SystemClock.uptimeMillis()<deadline)Thread.sleep(25)
            assertTrue(saved.get())
            ui.tap(ui.node("身体资料")!!)
            assertNotNull("A new profile visit must load the latest stored weight",ui.node("65"))
        }
    }
}
