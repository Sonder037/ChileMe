package me.chile.app

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.ui.rememberToday
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CurrentDateTest {
    @Test fun openScreenRefreshesTimeAcrossMidnight() {
        val now=AtomicReference(Instant.parse("2026-09-30T23:59:30Z"))
        val shown=AtomicReference<LocalDateTime>()
        val clock=object: Clock() {
            override fun instant()=now.get()
            override fun getZone(): ZoneId=ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock=Clock.fixed(instant(),zone)
        }
        val inst=InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {shown.set(me.chile.app.ui.rememberCurrentTime(clock))}}
            val initialDeadline=System.nanoTime()+3_000_000_000L
            do {Thread.sleep(30);inst.waitForIdleSync()}while(shown.get()==null && System.nanoTime()<initialDeadline)
            assertEquals("2026-09-30T23:59:30",shown.get()?.toString())
            now.set(Instant.parse("2026-10-01T00:00:30Z"))
            val deadline=System.nanoTime()+65_000_000_000L
            do {Thread.sleep(200)}while(shown.get()?.toString()!="2026-10-01T00:00:30" && System.nanoTime()<deadline)
            assertEquals("Foreground minute timer must refresh without activity restart","2026-10-01T00:00:30",shown.get()?.toString())
        }
    }
    @Test fun returnFromBackgroundRefreshesDateWithoutChangingDeviceClock() {
        val now=AtomicReference(Instant.parse("2026-09-26T23:59:00Z"))
        val shown=AtomicReference<LocalDate>()
        val clock=object: Clock() {
            override fun instant()=now.get()
            override fun getZone(): ZoneId=ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock=Clock.fixed(instant(),zone)
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun awaitDate(expected: String) {
            val deadline=System.nanoTime()+3000000000L
            do {Thread.sleep(30);instrumentation.waitForIdleSync()} while(shown.get()?.toString()!=expected && System.nanoTime()<deadline)
            assertEquals(expected,shown.get()?.toString())
        }
        ActivityScenario.launch<PreviewActivity>(Intent(instrumentation.targetContext,PreviewActivity::class.java)).use {scenario->
            scenario.onActivity {it.setContent {shown.set(rememberToday(clock))}}
            awaitDate("2026-09-26")
            scenario.moveToState(Lifecycle.State.CREATED)
            now.set(Instant.parse("2026-09-27T00:01:00Z"))
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitDate("2026-09-27")
        }
    }
}
