package me.chile.app
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class SettingsChangeUiTest {
 @Test fun confirmationAndCancelUseThePersistedChangeAndRefresh() {
  val inst=InstrumentationRegistry.getInstrumentation();val ui=ProfileUiTest()
  for(confirm in listOf(false,true)) {
   ActivityScenario.launch<PreviewActivity>(Intent(inst.targetContext,PreviewActivity::class.java).putExtra("changes",true)).use {
    assertNotNull(ui.node("确认修改"))
    Thread.sleep(300);ui.screenshot("settings-change")
    LocalStore(inst.targetContext,"chile-render-test.db").use {db->assertEquals(70.0,db.profiles().first().weight,0.0)}
    ui.tap(ui.node(if(confirm)"确认修改" else "取消")!!)
    assertNotNull(ui.node(if(confirm)"已修改" else "已取消"))
    LocalStore(inst.targetContext,"chile-render-test.db").use {db->
     assertEquals(if(confirm)65.0 else 70.0,db.profiles().first().weight,0.0)
     assertEquals(if(confirm)"moderate" else null,db.profiles().first().activity)
    }
   }
  }
 }
}
