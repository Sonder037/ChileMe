package me.chile.app

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.chile.app.data.*
import me.chile.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MealPagingTest {
    @Test fun recentWindowThenTwelveAtATimeAndEditsStayConsistent() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val inst=InstrumentationRegistry.getInstrumentation()
        val name="meal-paging-test.db"
        app.deleteDatabase(name)
        val today=LocalDate.now()
        LocalStore(app,name).use {db->db.transaction {
            repeat(40) {i->db.saveMeal(Meal(id="page-%03d".format(i),date=today.minusDays(i.toLong()).toString(),items=listOf(FoodItem("测试餐$i",100.0,130))),false)}
        }}
        val owner=androidx.lifecycle.ViewModelStore()
        lateinit var vm: AppViewModel
        fun await(check: ()->Boolean) {
            repeat(200) {
                var done=false;inst.runOnMainSync {done=check()}
                if(done)return
                Thread.sleep(25)
            }
            fail("Meal page did not finish loading")
        }
        try {
            inst.runOnMainSync {vm=androidx.lifecycle.ViewModelProvider(owner,object: androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T: androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AppViewModel(app,SavedStateHandle(),name) as T
                }
            })[AppViewModel::class.java]}
            await {!vm.loading}
            inst.runOnMainSync {
                assertEquals(3,vm.data.mealRecords.size)
                assertEquals(40,vm.data.mealCount)
                vm.loadOlderMeals();vm.loadOlderMeals()
            }
            await {!vm.loadingOlderMeals && vm.data.mealRecords.size==15}
            inst.runOnMainSync {vm.loadOlderMeals()}
            await {!vm.loadingOlderMeals && vm.data.mealRecords.size==27}
            inst.runOnMainSync {vm.deleteMeal(vm.data.mealRecords[5])}
            await {vm.data.mealCount==39}
            inst.runOnMainSync {
                assertEquals(27,vm.data.mealRecords.size)
                assertEquals(27,vm.data.mealRecords.map {it.id}.distinct().size)
                assertFalse(vm.data.mealRecords.any {it.id=="page-005"})
            }
        }finally {inst.runOnMainSync {owner.clear()};app.deleteDatabase(name)}
    }

    @Test fun upgradesExistingMealsAndLimitsBusyDays() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val name="meal-paging-migration.db";app.deleteDatabase(name)
        val old=object: SQLiteOpenHelper(app,name,null,4) {
            override fun onCreate(db: SQLiteDatabase) {db.execSQL("CREATE TABLE meals (id TEXT PRIMARY KEY, body TEXT NOT NULL)")}
            override fun onUpgrade(db: SQLiteDatabase,from: Int,to: Int) {}
        }
        old.use {helper->
            repeat(25) {i->
                val meal=Meal(id="same-%03d".format(i),time="12:00",items=listOf(FoodItem("午餐",100.0,130)))
                helper.writableDatabase.insertOrThrow("meals",null,ContentValues().apply {put("id",meal.id);put("body",meal.json().toString())})
            }
        }
        try {LocalStore(app,name).use {db->
            assertEquals(25,db.mealCount())
            val first=db.mealRecords(12,LocalDate.now().minusDays(2).toString())
            assertEquals(12,first.size)
            assertEquals("same-024",first.first().id)
            assertEquals(first,db.mealRecords(24).take(12))
            assertEquals(25,db.mealRecords(36).size)
        }}finally {app.deleteDatabase(name)}
    }
}
