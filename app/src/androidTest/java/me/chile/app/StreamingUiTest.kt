package me.chile.app

import android.app.Application
import android.content.Intent
import android.os.*
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.*
import me.chile.app.ui.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StreamingUiTest {
    @Test fun longReplyAppearsIncrementallyAndFinishesAtLatestText() = runStream(false)
    @Test fun readingEarlierTextIsNotPulledDownByNewChunks() = runStream(true)
    private fun runStream(readEarlier: Boolean) {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("stream-ui-$name",mode)
        }
        val db="stream-ui-test.db";app.deleteDatabase(db)
        LocalStore(app,db).use {it.put("onboarded","true")}
        val vault=KeyVault(app);vault.save("local-fixture-only")
        val paragraphs=InstrumentationRegistry.getArguments().getString("streamParagraphs")?.toIntOrNull()?.coerceIn(180,1200)?:180
        val expected=(1..paragraphs).joinToString("\n\n") {"- **第 $it 项**：这是本地模拟的长回复，用于检查流式排版和滚动。"}+"\n\n**流式结束标记**"
        fun frame(text: String)= "data: "+JSONObject().put("choices",JSONArray().put(JSONObject().put("delta",JSONObject().put("content",text)))).toString()+"\n\n"
        val paused=java.util.concurrent.atomic.AtomicBoolean(false)
        val frames=expected.chunked(60).map(::frame)+listOf("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n","data: [DONE]\n\n")
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val stream=object: Source {
                var index=0;val buffer=Buffer()
                override fun read(sink: Buffer,byteCount: Long): Long {
                    while(paused.get())Thread.sleep(10)
                    if(buffer.size==0L) {if(index==frames.size)return -1;Thread.sleep(50);buffer.writeUtf8(frames[index++])}
                    return buffer.read(sink,byteCount)
                }
                override fun timeout()=Timeout.NONE
                override fun close(){}
            }.buffer()
            val body=object: ResponseBody() {
                override fun contentType()="text/event-stream".toMediaType()
                override fun contentLength()=-1L
                override fun source()=stream
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("local stream").body(body).build()
        }.build()
        val inst=InstrumentationRegistry.getInstrumentation();val owner=ViewModelStore();val ui=ProfileUiTest()
        val durations=mutableListOf<Long>();val layoutTimes=mutableListOf<Long>();val drawTimes=mutableListOf<Long>();var window: Window?=null
        val listener=Window.OnFrameMetricsAvailableListener {_,metrics,_->
            durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
            layoutTimes.add(metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION));drawTimes.add(metrics.getMetric(FrameMetrics.DRAW_DURATION))
        }
        lateinit var vm: AppViewModel
        fun awaitState(condition: ()->Boolean) {
            val deadline=SystemClock.uptimeMillis()+maxOf(20000L,expected.length*50L/60+10000)
            do {var ready=false;inst.runOnMainSync {ready=condition()};if(ready)return;Thread.sleep(20)}while(SystemClock.uptimeMillis()<deadline)
            fail("Streaming state did not settle")
        }
        try {
            ActivityScenario.launch<PreviewActivity>(Intent(base,PreviewActivity::class.java)).use {scenario->
                scenario.onActivity {activity->
                    vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),db,AiClient(http))}})[AppViewModel::class.java]
                    activity.setContent {ChileTheme {ChileApp(vm)}}
                    window=activity.window;window!!.addOnFrameMetricsAvailableListener(listener,Handler(Looper.getMainLooper()))
                }
                awaitState {!vm.loading}
                inst.runOnMainSync {vm.updateDraft("演示一段长回复")}
                ui.tap(ui.node("发送")!!)
                awaitState {vm.streamingText.isNotBlank()}
                inst.runOnMainSync {assertTrue(vm.busy);assertTrue(vm.streamingText.length<expected.length);assertTrue(vm.data.messages.none {it.role=="assistant"})}
                var assertReadingStill: (() -> Unit)?=null
                if(readEarlier) {
                    awaitState {vm.streamingText.length>=2400}
                    paused.set(true);Thread.sleep(200)
                    val screen=inst.uiAutomation.takeScreenshot()
                    val width=screen.width;val height=screen.height;screen.recycle()
                    val down=SystemClock.uptimeMillis()
                    for(step in 0..20) {
                        val action=when(step){0->android.view.MotionEvent.ACTION_DOWN;20->android.view.MotionEvent.ACTION_UP;else->android.view.MotionEvent.ACTION_MOVE}
                        val y=height*(.30f+.025f*step.coerceAtMost(19))
                        val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,width*.5f,y,0)
                        inst.uiAutomation.injectInputEvent(event,true);event.recycle();Thread.sleep(30)
                    }
                    Thread.sleep(600)
                    fun readingPixels(): IntArray {
                        val bitmap=inst.uiAutomation.takeScreenshot()
                        val w=width*3/4;val h=height/5
                        val pixels=IntArray(w*h);bitmap.getPixels(pixels,0,w,width/8,height/3,w,h);bitmap.recycle()
                        return pixels
                    }
                    val before=readingPixels()
                    assertReadingStill={
                        val after=readingPixels()
                        val changed=before.indices.count {before[it]!=after[it]}
                        assertTrue("Reply completion moved the reading position: $changed pixels",changed<before.size/100)
                    }
                    paused.set(false);Thread.sleep(700)
                    val after=readingPixels()
                    val changed=before.indices.count {before[it]!=after[it]}
                    assertTrue("New stream chunks moved the reading position: $changed pixels",changed<before.size/100)
                }
                awaitState {!vm.busy}
                inst.runOnMainSync {assertNull(vm.error);assertEquals(expected,vm.data.messages.last().text);assertEquals("",vm.streamingText)}
                if(!readEarlier) {
                    fun hasEnd(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                        if(node==null)return false
                        return node.text?.contains("流式结束标记")==true || (0 until node.childCount).any {hasEnd(node.getChild(it))}
                    }
                    val renderedDeadline=SystemClock.uptimeMillis()+6000
                    do {
                        if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache()
                        if(hasEnd(inst.uiAutomation.rootInActiveWindow))break
                        Thread.sleep(100)
                    } while(SystemClock.uptimeMillis()<renderedDeadline)
                    ui.screenshot("stream-bottom")
                    assertTrue("Final formatted reply must remain rendered",hasEnd(inst.uiAutomation.rootInActiveWindow))
                    var time=""
                    inst.runOnMainSync {time=chatTimeLabel(vm.data.messages.last().createdAt,java.time.LocalDate.now())!!}
                    val timeBounds=android.graphics.Rect();val inputBounds=android.graphics.Rect()
                    ui.node(time)!!.getBoundsInScreen(timeBounds)
                    ui.node("消息输入")!!.getBoundsInScreen(inputBounds)
                    assertTrue("The end of the reply must be above the composer",timeBounds.top>0 && timeBounds.bottom<=inputBounds.top)
                }
                Thread.sleep(300)
                assertReadingStill?.invoke()
                ui.screenshot("stream-final")
                val parseStart=System.nanoTime();repeat(30){markdownText(expected)}
                val parseMs=(System.nanoTime()-parseStart)/30/1_000_000.0
                inst.runOnMainSync {
                    val ordered=durations.filter {it>0}.sorted()
                    fun p95(times: List<Long>)=times.sorted().let {it[(it.size-1)*95/100]/1_000_000.0}
                    val metrics="${expected.length} chars; ${ordered.size} frames; p95 ${p95(ordered)}ms; max ${ordered.last()/1_000_000.0}ms; layout p95 ${p95(layoutTimes)}ms; draw p95 ${p95(drawTimes)}ms; parse mean ${parseMs}ms"
                    File(base.filesDir,"stream-ui-metrics.txt").writeText(metrics)
                    inst.sendStatus(0,Bundle().apply {putString("stream_metrics",metrics)})
                }
            }
        } finally {
            paused.set(false)
            inst.runOnMainSync {window?.removeOnFrameMetricsAvailableListener(listener);owner.clear()}
            vault.clear();app.deleteDatabase(db);http.dispatcher.executorService.shutdown();http.connectionPool.evictAll()
        }
    }
}
