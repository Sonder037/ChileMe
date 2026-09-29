package me.chile.app

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.chile.app.domain.*
import me.chile.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ActivityPresentationTest {
    @Test fun composerHasNoVoiceControlOrMicrophonePermission() {
        val ui=ProfileUiTest()
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {
            assertNotNull(ui.node("消息输入"))
            assertNull(ui.find(ui.inst.uiAutomation.rootInActiveWindow,"语音输入"))
            val permissions=ui.inst.targetContext.packageManager.getPackageInfo("me.chile.app",android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertFalse("The app must no longer request microphone access",permissions.contains("android.permission.RECORD_AUDIO"))
        }
    }
    @Test fun activityCombinesClockReferenceAndExtraRecordsWithoutWalkingGoal() {
        val ui=ProfileUiTest();val today=LocalDate.now().toString()
        val profile=Profile("male",30,175.0,70.0,today,"sedentary")
        val extra=Exercise(date=today,time="08:00",name="散步",minutes=20,activeKcal=60,source="manual",messageId="fixture")
        ActivityScenario.launch<PreviewActivity>(Intent(ui.inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {ChileTheme {DailyEnergyCards(emptyList(),listOf(extra),listOf(profile))}}}
            assertNotNull(ui.node("活动"))
            val now=LocalTime.now()
            assertTrue((-1L..1L).any {ui.find(ui.inst.uiAutomation.rootInActiveWindow,"${dailyActivityReference(profile,now.plusMinutes(it))!!+60}")!=null})
            assertNull(ui.find(ui.inst.uiAutomation.rootInActiveWindow,"快走 30 分钟参考"))
        }
    }
}
