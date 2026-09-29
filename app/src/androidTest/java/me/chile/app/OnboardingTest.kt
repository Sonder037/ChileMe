package me.chile.app

import android.app.Application
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.LocalStore
import me.chile.app.domain.Profile
import me.chile.app.ui.ChileApp
import me.chile.app.ui.ChileTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @Test fun helpReturnsToSetupAndLocalStartKeepsProfile() = verifySetup(false)
    @Test fun renderSetupWithKeyboardAndLargeText() = verifySetup(true)
    @Test fun profileDraftStartedYesterdayTakesEffectOnSaveDay() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val inst=InstrumentationRegistry.getInstrumentation();val owner=ViewModelStore()
        val database="profile-save-day-test.db";app.deleteDatabase(database)
        val today=java.time.LocalDate.now();val yesterday=today.minusDays(1).toString()
        val old=Profile("male",30,175.0,70.0,yesterday,"light")
        LocalStore(app,database).use {it.saveProfile(old)}
        lateinit var vm: AppViewModel
        val complete=java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            inst.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]}
            val deadline=android.os.SystemClock.uptimeMillis()+5000
            do {var loaded=false;inst.runOnMainSync {loaded=!vm.loading};if(loaded)break;Thread.sleep(25)}while(android.os.SystemClock.uptimeMillis()<deadline)
            inst.runOnMainSync {assertFalse(vm.loading);vm.saveProfile(old.copy(weight=65.0)){complete.set(true)}}
            while(!complete.get() && android.os.SystemClock.uptimeMillis()<deadline)Thread.sleep(25)
            assertTrue("Profile save must finish",complete.get())
            LocalStore(app,database).use {db->
                assertEquals(70.0,me.chile.app.domain.profileOn(db.profiles(),yesterday)!!.weight,0.0)
                val saved=me.chile.app.domain.profileOn(db.profiles(),today.toString())!!
                assertEquals(today.toString(),saved.date);assertEquals(65.0,saved.weight,0.0)
            }
        }finally{inst.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
    private fun verifySetup(large: Boolean) {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val automation=instrumentation.uiAutomation
        val database="chile-onboarding-test.db";app.deleteDatabase(database)
        val owner=ViewModelStore();lateinit var vm: AppViewModel
        fun find(node: AccessibilityNodeInfo?,label: String): AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.text?.toString()==label || node.contentDescription?.toString()==label)return node
            for(i in 0 until node.childCount)find(node.getChild(i),label)?.let {return it}
            return null
        }
        fun node(label: String): AccessibilityNodeInfo {
            val deadline=System.nanoTime()+5_000_000_000L
            do {
                instrumentation.waitForIdleSync()
                if(android.os.Build.VERSION.SDK_INT>=33)automation.clearCache()
                find(automation.rootInActiveWindow,label)?.let {return it}
                Thread.sleep(100)
            } while(System.nanoTime()<deadline)
            error("Missing onboarding control: $label")
        }
        fun click(label: String) {
            val deadline=System.nanoTime()+5_000_000_000L
            do {
                instrumentation.waitForIdleSync()
                if(android.os.Build.VERSION.SDK_INT>=33)automation.clearCache()
                var current: AccessibilityNodeInfo?=find(automation.rootInActiveWindow,label)
                while(current!=null && !current.isClickable)current=current.parent
                if(current?.isEnabled==true && current.performAction(AccessibilityNodeInfo.ACTION_CLICK))return
                Thread.sleep(50)
            } while(System.nanoTime()<deadline)
            fail("Control must be clickable: $label")
        }
        try {
            ActivityScenario.launch<PreviewActivity>(Intent(app,PreviewActivity::class.java)).use {scenario->
                scenario.onActivity {activity->
                    vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]
                    activity.setContent {
                        val density=LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.5f else density.fontScale)) {ChileTheme {ChileApp(vm)}}
                    }
                }
                fun captureInput(label: String,file: String,action: String) {
                    click(label)
                    val deadline=System.nanoTime()+5_000_000_000L;var shown=false
                    do {
                        scenario.onActivity {shown=ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true}
                        if(!shown)Thread.sleep(100)
                    } while(!shown && System.nanoTime()<deadline)
                    assertTrue("Keyboard must be open: $label",shown)
                    Thread.sleep(500);instrumentation.waitForIdleSync()
                    val bitmap=automation.takeScreenshot()!!
                    try {java.io.File(app.filesDir,file).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
                    fun scrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                        if(n==null)return null
                        if(n.isScrollable)return n
                        for(i in 0 until n.childCount)scrollable(n.getChild(i))?.let {return it}
                        return null
                    }
                    var reachable=false
                    for(attempt in 0..8) {
                        var control=find(automation.rootInActiveWindow,action)
                        while(control!=null && !control.isClickable)control=control.parent
                        if(control!=null && control.isVisibleToUser) {
                            val bounds=android.graphics.Rect();control.getBoundsInScreen(bounds)
                            var keyboardTop=0
                            scenario.onActivity {
                                val view=it.window.decorView;val location=IntArray(2);view.getLocationOnScreen(location)
                                keyboardTop=location[1]+view.height-(ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?:0)
                            }
                            if(bounds.bottom<=keyboardTop && bounds.height()>0){reachable=true;break}
                        }
                        scrollable(automation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        Thread.sleep(350);instrumentation.waitForIdleSync()
                    }
                    assertTrue("Primary action must be reachable above the keyboard: $action",reachable)
                    val actionImage=automation.takeScreenshot()!!
                    try {java.io.File(app.filesDir,file.replace(".png","-action.png")).outputStream().use {actionImage.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {actionImage.recycle()}
                }
                node("身体数据");click("帮助");node("使用说明");click("返回");node("身体数据")
                if(large) {
                    click("男")
                    for(label in listOf("年龄整数","身高整数","体重整数")) {
                        val wheel=node(label)
                        assertNotNull("Wheel must expose its selected integer",wheel.stateDescription)
                        var ime=false
                        scenario.onActivity {ime=ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true}
                        assertFalse("Wheel selection should not summon the keyboard",ime)
                        assertTrue("Continue must not wait for inertia",node("继续").isEnabled)
                        if(label=="年龄整数") {
                            val image=automation.takeScreenshot()!!
                            try {java.io.File(app.filesDir,"render-setup-profile-wheel-large.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {image.recycle()}
                        }
                        click("继续")
                    }
                    click("久坐少动 · 1.2×");click("连接 AI")
                } else instrumentation.runOnMainSync {vm.saveProfile(Profile("unspecified",28,170.0,65.0)){}}
                node("连接 AI")
                if(large){captureInput("API Key","render-setup-model-keyboard.png","保存配置");return@use}
                click("稍后连接，先本地记录");node("消息输入")
                instrumentation.runOnMainSync {assertTrue(vm.data.onboarded);assertEquals(65.0,vm.data.profiles.single().weight,0.01)}
                LocalStore(app,database).use {assertEquals("true",it.get("onboarded"));assertEquals(1,it.profiles().size)}
            }
        } finally {instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
}
