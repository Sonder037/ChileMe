package me.chile.app

import android.content.*
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.chile.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownSelectionTest {
    @Test fun longReplyCanSelectAndCopyAcrossLayoutSections() {
        val ui=ProfileUiTest();val inst=ui.inst
        val source=(1..30).joinToString("\n") {"**第 $it 项**：保留完整的中文内容并跨过排版分段，检查文本选择和复制。"}
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {
                val density=LocalDensity.current
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalDensity provides Density(density.density,.5f)) {MarkdownText(source)}
                }
            }}}
            Thread.sleep(700)
            if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache()
            ui.screenshot("markdown-before-selection")
            fun findText(node: android.view.accessibility.AccessibilityNodeInfo?,text: String): android.view.accessibility.AccessibilityNodeInfo? {
                if(node==null)return null
                if(node.text?.contains(text)==true)return node
                for(i in 0 until node.childCount)findText(node.getChild(i),text)?.let {return it}
                return null
            }
            val bounds=android.graphics.Rect()
            findText(inst.uiAutomation.rootInActiveWindow,"第 1 项")!!.getBoundsInScreen(bounds)
            val end=android.graphics.Rect()
            findText(inst.uiAutomation.rootInActiveWindow,"第 30 项")!!.getBoundsInScreen(end)
            val density=inst.targetContext.resources.displayMetrics.density
            val x=bounds.left+2*density;val y=bounds.top+5*density
            val down=SystemClock.uptimeMillis()
            fun event(action: Int,px: Float=x,py: Float=y) {val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,px,py,0);try {inst.uiAutomation.injectInputEvent(e,true)}finally{e.recycle()}}
            event(MotionEvent.ACTION_DOWN);Thread.sleep(800)
            for(step in 1..20) {event(MotionEvent.ACTION_MOVE,x+(end.right-2*density-x)*step/20,y+(end.bottom-5*density-y)*step/20);Thread.sleep(30)}
            event(MotionEvent.ACTION_UP,end.right-2*density,end.bottom-5*density)
            Thread.sleep(300);ui.screenshot("markdown-selection")
            val info=inst.uiAutomation.serviceInfo;val previousFlags=info.flags
            try {
                info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                inst.uiAutomation.serviceInfo=info
                var copy: android.view.accessibility.AccessibilityNodeInfo?=null
                val deadline=SystemClock.uptimeMillis()+5000
                do {
                    if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache()
                    copy=inst.uiAutomation.windows.firstNotNullOfOrNull {ui.find(it.root,"Copy")?:ui.find(it.root,"复制")}
                    if(copy==null)Thread.sleep(100)
                } while(copy==null && SystemClock.uptimeMillis()<deadline)
                ui.tap(copy?:error("Selection must offer copy"))
            } finally {info.flags=previousFlags;inst.uiAutomation.serviceInfo=info}
            var copied:String?=null
            scenario.onActivity {copied=(it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()}
            assertNotNull(copied)
            assertTrue("Selection must cross the 800-character layout boundary",copied!!.length>800)
            assertTrue("Copied text must preserve the selected source exactly",markdownText(source).text.contains(copied!!))
        }
    }
}
