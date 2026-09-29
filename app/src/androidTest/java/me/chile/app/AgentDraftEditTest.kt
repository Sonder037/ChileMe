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
import me.chile.app.domain.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AgentDraftEditTest {
    @Test fun chatCanQueryEditAndConfirmAnExistingPendingCorrection() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("agent-edit-test-$name",mode)
        }
        val database="chile-agent-edit-test.db";app.deleteDatabase(database)
        val vault=KeyVault(app);vault.save("synthetic-local-only")
        val original=Meal(id="original",items=listOf(FoodItem("米饭",100.0,130)))
        val pending=original.copy(id="pending",replacesId=original.id,items=listOf(FoodItem("米饭",200.0,260)))
        LocalStore(app,database).use {db->
            db.saveMeal(original,false);db.stageProposal(pending,original)
            db.addMessage(ChatMessage(role="assistant",text="修改待确认",mealId=pending.id))
        }
        val calls=AtomicInteger()
        fun tool(name: String,args: JSONObject)=JSONObject().put("id",name).put("type","function").put("function",JSONObject().put("name",name).put("arguments",args.toString()))
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val request=okio.Buffer();chain.request().body!!.writeTo(request)
            val history=JSONObject(request.readUtf8()).getJSONArray("messages")
            val call=when(calls.getAndIncrement()) {
                0->tool("query_meals",JSONObject().put("from",original.date).put("to",original.date))
                1->{
                    val result=JSONObject(history.getJSONObject(history.length()-1).getString("content"))
                    val rows=result.getJSONArray("meals")
                    check((0 until rows.length()).any {rows.getJSONObject(it).optString("meal_id")==pending.id}) {"Pending correction absent from query"}
                    tool("edit_meal",JSONObject().put("meal_id",pending.id).put("items",JSONArray().put(JSONObject().put("name","米饭").put("quantity",150).put("unit","g").put("kcal",195).put("category","主食"))))
                }
                else->null
            }
            val message=JSONObject().put("role","assistant").put("content",if(call==null)"改好了，核对卡片。" else "")
            if(call!=null)message.put("tool_calls",JSONArray().put(call))
            val body=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",if(call==null)"stop" else "tool_calls").put("message",message)))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Local fake provider").body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val instrumentation=InstrumentationRegistry.getInstrumentation();val owner=ViewModelStore();lateinit var vm: AppViewModel
        fun awaitState(check: ()->Boolean) {
            val deadline=System.nanoTime()+5000000000L
            do {var done=false;instrumentation.runOnMainSync {done=check()};if(done)return;Thread.sleep(20)} while(System.nanoTime()<deadline)
            fail("Agent state did not settle")
        }
        try {
            instrumentation.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database,AiClient(http))}})[AppViewModel::class.java]}
            awaitState {!vm.loading}
            instrumentation.runOnMainSync {vm.updateDraft("把待确认的米饭改成150克");vm.send()}
            awaitState {!vm.busy}
            instrumentation.runOnMainSync {
                assertNull(vm.error);assertEquals(3,calls.get());assertEquals(195,vm.data.cards.getValue(pending.id).kcal)
                assertEquals(130,vm.data.meals.single().kcal)
                assertEquals(pending.id,vm.data.messages.last().mealId)
                vm.confirmCard(vm.data.cards.getValue(pending.id))
            }
            awaitState {!vm.saving}
            LocalStore(app,database).use {db->assertEquals(1,db.meals().size);assertEquals(original.id,db.meals().single().id);assertEquals(195,db.meals().single().kcal);assertEquals("confirmed",db.get("cardState:${pending.id}"))}
        } finally {
            instrumentation.runOnMainSync {owner.clear()};vault.clear();app.deleteDatabase(database)
            http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()
        }
    }
}
