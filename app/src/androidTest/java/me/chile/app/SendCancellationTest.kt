package me.chile.app

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.*
import okhttp3.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SendCancellationTest {
    @Test fun cancelWhileLocalWriteIsBlockedKeepsDatabaseAndRetryInSync()=verifyBlockedWrite(null)
    @Test fun cancelWhileLocalWriteIsBlockedPreservesNewerDraft()=verifyBlockedWrite("下一条尚未发送")
    private fun verifyBlockedWrite(nextDraft: String?) {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("cancel-write-$name",mode)
        }
        val database="cancel-write-test.db";app.deleteDatabase(database)
        val vault=KeyVault(app);vault.save("isolated-test-only")
        val http=OkHttpClient.Builder().addInterceptor {throw java.io.IOException("local test, no network")}.build()
        val inst=InstrumentationRegistry.getInstrumentation();val owner=ViewModelStore();lateinit var vm: AppViewModel
        fun awaitState(condition: ()->Boolean) {
            val deadline=System.nanoTime()+10_000_000_000L
            do {var ready=false;inst.runOnMainSync {ready=condition()};if(ready)return;Thread.sleep(20)}while(System.nanoTime()<deadline)
            fail("Cancellation state did not settle")
        }
        try {
            inst.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database,AiClient(http))}})[AppViewModel::class.java]}
            awaitState {!vm.loading}
            inst.runOnMainSync {vm.updateDraft("正在提交的一餐")}
            val savedDeadline=System.nanoTime()+3_000_000_000L
            while(LocalStore(app,database).use {it.get("chatDraft")}!="正在提交的一餐" && System.nanoTime()<savedDeadline)Thread.sleep(20)
            LocalStore(app,database).use {guard->
                val db=guard.writableDatabase
                db.beginTransaction()
                try {
                    inst.runOnMainSync {vm.send()}
                    Thread.sleep(300)
                    inst.runOnMainSync {vm.cancelRequest();if(nextDraft!=null)vm.updateDraft(nextDraft)}
                }finally {db.endTransaction()}
            }
            awaitState {!vm.busy}
            LocalStore(app,database).use {db->
                val messages=db.messages()
                inst.runOnMainSync {
                    if(messages.isNotEmpty()) {
                        assertEquals("Committed message must be visible after cancellation",messages.map {it.id},vm.data.messages.map {it.id})
                        assertEquals("Committed message must retain retry",messages.last().id,vm.retryMessage?.id)
                        assertEquals("Only the committed input is cleared; newer draft survives",nextDraft.orEmpty(),vm.draft)
                    } else assertEquals("Uncommitted draft must survive cancellation",nextDraft?:"正在提交的一餐",vm.draft)
                }
            }
        } finally {
            inst.runOnMainSync {owner.clear()};vault.clear();app.deleteDatabase(database)
            http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()
        }
    }
}
