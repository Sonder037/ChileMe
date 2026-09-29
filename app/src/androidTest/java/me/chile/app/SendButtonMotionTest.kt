package me.chile.app

import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SendButtonMotionTest {
    @Test fun sendButtonGrowsAroundItsCenterWithoutHorizontalClipping() {
        val ui=ProfileUiTest()
        fun shell(command: String)=ParcelFileDescriptor.AutoCloseInputStream(ui.inst.uiAutomation.executeShellCommand(command)).use {it.readBytes().toString(Charsets.UTF_8).trim()}
        val original=shell("settings get global animator_duration_scale")
        try {
            shell("settings put global animator_duration_scale 5")
            ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {
                assertNotNull(ui.node("消息输入"));Thread.sleep(500)
                val camera=Rect();ui.node("拍照或选择图片")!!.getBoundsInScreen(camera)
                val density=ui.inst.targetContext.resources.displayMetrics.density
                val screenWidth=ui.inst.targetContext.resources.displayMetrics.widthPixels
                val region=Rect(screenWidth-(78*density).toInt(),camera.top,screenWidth-(30*density).toInt(),camera.bottom)
                fun visibleCircle(): Rect? {
                    val bitmap=ui.inst.uiAutomation.takeScreenshot()
                    try {
                        var left=region.right;var right=region.left;var top=region.bottom;var bottom=region.top
                        for(y in region.top until region.bottom)for(x in region.left until region.right) {
                            val color=bitmap.getPixel(x,y)
                            if(android.graphics.Color.blue(color)-android.graphics.Color.red(color)>45 && android.graphics.Color.red(color)<180) {
                                left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y)
                            }
                        }
                        return if(right>left && bottom>top)Rect(left,top,right+1,bottom+1) else null
                    }finally{bitmap.recycle()}
                }
                it.onActivity {activity->androidx.lifecycle.ViewModelProvider(activity)[AppViewModel::class.java].updateDraft("午饭吃了米饭")}
                val samples=mutableListOf<Rect>()
                val until=SystemClock.uptimeMillis()+1800
                while(SystemClock.uptimeMillis()<until) {
                    visibleCircle()?.let {bounds->if(bounds.width()>8 && bounds.height()>8)samples.add(bounds)}
                    Thread.sleep(30)
                }
                val final=visibleCircle()!!
                val middle=samples.filter {it.width()<final.width()*.9f}
                assertTrue("Capture actual intermediate growth frames: $samples",middle.size>=2)
                for(frame in middle) {
                    assertTrue("Button must grow as a circle, not a horizontal reveal: $frame",kotlin.math.abs(frame.width()-frame.height())<=4)
                    assertTrue("Button center must stay fixed: $frame -> $final",kotlin.math.abs(frame.centerX()-final.centerX())<=3)
                }
                ui.screenshot("send-center-grown")
            }
        } finally {
            if(original.toFloatOrNull()!=null)shell("settings put global animator_duration_scale $original")
            else shell("settings delete global animator_duration_scale")
        }
    }
}
