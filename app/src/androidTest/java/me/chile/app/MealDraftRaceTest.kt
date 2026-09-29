package me.chile.app

import android.app.Application
import android.content.SharedPreferences
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class MealDraftRaceTest {
    @Test fun snapshotAlreadyInFlightCannotRevertNewMealDraftText() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val pause=AtomicBoolean(false);val reading=CountDownLatch(1);val release=CountDownLatch(1)
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getSharedPreferences(name: String,mode: Int): SharedPreferences {
                val prefs=super.getSharedPreferences("meal-race-test-$name",mode)
                return object: SharedPreferences by prefs {
                    override fun contains(key: String): Boolean {
                        if(key=="cipher" && pause.compareAndSet(true,false)) {reading.countDown();check(release.await(5,TimeUnit.SECONDS))}
                        return prefs.contains(key)
                    }
                }
            }
        }
        val database="chile-meal-race-test.db";app.deleteDatabase(database)
        val original=Meal(title="原名称",items=listOf(FoodItem("米饭",100.0,130)))
        val card=original.copy(id="chat-card")
        LocalStore(app,database).use {it.put("mealDraft",original.json().toString());it.stageProposal(card,null)}
        val instrumentation=InstrumentationRegistry.getInstrumentation();val owner=ViewModelStore();lateinit var vm: AppViewModel
        fun awaitState(check: ()->Boolean) {
            val deadline=System.nanoTime()+5_000_000_000L
            do {var ready=false;instrumentation.runOnMainSync {ready=check()};if(ready)return;Thread.sleep(20)} while(System.nanoTime()<deadline)
            fail("Meal draft state did not settle")
        }
        try {
            instrumentation.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]}
            awaitState {!vm.loading}
            pause.set(true)
            instrumentation.runOnMainSync {vm.toggleMemory()}
            assertTrue(reading.await(5,TimeUnit.SECONDS))
            val edited=original.copy(title="刚输入的新名称")
            val editedCard=card.copy(title="刚修改的聊天卡")
            instrumentation.runOnMainSync {vm.updateMeal(edited);vm.updateCard(editedCard)}
            release.countDown()
            awaitState {!vm.data.memoryEnabled}
            // Read completion follows the queued edit; verify both persisted and visible values.
            var stored: Meal?=null
            val deadline=System.nanoTime()+3_000_000_000L
            do {Thread.sleep(20);LocalStore(app,database).use {stored=it.get("mealDraft")?.let(::mealJson)}} while(stored!=edited && System.nanoTime()<deadline)
            assertEquals(edited,stored)
            instrumentation.runOnMainSync {
                assertEquals("Refresh must preserve text edited during its read",edited,vm.data.mealDraft)
                assertEquals("Refresh must preserve edited chat card",editedCard,vm.data.cards[card.id])
                vm.confirmCard(vm.data.cards.getValue(card.id))
            }
            awaitState {!vm.saving && vm.data.cardStates[card.id]=="confirmed"}
            LocalStore(app,database).use {assertEquals(editedCard.title,it.meals().single().title)}
        } finally {release.countDown();instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database)}
    }
}
