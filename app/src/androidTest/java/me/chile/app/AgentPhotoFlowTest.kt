package me.chile.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.*
import me.chile.app.domain.*
import me.chile.app.ui.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AgentPhotoFlowTest {
    @Test fun photoToConfirmationThenExerciseKeepsOneMealAndOriginalImage() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val root=File(base.cacheDir,"agent-photo-flow-${java.util.UUID.randomUUID()}").apply {mkdirs()}
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("agent-photo-flow-$name",mode)
            override fun getFilesDir()=root
        }
        val dbName="agent-photo-flow-test.db";app.deleteDatabase(dbName)
        val vault=KeyVault(app);vault.save("synthetic-local-only")
        val profile=Profile("male",30,175.0,70.0,activity="sedentary")
        LocalStore(app,dbName).use {it.put("onboarded","true");it.saveProfile(profile)}
        val calls=AtomicInteger()
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val buffer=okio.Buffer();chain.request().body!!.writeTo(buffer)
            val history=JSONObject(buffer.readUtf8()).getJSONArray("messages")
            val index=calls.getAndIncrement()
            if(index==0) {
                val latest=history.getJSONObject(history.length()-1).getJSONArray("content")
                check(latest.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
            }
            val name=when(index){0->"record_meal";2->"record_exercise";else->null}
            val args=if(index==0)"""{"items":[{"name":"纯牛奶","quantity":250,"unit":"ml","kcal":155,"category":"奶类","nutrients":{"protein_g":8,"carbs_g":12,"fat_g":8}}]}""" else """{"name":"快走","minutes":30}"""
            val message=JSONObject().put("role","assistant").put("content",if(name==null)"可以，核对一下卡片。" else "")
            if(name!=null)message.put("tool_calls",JSONArray().put(JSONObject().put("id","fixture-$index").put("type","function")
                .put("function",JSONObject().put("name",name).put("arguments",args))))
            val body=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",if(name==null)"stop" else "tool_calls").put("message",message)))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("local fixture").body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val owner=ViewModelStore();val inst=InstrumentationRegistry.getInstrumentation();val ui=ProfileUiTest()
        lateinit var vm: AppViewModel
        fun awaitState(condition: ()->Boolean) {
            val deadline=System.nanoTime()+8_000_000_000L
            do {var ready=false;inst.runOnMainSync {ready=condition()};if(ready)return;Thread.sleep(30)}while(System.nanoTime()<deadline)
            fail("Photo/agent state did not settle")
        }
        try {
            val source=File(root,"source.jpg")
            Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).let {bitmap->
                try {source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}} finally {bitmap.recycle()}
            }
            ActivityScenario.launch<PreviewActivity>(Intent(base,PreviewActivity::class.java)).use {scenario->
                scenario.onActivity {activity->
                    vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),dbName,AiClient(http))}})[AppViewModel::class.java]
                    activity.setContent {ChileTheme {ChileApp(vm)}}
                }
                awaitState {!vm.loading}
                inst.runOnMainSync {vm.attachPhoto(Uri.fromFile(source))}
                awaitState {!vm.busy && vm.data.pendingPhoto!=null}
                val photo=vm.data.pendingPhoto!!
                assertNotNull(ui.node("移除待发送照片"))
                inst.runOnMainSync {vm.updateDraft("新增这份外卖")}
                ui.tap(ui.node("发送")!!)
                awaitState {!vm.busy && vm.data.cards.size==1}
                inst.runOnMainSync {
                    assertNull(vm.error);assertTrue(vm.data.meals.isEmpty())
                    assertNull(vm.data.pendingPhoto)
                    assertEquals(photo,vm.data.messages.first {it.role=="user"}.photo)
                    assertEquals(photo,vm.data.cards.values.single().photo)
                }
                ui.tap(ui.node("确认记录")!!)
                awaitState {!vm.saving && vm.data.meals.size==1}
                val meal=vm.data.meals.single()
                // A second confirmation must be idempotent, including after the first write completes.
                inst.runOnMainSync {vm.confirmCard(vm.data.cards.getValue(meal.id))}
                awaitState {!vm.saving}
                inst.runOnMainSync {vm.updateDraft("刚快走30分钟")}
                ui.tap(ui.node("发送")!!)
                awaitState {!vm.busy && vm.data.exercises.size==1}
                inst.runOnMainSync {
                    assertNull(vm.error);assertEquals(4,calls.get())
                    assertEquals(1,vm.data.meals.size);assertEquals(155,vm.data.meals.single().kcal)
                    assertEquals(140,vm.data.exercises.single().activeKcal)
                    val day=energyProgress(vm.data.meals,vm.data.exercises,vm.data.profiles,LocalDate.now(),LocalDate.now()).days.single()
                    assertEquals(155-profile.totalEnergy()!!,day.balance)
                    assertTrue(vm.photos.file(photo).exists())
                }
                LocalStore(app,dbName).use {db->assertEquals(1,db.meals().size);assertEquals(1,db.exercises().size);assertEquals(4,db.messages().size)}
            }
        } finally {
            inst.runOnMainSync {owner.clear()};vault.clear();app.deleteDatabase(dbName);root.deleteRecursively()
            http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()
        }
    }
}
