package me.chile.app

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.chile.app.domain.FoodItem
import me.chile.app.ui.ChileTheme
import me.chile.app.ui.FoodGrid
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardMotionTest {
    @Test fun foodCardPressReturnsToSizeAndScrollDoesNotOpenEditor() {
        val ui=ProfileUiTest()
        val foods=(0..19).map {FoodItem("测试食物$it",100.0,130,"主食")}
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {
                Surface(color=MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {FoodGrid(foods){}}
                }
            }}}
            fun bounds(): Rect {
                var node=ui.node("测试食物0")!!
                while(!node.isClickable && node.parent!=null)node=node.parent
                return Rect().also {node.getBoundsInScreen(it)}
            }
            Thread.sleep(500)
            val before=bounds()
            var down=SystemClock.uptimeMillis()
            fun touch(action: Int,y: Float=before.exactCenterY()) {
                val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,before.exactCenterX(),y,0)
                try {ui.inst.uiAutomation.injectInputEvent(event,true)}finally{event.recycle()}
            }
            touch(MotionEvent.ACTION_DOWN)
            Thread.sleep(350)
            val pressed=bounds()
            touch(MotionEvent.ACTION_CANCEL)
            Thread.sleep(500)
            val released=bounds()
            assertTrue("Actual pressed card must visibly shrink: $before -> $pressed",pressed.width()<=before.width()-3)
            assertTrue("Cancelled press must return to original width",kotlin.math.abs(released.width()-before.width())<=2)
            assertNull("Cancelled press must not edit",ui.find(ui.inst.uiAutomation.rootInActiveWindow,"关闭食物编辑"))
            down=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN)
            for(step in 1..12){touch(MotionEvent.ACTION_MOVE,before.exactCenterY()-step*12f);Thread.sleep(25)}
            touch(MotionEvent.ACTION_UP,before.exactCenterY()-144f)
            Thread.sleep(350)
            assertTrue("The gesture must actually scroll the list",bounds().top<before.top-20)
            assertNull("Vertical scroll must cancel the card click",ui.find(ui.inst.uiAutomation.rootInActiveWindow,"关闭食物编辑"))
        }
    }
}
