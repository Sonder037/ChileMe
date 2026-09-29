package me.chile.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WindowResizeTest {
    @Test fun rotatingRetainsDraftAndKeepsCameraControlsReachable() {
        val ui=ProfileUiTest()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.inst.uiAutomation.executeShellCommand("pm grant me.chile.app android.permission.CAMERA")).use {it.readBytes()}
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {scenario->
            assertNotNull(ui.node("消息输入"))
            assertTrue(ui.setText(ui.inst.uiAutomation.rootInActiveWindow,"保留这段还没发送的文字"))
            scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            repeat(40) {
                var landscape=false
                scenario.onActivity {landscape=it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
                if(!landscape)Thread.sleep(50)
            }
            assertNotNull("Rotation must retain the unsent draft",ui.node("保留这段还没发送的文字"))
            assertTrue(ui.setText(ui.inst.uiAutomation.rootInActiveWindow,""))
            Thread.sleep(400)
            fun inside(label: String) {
                val found=ui.node(label);assertNotNull("Missing control after rotation: $label",found)
                val bounds=Rect();found!!.getBoundsInScreen(bounds)
                val image=ui.inst.uiAutomation.takeScreenshot()
                try {assertTrue("$label is outside the rotated viewport: $bounds",bounds.width()>0 && bounds.height()>0 && bounds.left>=0 && bounds.top>=0 && bounds.right<=image.width && bounds.bottom<=image.height)}finally{image.recycle()}
            }
            inside("消息输入");inside("拍照或选择图片")
            ui.tap(ui.node("拍照或选择图片")!!)
            val readyDeadline=android.os.SystemClock.uptimeMillis()+20000
            while(ui.node("拍摄照片")?.isEnabled!=true && android.os.SystemClock.uptimeMillis()<readyDeadline)Thread.sleep(100)
            assertTrue("Camera must bind after rotation",ui.node("拍摄照片")!!.isEnabled)
            ui.screenshot("camera-landscape-before-check")
            inside("关闭拍照");inside("拍摄照片");inside("从相册选择图片")
            ui.tap(ui.node("相机预览")!!)
            assertNotNull("Focus must work in the resized preview",ui.node("对焦位置"))
            ui.screenshot("camera-landscape")
            ui.tap(ui.node("关闭拍照")!!)
            scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            assertNotNull(ui.node("消息输入"))
        }
    }
}
