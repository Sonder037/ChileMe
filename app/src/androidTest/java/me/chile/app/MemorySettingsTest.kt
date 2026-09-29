package me.chile.app

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.LocalStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemorySettingsTest {
    @Test fun memorySharingChoiceSurvivesRecreationAndRemainsIndependentOfRecording() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val database="chile-memory-settings-test.db";app.deleteDatabase(database)
        val owner=ViewModelStore();lateinit var vm: AppViewModel
        fun awaitState(check: ()->Boolean) {
            val deadline=System.nanoTime()+3_000_000_000L
            do {var done=false;instrumentation.runOnMainSync {done=check()};if(done)return;Thread.sleep(20)} while(System.nanoTime()<deadline)
            fail("Memory preference did not settle")
        }
        fun recreate() {
            instrumentation.runOnMainSync {
                owner.clear()
                vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]
            }
            awaitState {!vm.loading}
        }
        try {
            recreate()
            instrumentation.runOnMainSync {assertFalse(vm.includeMemory);vm.setIncludeMemory(true)}
            awaitState {vm.includeMemory}
            recreate()
            instrumentation.runOnMainSync {assertTrue("Sharing preference must survive restart",vm.includeMemory);vm.toggleMemory()}
            awaitState {!vm.data.memoryEnabled}
            instrumentation.runOnMainSync {assertTrue(vm.includeMemory);vm.setIncludeMemory(false)}
            awaitState {!vm.includeMemory}
            recreate()
            instrumentation.runOnMainSync {assertFalse(vm.includeMemory);assertFalse(vm.data.memoryEnabled)}
            LocalStore(app,database).use {assertEquals("false",it.get("includeMemory"))}
        } finally {instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
}
