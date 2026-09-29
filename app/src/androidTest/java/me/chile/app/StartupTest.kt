package me.chile.app

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
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
import me.chile.app.ui.ChileApp
import me.chile.app.ui.ChileTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StartupTest {
    @Test fun loadingShowsNonInteractiveComposerBeforeRecordsArrive() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val reading=CountDownLatch(1);val release=CountDownLatch(1)
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int): SharedPreferences {
                val prefs=super.getSharedPreferences("startup-test-$name",mode)
                return object: SharedPreferences by prefs {
                    override fun contains(key: String): Boolean {
                        if(key=="cipher") {reading.countDown();check(release.await(15,TimeUnit.SECONDS))}
                        return prefs.contains(key)
                    }
                }
            }
        }
        val database="chile-startup-test.db";app.deleteDatabase(database)
        LocalStore(app,database).use {it.put("onboarded","true")}
        val owner=ViewModelStore()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun find(n: AccessibilityNodeInfo?,label: String): AccessibilityNodeInfo? {
            if(n==null)return null
            if(n.contentDescription?.toString()==label)return n
            for(i in 0 until n.childCount)find(n.getChild(i),label)?.let {return it}
            return null
        }
        fun awaitNode(label: String): AccessibilityNodeInfo? {
            repeat(40) {instrumentation.waitForIdleSync();find(instrumentation.uiAutomation.rootInActiveWindow,label)?.let {return it};Thread.sleep(50)}
            return null
        }
        try {
            ActivityScenario.launch<PreviewActivity>(Intent(base,PreviewActivity::class.java)).use {scenario->
                scenario.onActivity {activity->
                    val vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]
                    activity.setContent {ChileTheme {ChileApp(vm)}}
                }
                assertTrue(reading.await(5,TimeUnit.SECONDS))
                val pending=awaitNode("消息输入加载中")
                assertNotNull("Composer shell must render while database snapshot is blocked",pending)
                assertFalse(pending!!.isEditable)
                instrumentation.uiAutomation.takeScreenshot()?.let {bitmap->
                    try {java.io.File(base.filesDir,"render-startup-shell.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
                }
                release.countDown()
                assertNotNull("Real composer must replace loading shell",awaitNode("消息输入"))
            }
        } finally {release.countDown();instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
}
