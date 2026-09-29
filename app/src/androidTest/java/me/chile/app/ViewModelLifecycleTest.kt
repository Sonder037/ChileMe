package me.chile.app

import android.app.Application
import android.system.Os
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ViewModelLifecycleTest {
    @Test fun clearedViewModelReleasesItsDatabaseFileDescriptors() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val database="chile-lifecycle-test.db";app.deleteDatabase(database)
        val owner=ViewModelStore()
        lateinit var vm: AppViewModel
        fun openDatabaseFiles()=File("/proc/self/fd").listFiles().orEmpty().count {fd->
            runCatching {Os.readlink(fd.path).contains(database)}.getOrDefault(false)
        }
        try {
            instrumentation.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]}
            val readyDeadline=System.nanoTime()+5_000_000_000L
            var loading=true
            while(loading && System.nanoTime()<readyDeadline) {
                instrumentation.runOnMainSync {loading=vm.loading};if(loading)Thread.sleep(20)
            }
            assertFalse("Database initialization timed out",loading)
            assertTrue("Fixture must open the database before checking closure",openDatabaseFiles()>0)
            instrumentation.runOnMainSync {owner.clear()}
            val closeDeadline=System.nanoTime()+2_000_000_000L
            while(openDatabaseFiles()>0 && System.nanoTime()<closeDeadline)Thread.sleep(20)
            assertEquals("Clearing the owner must release SQLite handles",0,openDatabaseFiles())
            assertFalse(vm.loading) // Retain the object; closure must not depend on GC.
        } finally {instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
}
