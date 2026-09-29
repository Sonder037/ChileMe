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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ChatDraftRaceTest {
    @Test fun sendingRetryingAndCancellingPreserveNextDraftAndMessageOwnership() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        // Separate preference namespace: never read or overwrite a configured provider key.
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("chat-race-test-$name",mode)
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val database="chile-chat-race-test.db";app.deleteDatabase(database)
        val vault=KeyVault(app);vault.save("synthetic-local-test")
        val nextResponse=AtomicReference("normal")
        val held=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val mode=nextResponse.getAndSet("normal")
            if(mode=="hold"){held.countDown();check(release.await(5,TimeUnit.SECONDS))}
            try {Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if(mode=="fail")500 else 200).message("Local test")
                .body("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"${if(mode=="hold")"迟到的旧回复" else "本地测试回复"}"}}]}""".toResponseBody("application/json".toMediaType())).build()
            } finally {if(mode=="hold")returned.countDown()}
        }.build()
        val owner=ViewModelStore();val saved=SavedStateHandle()
        lateinit var vm: AppViewModel
        fun awaitState(check: ()->Boolean) {
            val deadline=System.nanoTime()+5_000_000_000L
            while(System.nanoTime()<deadline) {
                var done=false;instrumentation.runOnMainSync {done=check()}
                if(done)return
                Thread.sleep(20)
            }
            fail("Chat state did not settle")
        }
        try {
            instrumentation.runOnMainSync {
                vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,saved,database,AiClient(http))}})[AppViewModel::class.java]
            }
            awaitState {!vm.loading}
            instrumentation.runOnMainSync {
                vm.updateDraft("第一条消息")
                vm.send()
                vm.updateDraft("正在准备下一条")
            }
            awaitState {!vm.busy && vm.data.messages.any {it.role=="assistant"}}
            instrumentation.runOnMainSync {
                assertNull(vm.error)
                assertEquals("第一条消息",vm.data.messages.single {it.role=="user"}.text)
                assertEquals("正在准备下一条",vm.draft)
                assertEquals("正在准备下一条",saved.get<String>("chatDraft"))
            }
            LocalStore(app,database).use {assertEquals("正在准备下一条",it.get("chatDraft"))}
            instrumentation.runOnMainSync {vm.send()}
            awaitState {!vm.busy && vm.data.messages.count {it.role=="assistant"}==2}
            instrumentation.runOnMainSync {assertEquals("",vm.draft)}
            LocalStore(app,database).use {assertEquals("",it.get("chatDraft"))}
            // A new edit can have identical text; content equality cannot identify ownership.
            instrumentation.runOnMainSync {
                vm.updateDraft("同样一句话")
                vm.send()
                vm.updateDraft("同样一句话")
            }
            awaitState {!vm.busy && vm.data.messages.count {it.role=="assistant"}==3}
            instrumentation.runOnMainSync {assertEquals("同样一句话",vm.draft)}
            LocalStore(app,database).use {assertEquals("同样一句话",it.get("chatDraft"))}
            nextResponse.set("fail")
            instrumentation.runOnMainSync {
                vm.updateDraft("需要重试的消息");vm.send();vm.updateDraft("重试之外的草稿")
            }
            awaitState {!vm.busy && vm.error!=null}
            instrumentation.runOnMainSync {
                assertNotNull(vm.retryMessage)
                assertEquals(4,vm.data.messages.count {it.role=="user"})
                assertEquals("重试之外的草稿",vm.draft)
                vm.send(retry=true)
            }
            awaitState {!vm.busy && vm.data.messages.count {it.role=="assistant"}==4}
            instrumentation.runOnMainSync {
                assertEquals(4,vm.data.messages.count {it.role=="user"})
                assertNull(vm.retryMessage);assertNull(vm.error)
                assertEquals("重试之外的草稿",vm.draft)
            }
            nextResponse.set("hold")
            instrumentation.runOnMainSync {vm.updateDraft("停止这一条");vm.send()}
            assertTrue(held.await(5,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {vm.cancelRequest()}
            awaitState {!vm.busy}
            instrumentation.runOnMainSync {vm.updateDraft("停止后重新发送");vm.send();vm.updateDraft("仍未发送的草稿")}
            release.countDown();assertTrue(returned.await(5,TimeUnit.SECONDS))
            awaitState {!vm.busy && vm.data.messages.count {it.role=="assistant"}==5}
            instrumentation.runOnMainSync {
                assertFalse(vm.data.messages.any {it.text=="迟到的旧回复"})
                assertEquals("",vm.streamingText)
                assertEquals(6,vm.data.messages.count {it.role=="user"})
                assertEquals("仍未发送的草稿",vm.draft)
            }
            LocalStore(app,database).use {assertEquals("仍未发送的草稿",it.get("chatDraft"))}
        } finally {
            release.countDown()
            instrumentation.runOnMainSync {owner.clear()}
            vault.clear();app.deleteDatabase(database)
            http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()
        }
    }
}
