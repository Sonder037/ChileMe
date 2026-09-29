package me.chile.app

import android.content.Intent
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaInputTest {
    private val ui=ProfileUiTest()
    private fun grant(permission: String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.inst.uiAutomation.executeShellCommand("pm grant me.chile.app android.permission.$permission")).use {it.readBytes()}}
    @Test fun composerShadowExtendsPastBothRoundedEdges() {
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {
            val camera=Rect();ui.node("拍照或选择图片")!!.getBoundsInScreen(camera)
            val density=ui.inst.targetContext.resources.displayMetrics.density
            val inset=(6*density).toInt()
            val left=camera.left-inset
            Thread.sleep(500)
            val bitmap=ui.inst.uiAutomation.takeScreenshot()
            try {
                val right=bitmap.width-left
                fun brightness(x: Int,y: Int): Int {val c=bitmap.getPixel(x,y);return android.graphics.Color.red(c)+android.graphics.Color.green(c)+android.graphics.Color.blue(c)}
                for((edge,direction) in listOf(left to -1,right to 1)) {
                    val outside=edge+direction*2
                    val background=edge+direction*(12*density).toInt()
                    val falloff=(0..(8*density).toInt()).maxOf {dy->brightness(background,camera.centerY()+dy)-brightness(outside,camera.centerY()+dy)}
                    assertTrue("Shadow must fade outside edge $edge; a clipped vertical edge has no falloff ($falloff)",falloff>=3)
                }
            }finally{bitmap.recycle()}
        }
    }
    @Test fun cameraIsLeftAndVoiceIsAbsent() {
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {
            val input=Rect();ui.node("消息输入")!!.getBoundsInScreen(input)
            val camera=Rect();ui.node("拍照或选择图片")!!.getBoundsInScreen(camera)
            assertTrue(camera.centerX()<input.centerX())
            assertNull(ui.find(ui.inst.uiAutomation.rootInActiveWindow,"语音输入"))
        }
    }
    @Test fun cameraOpensPreviewWithGalleryAndTouchFocus() {
        grant("CAMERA")
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {
            ui.tap(ui.node("拍照或选择图片")!!)
            assertNotNull("Camera button must open the preview directly",ui.node("拍摄照片"))
            assertNotNull(ui.node("从相册选择图片"))
            // The headless AVD can spend ten seconds validating its missing front camera.
            repeat(150) { if(ui.node("拍摄照片")?.apply {refresh()}?.isEnabled!=true)Thread.sleep(100) }
            ui.screenshot("camera-open")
            assertTrue("Preview must bind the camera",ui.node("拍摄照片")!!.isEnabled)
            val flash=ui.node("闪光灯关闭")!!
            if(flash.isEnabled) {
                ui.tap(flash)
                assertNotNull(ui.node("闪光灯自动"))
                ui.tap(ui.node("闪光灯自动")!!)
                assertNotNull(ui.node("闪光灯开启"))
                ui.tap(ui.node("闪光灯开启")!!)
                assertNotNull(ui.node("闪光灯关闭"))
            }
            ui.tap(ui.node("相机预览")!!)
            assertNotNull(ui.node("对焦位置"))
            ui.screenshot("camera-focus")
            ui.tap(ui.node("拍摄照片")!!)
            assertNotNull(ui.node("消息输入"))
            assertNotNull("Captured photo must remain a draft until sent",ui.node("移除待发送照片"))
        }
    }

    @Test fun cameraReopensAfterRepeatedCloseAndBackground() {
        grant("CAMERA")
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {scenario->
            fun open() {
                ui.tap(ui.node("拍照或选择图片")!!)
                val deadline=System.nanoTime()+20_000_000_000L
                while(ui.node("拍摄照片")?.apply {refresh()}?.isEnabled!=true && System.nanoTime()<deadline)Thread.sleep(100)
                assertTrue("Camera must rebind after close/background",ui.node("拍摄照片")!!.isEnabled)
            }
            open();ui.tap(ui.node("关闭拍照")!!);Thread.sleep(500)
            val before=java.io.File("/proc/self/fd").list()!!.size
            val arguments=androidx.test.platform.app.InstrumentationRegistry.getArguments()
            val cycles=arguments.getString("cameraCycles")?.toIntOrNull()?.coerceIn(8,100)?:8
            val hold=arguments.getString("cameraHoldMs")?.toLongOrNull()?.coerceIn(0,30_000)?:0L
            repeat(cycles) {i->
                open()
                if(hold>0)Thread.sleep(hold)
                if(i%3==0) {
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                }
                ui.tap(ui.node("关闭拍照")!!)
                if(cycles>8 && (i+1)%10==0)ui.inst.sendStatus(0,android.os.Bundle().apply {
                    putString("camera_cycles","${i+1}/$cycles; fd=${java.io.File("/proc/self/fd").list()!!.size}; baseline=$before; nativeHeap=${android.os.Debug.getNativeHeapAllocatedSize()}")
                })
            }
            Thread.sleep(700)
            val after=java.io.File("/proc/self/fd").list()!!.size
            assertTrue("Camera descriptors should not grow each cycle: $before -> $after",after-before<16)
            open();ui.tap(ui.node("拍摄照片")!!)
            assertNotNull("Camera must still capture after repeated lifecycle changes",ui.node("移除待发送照片"))
        }
    }

    @Test fun typingHidesMediaButtonsAndExpandsTheEditor() {
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {
            val before=Rect();ui.node("消息输入")!!.getBoundsInScreen(before)
            assertTrue(ui.setText(ui.inst.uiAutomation.rootInActiveWindow,"今天想吃一份清淡的午餐"))
            assertNotNull(ui.node("今天想吃一份清淡的午餐"))
            Thread.sleep(350)
            val after=Rect();ui.node("消息输入")!!.getBoundsInScreen(after)
            assertTrue("Typing must keep the available editor width",after.width()>=before.width()-2)
            assertNull(ui.find(ui.inst.uiAutomation.rootInActiveWindow,"拍照或选择图片"))
            assertNull(ui.find(ui.inst.uiAutomation.rootInActiveWindow,"语音输入"))
            assertNotNull(ui.node("发送"))
            ui.screenshot("composer-typing")
            assertTrue(ui.setText(ui.inst.uiAutomation.rootInActiveWindow,""))
            assertNotNull(ui.node("拍照或选择图片"))
            assertNull(ui.find(ui.inst.uiAutomation.rootInActiveWindow,"语音输入"))
        }
    }

}
