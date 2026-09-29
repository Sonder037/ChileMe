package me.chile.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.chile.app.data.json
import me.chile.app.data.*
import me.chile.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStoreTest {
    @Test fun activityChangesPersistAndStaleProposalsNeverOverwriteEdits() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-activity-changes-test.db";context.deleteDatabase(name)
        val date=java.time.LocalDate.now();val old=Profile("male",30,175.0,70.0,date.minusDays(2).toString(),"light")
        try {
            LocalStore(context,name).use {db->
                db.saveProfile(old)
                db.setDailyActivity(date.minusDays(1).toString(),"high")
                val t=SettingsTools(db.profiles(),db.activityOverrides(),db.editableSettings(),emptyList(),emptyList(),date.atTime(12,0))
                t.execute("query_settings","{}")
                assertEquals("pending_change",t.execute("update_profile","""{"weight":68,"activity":"moderate"}""").getString("status"))
                assertEquals(70.0,db.profiles().first().weight,0.0)
                db.put("change:test",t.proposal!!.json().toString())
                assertEquals(t.proposal,db.pendingChanges()["test"])
                db.transaction {db.applyChange(t.proposal!!)}
                assertEquals(68.0,db.profiles().first().weight,0.0)
                assertEquals("light",profileForDay(db.profiles(),old.date,db.activityOverrides())!!.activity)
                assertEquals("high",profileForDay(db.profiles(),date.minusDays(1).toString(),db.activityOverrides())!!.activity)
                assertEquals("moderate",profileForDay(db.profiles(),date.toString(),db.activityOverrides())!!.activity)
                assertTrue(runCatching {db.transaction {db.applyChange(t.proposal!!)}}.isFailure)
                db.setDailyActivity(date.minusDays(1).toString(),null)
                assertEquals("light",profileForDay(db.profiles(),date.minusDays(1).toString(),db.activityOverrides())!!.activity)
            }
            LocalStore(context,name).use {db->
                assertEquals(68.0,db.profiles().first().weight,0.0)
                assertTrue(db.activityOverrides().isEmpty())
                val meal=Meal(items=listOf(FoodItem("测试餐",null,100)))
                db.saveMeal(meal,false)
                val deletion=PendingChange("meal",meal.id,meal.json().toString(),null,"删除")
                db.saveMeal(meal.copy(title="已经手动修改"),false)
                assertTrue(runCatching {db.transaction {db.applyChange(deletion)}}.isFailure)
                assertEquals("已经手动修改",db.meals().single().title)
            }
        }finally{context.deleteDatabase(name)}
    }
    @Test fun legacyEstimateCanBeEditedInPlaceAndExplicitlyConfirmed() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-unlock-estimate-test.db";context.deleteDatabase(name)
        try {LocalStore(context,name).use {db->
            val before=Meal(items=listOf(FoodItem("米饭",100.0,130)),source="饭前估算 · 尚未食用")
            db.stageProposal(before,null)
            db.confirmProposal(before,true)
            assertEquals(1,db.meals().size)
            assertFalse(db.meals().single().source.contains("尚未食用"))
            val draft=before.copy(id="editable")
            db.stageProposal(draft,null)
            val changed=draft.copy(items=listOf(FoodItem("米饭",150.0,195)))
            db.stageProposal(changed,draft)
            assertEquals(changed,db.cards()[draft.id])
            assertEquals(1,db.meals().size)
            assertThrows(IllegalArgumentException::class.java){db.stageProposal(draft,draft)}
            assertThrows(IllegalArgumentException::class.java){db.confirmProposal(draft,true)}
            assertEquals("Stale confirmation must not overwrite the edited draft",changed,db.cards()[draft.id])
            assertEquals(1,db.meals().size)
            db.confirmProposal(changed,true);db.confirmProposal(changed,true)
            assertEquals(2,db.meals().size)
            assertEquals(195,db.meals().single {it.id==draft.id}.kcal)
            val correction=changed.copy(id="correction",replacesId=changed.id,items=listOf(FoodItem("米饭",120.0,156)))
            db.stageProposal(correction,db.meals().single {it.id==changed.id})
            val revised=correction.copy(items=listOf(FoodItem("米饭",130.0,169)))
            db.stageProposal(revised,correction)
            db.confirmProposal(revised,true)
            assertEquals(2,db.meals().size)
            assertEquals(169,db.meals().single {it.id==changed.id}.kcal)
        }} finally {context.deleteDatabase(name)}
    }
    @Test fun actualRecordsRejectFutureAndOutOfRangeDatesButRetainEstimates() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-record-date-test.db";context.deleteDatabase(name)
        try {LocalStore(context,name).use {db->
            val dates=listOf(java.time.LocalDate.now().plusDays(1).toString(),"1899-12-31","-999999999-01-01")
            val accepted=dates.flatMap {date->
                val meal=Meal(date=date,items=listOf(FoodItem("米饭",100.0,130)))
                db.put("mealDraft",meal.json().toString())
                val mealAccepted=runCatching {db.saveMeal(meal,true)}.isSuccess
                val exercise=Exercise(date=date,time="12:00",name="散步",minutes=20,activeKcal=60,source="设备",messageId=date)
                listOf(mealAccepted,runCatching {db.saveExercise(exercise)}.isSuccess)
            }
            assertEquals("Actual meal/exercise dates must be within supported past dates",List(6){false},accepted)
            assertTrue(db.meals().isEmpty());assertTrue(db.exercises().isEmpty());assertTrue(db.memories().isEmpty())
            assertFalse(db.get("mealDraft").isNullOrBlank())
            val estimate=Meal(date=dates.first(),source="饭前估算 · 尚未食用",items=listOf(FoodItem("米饭",100.0,130)))
            db.stageProposal(estimate,null)
            assertEquals(estimate,db.cards()[estimate.id])
        }} finally {context.deleteDatabase(name)}
    }
    @Test fun largeHistoryKeepsOrderAndOptionalMetadata() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-history-read-test.db";context.deleteDatabase(name)
        try {LocalStore(context,name).use {db->
            val expected=(0 until 1000).map {index->ChatMessage(id="m:$index",role=if(index%2==0)"user" else "assistant",text="消息 $index",
                photo=if(index%3==0)"test-photo.jpg" else null,mealId=if(index%5==0)"meal:$index" else null,
                createdAt=if(index%7==0)null else "2026-09-25T12:00",exerciseId=if(index%11==0)"exercise:$index" else null)}
            db.transaction {expected.forEach(db::addMessage)}
            val start=System.nanoTime()
            assertEquals(expected,db.messages())
            android.util.Log.i("ChileAudit","history1000 read ms="+(System.nanoTime()-start)/1_000_000)
        }} finally {context.deleteDatabase(name)}
    }
    @Test fun failedMealInsertPreservesDraftAndDoesNotReportSuccess() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-write-failure-test.db";context.deleteDatabase(name)
        try {LocalStore(context,name).use {db->
            val meal=Meal(id="failed",items=listOf(FoodItem("米饭",100.0,130)))
            db.put("mealDraft",meal.json().toString())
            db.writableDatabase.execSQL("CREATE TRIGGER reject_meal BEFORE INSERT ON meals BEGIN SELECT RAISE(ABORT, 'test write failure'); END")
            assertTrue("Failed insert must propagate to the UI",runCatching {db.saveMeal(meal,true)}.isFailure)
            assertEquals(meal.json().toString(),db.get("mealDraft"))
            assertTrue(db.meals().isEmpty());assertTrue(db.memories().isEmpty())
        }} finally {context.deleteDatabase(name)}
    }
    @Test fun exercisePersistsIdempotentlyAndMigratesV2WithoutLosingMessages() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-exercise-test.db";context.deleteDatabase(name)
        val old=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null)
        old.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY,value TEXT NOT NULL)")
        old.execSQL("CREATE TABLE meals (id TEXT PRIMARY KEY,body TEXT NOT NULL)")
        old.execSQL("CREATE TABLE messages (seq INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT UNIQUE NOT NULL,role TEXT NOT NULL,body TEXT NOT NULL,photo TEXT,mealId TEXT)")
        old.execSQL("INSERT INTO messages(id,role,body) VALUES ('old','user','保留原对话')")
        old.version=2;old.close()
        val exercise=Exercise(id="exercise:msg",date="2026-09-26",time="18:00",name="快走",minutes=30,activeKcal=138,source="估算",messageId="msg")
        LocalStore(context,name).use {db->
            assertEquals("保留原对话",db.messages().single().text)
            db.transaction {db.saveExercise(exercise);db.addMessage(ChatMessage(role="assistant",text="记下了",exerciseId=exercise.id))}
            db.saveExercise(exercise);assertEquals(1,db.exercises().size)
        }
        LocalStore(context,name).use {db->
            assertEquals(exercise,db.exercises().single());assertEquals(exercise.id,db.messages().last().exerciseId)
            db.saveExercise(exercise.copy(activeKcal=150));assertEquals(150,db.exercises().single().activeKcal)
            db.deleteExercise(exercise.id);assertTrue(db.exercises().isEmpty());assertEquals(2,db.messages().size)
        }
        context.deleteDatabase(name)
    }
    @Test fun beforeAndAfterConfirmationDoesNotDoubleCountAndRetainsBaseline() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-leftovers-test.db";context.deleteDatabase(name)
        LocalStore(context,name).use {db->
            val before=Meal(id="before",items=listOf(FoodItem("米饭",300.0,400)),source="饭前估算 · 尚未食用")
            db.stageProposal(before,null)
            assertTrue(db.meals().isEmpty())
            val after=before.copy(id="after",items=listOf(FoodItem("米饭",225.0,300)),source="饭前减剩饭 · 估算待确认",replacesDraftId=before.id,beforeItems=before.items)
            val firstComparison=after.copy(items=listOf(FoodItem("米饭",150.0,200)))
            db.stageProposal(firstComparison,before)
            db.stageProposal(after,firstComparison)
            db.confirmProposal(after,true);db.confirmProposal(after,true)
            assertEquals(1,db.meals().size);assertEquals(300,db.meals().single().kcal)
            assertEquals(before.items,db.meals().single().beforeItems)
            assertFalse("Confirmed ledger provenance must not claim pending confirmation",db.meals().single().source.contains("待确认"))
            assertFalse(db.cards().getValue(after.id).source.contains("待确认"))
            assertEquals("superseded",db.get("cardState:before"))
            val competing=after.copy(id="competing")
            db.stageProposal(competing,before)
            assertThrows(IllegalArgumentException::class.java){db.confirmProposal(competing,true)}
            assertEquals(300,db.meals().single().kcal)
        }
        context.deleteDatabase(name)
    }

    @Test fun editingProposalIsAtomicIdempotentAndRejectsStaleBaseline() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-tool-edit-test.db";context.deleteDatabase(name)
        LocalStore(context,name).use {db->
            val old=Meal(items=listOf(FoodItem("米饭",100.0,130)))
            db.saveMeal(old,true)
            val proposed=old.copy(id="proposal",replacesId=old.id,items=listOf(FoodItem("米饭",200.0,260)))
            db.stageProposal(proposed,old)
            assertEquals(130,db.meals().single().kcal)
            db.confirmProposal(proposed,true);db.confirmProposal(proposed,true)
            assertEquals(1,db.meals().size);assertEquals(old.id,db.meals().single().id);assertEquals(260,db.meals().single().kcal)
            val stale=proposed.copy(id="stale")
            db.stageProposal(stale,old)
            assertThrows(IllegalArgumentException::class.java){db.confirmProposal(stale,true)}
            assertEquals(260,db.meals().single().kcal)
            db.deleteMeal(old.id);assertEquals("deleted",db.get("cardState:proposal"))
        }
        context.deleteDatabase(name)
    }

    @Test fun migrationPreservesOldMessagesAndNewAttachments() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-migration-test.db";context.deleteDatabase(name)
        val old=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null)
        old.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY,value TEXT NOT NULL)")
        old.execSQL("CREATE TABLE meals (id TEXT PRIMARY KEY,body TEXT NOT NULL)")
        old.execSQL("CREATE TABLE messages (seq INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT UNIQUE NOT NULL,role TEXT NOT NULL,body TEXT NOT NULL)")
        old.execSQL("INSERT INTO messages(id,role,body) VALUES ('old','user','旧消息')")
        old.version=1;old.close()
        LocalStore(context,name).use {db->
            assertEquals("旧消息",db.messages().single().text)
            val message=ChatMessage(role="user",text="新照片",photo="test.jpg",mealId="meal-1")
            db.addMessage(message);assertEquals(message,db.messages().last())
        }
        context.deleteDatabase(name)
    }
    @Test fun recognitionCardConfirmationAndDeletionStayConsistent() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="chile-card-test.db";context.deleteDatabase(name)
        LocalStore(context,name).use {db->
            val meal=Meal(items=listOf(FoodItem("米饭",150.0,195)))
            db.put("card:${meal.id}",meal.json().toString())
            db.put("mealDraft","unrelated-draft");db.saveMeal(meal,true,false);db.saveMeal(meal,true,false)
            assertEquals("unrelated-draft",db.get("mealDraft"))
            assertEquals(1,db.meals().size);assertEquals("confirmed",db.get("cardState:${meal.id}"))
            assertEquals(meal,db.cards()[meal.id]);db.deleteMeal(meal.id)
            assertEquals("deleted",db.get("cardState:${meal.id}"));assertTrue(db.memories().isEmpty())
        }
        context.deleteDatabase(name)
    }

    @Test fun confirmationIsIdempotentAndSurvivesReopen() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("chile-test.db")
        val meal=Meal(items=listOf(FoodItem("米饭",150.0,195)))
        LocalStore(context,"chile-test.db").use {db-> db.saveMeal(meal,true);db.saveMeal(meal,true);assertEquals(1,db.meals().size)}
        LocalStore(context,"chile-test.db").use {db->assertEquals(195,db.meals().single().kcal);assertEquals(1,db.memories().size);db.deleteMeal(meal.id);assertTrue(db.memories().isEmpty())}
    }
    @Test fun forgottenMealMemoryDoesNotReturnAndDisabledEditsInvalidateOldSummary() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("chile-test.db")
        LocalStore(context,"chile-test.db").use {db->
            val meal=Meal(items=listOf(FoodItem("饭",null,200)))
            db.saveMeal(meal,true);db.forget("meal:${meal.id}");db.saveMeal(meal,true)
            assertTrue(db.memories().isEmpty())
            val other=Meal(items=listOf(FoodItem("面",null,300)))
            db.saveMeal(other,true);db.saveMeal(other.copy(items=listOf(FoodItem("面",null,400))),false)
            assertTrue(db.memories().isEmpty())
        }
    }
}
