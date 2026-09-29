package me.chile.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import me.chile.app.ui.ChileTheme
import me.chile.app.ui.RecentActivityCard
import me.chile.app.domain.Exercise
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RenderTest {
    @Test fun renderLargeTextChatCard() {
        val inst=InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java).putExtra("longTitles",true)).use {scenario->
            scenario.onActivity {activity->
                val vm=androidx.lifecycle.ViewModelProvider(activity)[AppViewModel::class.java]
                activity.setContent {
                    val density=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)) {
                        ChileTheme {Box(Modifier.safeDrawingPadding().padding(16.dp)) {me.chile.app.ui.ChatScreen(vm,0.dp)}}
                    }
                }
            }
            Thread.sleep(1500);inst.waitForIdleSync()
            ProfileUiTest().screenshot("chat-card-large-text")
            val ui=ProfileUiTest()
            val foodBounds=android.graphics.Rect();val amountBounds=android.graphics.Rect()
            ui.node("香煎鸡胸肉配西兰花和番茄")!!.getBoundsInScreen(foodBounds)
            ui.node("10000 g · 10000 kcal")!!.getBoundsInScreen(amountBounds)
            org.junit.Assert.assertTrue("Large text must place portions below the food name instead of squeezing it: $foodBounds / $amountBounds",amountBounds.top>=foodBounds.bottom)
        }
    }
    @Test fun renderLauncherBowl() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        context.getDrawable(R.drawable.ic_launcher_foreground)!!.apply {setBounds(0,0,432,432);draw(canvas)}
        File(context.filesDir,"render-logo.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        if(android.os.Build.VERSION.SDK_INT>=33) {
            val icon=context.getDrawable(R.drawable.ic_launcher) as android.graphics.drawable.AdaptiveIconDrawable
            val monochrome=checkNotNull(icon.monochrome){"System themed icons need a monochrome layer"}
            val themed=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888)
            val themedCanvas=Canvas(themed);themedCanvas.drawColor(android.graphics.Color.rgb(232,234,255))
            monochrome.mutate().apply {setTint(android.graphics.Color.rgb(57,64,130));setBounds(0,0,432,432);draw(themedCanvas)}
            File(context.filesDir,"render-logo-themed.png").outputStream().use {themed.compress(Bitmap.CompressFormat.PNG,100,it)}
            themed.recycle()
        }
    }
    @Test fun renderClarificationChoices() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val intent=Intent(instrumentation.targetContext,PreviewActivity::class.java).putExtra("choices",true)
        ActivityScenario.launch<PreviewActivity>(intent).use {scenario->
            Thread.sleep(1200);instrumentation.waitForIdleSync()
            scenario.onActivity {activity->
                val view=activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(activity.filesDir,"render-choices.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
    }
    @Test fun renderSyntheticChatAndCalendar() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        for(page in listOf("chat","records","calendar","long-chat","large-activity","large-energy")) {
            val intent=Intent(instrumentation.targetContext,PreviewActivity::class.java)
                .putExtra("monthly",page=="calendar").putExtra("records",page=="records").putExtra("longTitles",page=="long-chat")
            ActivityScenario.launch<PreviewActivity>(intent).use {scenario->
                if(page=="large-activity" || page=="large-energy")scenario.onActivity {activity->activity.setContent {
                    val density=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {ChileTheme {
                        if(page=="large-energy")Box(Modifier.padding(16.dp)) {
                            me.chile.app.ui.DailyEnergyCards(listOf(me.chile.app.domain.Meal(items=listOf(me.chile.app.domain.FoodItem("测试餐",null,1800)))),
                                listOf(Exercise(date=java.time.LocalDate.now().toString(),time="12:00",name="快走",minutes=30,activeKcal=140,source="合成演示",messageId="energy-demo")),
                                listOf(me.chile.app.domain.Profile("male",30,175.0,70.0,activity="sedentary")))
                        } else Box(Modifier.padding(16.dp)) {RecentActivityCard((0..6).map {day->Exercise(date=java.time.LocalDate.now().minusDays(day.toLong()).toString(),time="12:00",name="测试运动",minutes=1440,activeKcal=10000,source="合成边界值",messageId="metric-$day")})}
                    }}
                }}
                // Give asynchronous database loading and a draw frame time to complete.
                Thread.sleep(1200);instrumentation.waitForIdleSync()
                scenario.onActivity {activity->
                    val view=activity.window.decorView
                    val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val file=File(activity.filesDir,"render-$page.png")
                    file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                }
                if(page=="records" || page=="large-energy") {
                    val ui=ProfileUiTest()
                    ui.tap(ui.node("能量图口径")!!)
                    checkNotNull(ui.node("能量口径"))
                    Thread.sleep(800)
                    ui.screenshot("$page-help")
                    ui.tap(ui.node("知道了")!!)
                }
            }
        }
    }
}
