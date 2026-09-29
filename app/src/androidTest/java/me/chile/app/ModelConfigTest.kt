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
import me.chile.app.data.*
import me.chile.app.domain.ModelConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelConfigTest {
    @Test fun invalidReplacementKeepsWorkingKeyAndDestinationChangesNeverReuseIt() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("config-test-$name",mode)
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val database="chile-config-test.db";app.deleteDatabase(database)
        val vault=KeyVault(app);vault.save("synthetic-original")
        val owner=ViewModelStore();lateinit var vm: AppViewModel
        fun settled(check: ()->Boolean) {
            val deadline=System.nanoTime()+5_000_000_000L
            do {var done=false;instrumentation.runOnMainSync {done=check()};if(done)return;Thread.sleep(20)} while(System.nanoTime()<deadline)
            fail("Configuration did not settle")
        }
        try {
            instrumentation.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]}
            settled {!vm.loading}
            val original=ModelConfig()
            val destination=original.copy(baseUrl="https://example.com/v1")
            for(invalid in listOf("invalid\nkey","invalid\rkey","invalid\tkey","invalid钥")) {
                var completed=false
                instrumentation.runOnMainSync {vm.clearError();vm.saveConfig(destination,invalid){completed=true}}
                settled {!vm.saving}
                assertEquals("synthetic-original",vault.read())
                instrumentation.runOnMainSync {assertFalse(completed);assertNotNull(vm.error);assertFalse(vm.error!!.contains(invalid));assertEquals(original,vm.data.config)}
            }
            instrumentation.runOnMainSync {vm.saveConfig(original.copy(chatModel="different-model"),""){}}
            settled {!vm.saving}
            assertEquals("synthetic-original",vault.read())
            instrumentation.runOnMainSync {vm.saveConfig(destination,""){}}
            settled {!vm.saving}
            assertNull(vault.read())
            instrumentation.runOnMainSync {assertFalse(vm.data.hasKey);assertEquals(destination,vm.data.config)}
            instrumentation.runOnMainSync {vm.saveConfig(destination,"synthetic-replacement"){}}
            settled {!vm.saving}
            assertEquals("synthetic-replacement",vault.read())
            LocalStore(app,database).use {assertEquals(destination,configJson(it.get("config")!!))}
            LocalStore(app,database).use {it.writableDatabase.execSQL("CREATE TRIGGER reject_config BEFORE INSERT ON kv WHEN NEW.key='config' BEGIN SELECT RAISE(ABORT, 'test config failure'); END")}
            val nextDestination=destination.copy(baseUrl="https://next.example/v1")
            for(replacement in listOf("synthetic-next","")) {
                var completed=false
                instrumentation.runOnMainSync {vm.clearError();vm.saveConfig(nextDestination,replacement){completed=true}}
                settled {!vm.saving}
                assertEquals("Database failure must retain the original key", "synthetic-replacement",vault.read())
                LocalStore(app,database).use {assertEquals(destination,configJson(it.get("config")!!))}
                instrumentation.runOnMainSync {assertFalse(completed);assertNotNull(vm.error);assertEquals(destination,vm.data.config)}
            }
            LocalStore(app,database).use {it.writableDatabase.execSQL("DROP TRIGGER reject_config")}
            instrumentation.runOnMainSync {vm.clearError();vm.saveConfig(nextDestination,"synthetic-next"){}}
            settled {!vm.saving}
            assertEquals("synthetic-next",vault.read())
            instrumentation.runOnMainSync {assertNull(vm.error);assertEquals(nextDestination,vm.data.config)}
            instrumentation.runOnMainSync {vm.clearKey()}
            settled {!vm.data.hasKey}
            assertNull(vault.read())
            LocalStore(app,database).use {it.writableDatabase.execSQL("CREATE TRIGGER reject_first_config BEFORE INSERT ON kv WHEN NEW.key='config' BEGIN SELECT RAISE(ABORT, 'test first config failure'); END")}
            instrumentation.runOnMainSync {vm.clearError();vm.saveConfig(original,"synthetic-first"){}}
            settled {!vm.saving}
            assertNull("Failed initial save must leave no key",vault.read())
            instrumentation.runOnMainSync {assertNotNull(vm.error);assertFalse(vm.data.hasKey);assertEquals(nextDestination,vm.data.config)}
        } finally {
            instrumentation.runOnMainSync {owner.clear()}
            vault.clear();app.deleteDatabase(database)
        }
    }
}
