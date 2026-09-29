package me.chile.app

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.*
import me.chile.app.domain.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ChoiceCardTest {
    @Test fun answeringOncePersistsChoicesAndPreservesComposer() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("choice-test-$name",mode)
        }
        val dbName="choice-test.db";app.deleteDatabase(dbName)
        val question=ChatMessage(role="assistant",text="这是新的一餐吗？",options=listOf("新增一餐","修改上一餐"))
        LocalStore(app,dbName).use {store->
            store.addMessage(question)
            assertEquals(question.options,store.messages().single().options)
            assertEquals(question.options,store.messagePage().messages.single().options)
            store.put("pendingPhoto","unsent-draft.jpg")
        }
        val vault=KeyVault(app);vault.save("synthetic-choice-test")
        val http=OkHttpClient.Builder().addInterceptor {chain->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("""{"choices":[{"finish_reason":"stop","message":{"content":"收到选择"}}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val instrumentation=InstrumentationRegistry.getInstrumentation();val owner=ViewModelStore()
        lateinit var vm: AppViewModel
        fun awaitState(check: ()->Boolean) {
            val until=System.nanoTime()+10_000_000_000L
            while(System.nanoTime()<until) {var ready=false;instrumentation.runOnMainSync {ready=check()};if(ready)return;Thread.sleep(20)}
            fail("Choice state did not settle")
        }
        try {
            instrumentation.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),dbName,AiClient(http))}})[AppViewModel::class.java]}
            awaitState {!vm.loading}
            instrumentation.runOnMainSync {
                vm.updateDraft("还没发出的说明")
                vm.answerChoice(question,"新增一餐");vm.answerChoice(question,"新增一餐")
            }
            awaitState {!vm.busy && vm.data.messages.size==3}
            instrumentation.runOnMainSync {
                assertNull(vm.error)
                assertEquals("还没发出的说明",vm.draft)
                assertEquals("unsent-draft.jpg",vm.data.pendingPhoto)
                assertEquals("这是新的一餐吗？\n新增一餐",vm.data.messages.single {it.role=="user"}.text)
                assertTrue(vm.data.meals.isEmpty());assertTrue(vm.data.cards.isEmpty())
                vm.answerChoice(question,"修改上一餐")
                assertFalse(vm.busy)
            }
            LocalStore(app,dbName).use {assertEquals(question.options,it.messagePage().messages.first().options)}
        } finally {instrumentation.runOnMainSync {owner.clear()};vault.clear()}
    }
}
