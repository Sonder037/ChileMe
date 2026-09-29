package me.chile.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.chile.app.data.LocalStore
import me.chile.app.data.json
import me.chile.app.domain.ChatMessage
import me.chile.app.domain.Meal
import me.chile.app.domain.FoodItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PendingPhotoTest {
    @Test fun failedAttachmentKeepsPreviousPhotoAndRemovesNewCopy() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val root=File(base.cacheDir,"photo-rollback-${java.util.UUID.randomUUID()}").apply {mkdirs()}
        val app=object: Application() {
            init {attachBaseContext(base)}
            override fun getFilesDir()=root
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val database="chile-photo-rollback-test.db";app.deleteDatabase(database)
        val owner=ViewModelStore();lateinit var vm: AppViewModel
        fun settled(check: ()->Boolean) {
            val deadline=System.nanoTime()+5_000_000_000L
            do {var done=false;instrumentation.runOnMainSync {done=check()};if(done)return;Thread.sleep(20)} while(System.nanoTime()<deadline)
            fail("Photo import did not settle")
        }
        try {
            val source=File(root,"source.jpg")
            val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            try {source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}} finally {bitmap.recycle()}
            instrumentation.runOnMainSync {vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]}
            settled {!vm.loading}
            instrumentation.runOnMainSync {vm.attachPhoto(Uri.fromFile(source))}
            settled {!vm.busy}
            val previous=vm.data.pendingPhoto!!
            LocalStore(app,database).use {db->db.writableDatabase.execSQL("CREATE TRIGGER reject_attachment BEFORE INSERT ON kv WHEN NEW.key='pendingPhoto' BEGIN SELECT RAISE(ABORT, 'test attachment failure'); END")}
            instrumentation.runOnMainSync {vm.attachPhoto(Uri.fromFile(source))}
            settled {!vm.busy}
            instrumentation.runOnMainSync {assertNotNull(vm.error);assertEquals(previous,vm.data.pendingPhoto)}
            LocalStore(app,database).use {assertEquals(previous,it.get("pendingPhoto"))}
            assertEquals("Failed import must not leave an orphan image",setOf(previous),File(root,"photos").list()!!.toSet())
            assertTrue(source.exists())
            LocalStore(app,database).use {it.writableDatabase.execSQL("DROP TRIGGER reject_attachment")}
            instrumentation.runOnMainSync {vm.clearError();vm.attachPhoto(Uri.fromFile(source))}
            settled {!vm.busy}
            instrumentation.runOnMainSync {assertNull(vm.error);assertNotNull(vm.data.pendingPhoto);assertNotEquals(previous,vm.data.pendingPhoto)}
            assertEquals(setOf(vm.data.pendingPhoto),File(root,"photos").list()!!.toSet())
        } finally {
            instrumentation.runOnMainSync {owner.clear()};app.deleteDatabase(database);root.deleteRecursively()
        }
    }
    @Test fun removingPendingPhotoDeletesOnlyUnreferencedFile() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val database="chile-pending-photo-test.db"
        app.deleteDatabase(database)
        val owner=ViewModelStore()
        lateinit var vm: AppViewModel
        val files=mutableListOf<File>()
        fun awaitState(check: ()->Boolean) {
            val deadline=System.nanoTime()+5_000_000_000L
            while(System.nanoTime()<deadline) {
                var ready=false
                instrumentation.runOnMainSync {ready=check()}
                if(ready)return
                Thread.sleep(20)
            }
            fail("ViewModel state did not settle")
        }
        try {
            instrumentation.runOnMainSync {
                vm=ViewModelProvider(owner,viewModelFactory {initializer {AppViewModel(app,SavedStateHandle(),database)}})[AppViewModel::class.java]
            }
            awaitState {!vm.loading}
            for(reference in listOf("none","message","meal","card","draft")) {
                val source=File.createTempFile("pending-test-",".jpg",app.cacheDir);files.add(source)
                val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
                try {source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}} finally {bitmap.recycle()}
                instrumentation.runOnMainSync {vm.attachPhoto(Uri.fromFile(source))}
                awaitState {!vm.busy && vm.data.pendingPhoto!=null}
                val name=vm.data.pendingPhoto!!;val imported=vm.photos.file(name);files.add(imported)
                assertTrue(imported.exists())
                LocalStore(app,database).use {db->
                    val meal=Meal(photo=name,items=listOf(FoodItem("米饭",100.0,130)))
                    when(reference) {
                        "message"->db.addMessage(ChatMessage(role="user",text="保留历史照片",photo=name))
                        "meal"->db.saveMeal(meal,false)
                        "card"->db.stageProposal(meal,null)
                        "draft"->db.put("mealDraft",meal.json().toString())
                    }
                }
                instrumentation.runOnMainSync {vm.removePhoto()}
                awaitState {vm.data.pendingPhoto==null}
                assertEquals("Retain photos referenced by $reference",reference!="none",imported.exists())
                instrumentation.runOnMainSync {vm.attachPhoto(Uri.fromFile(source))}
                awaitState {!vm.busy && vm.data.pendingPhoto!=null}
                val replaced=vm.photos.file(vm.data.pendingPhoto!!);files.add(replaced)
                instrumentation.runOnMainSync {vm.attachPhoto(Uri.fromFile(source))}
                awaitState {!vm.busy && vm.data.pendingPhoto!=replaced.name}
                files.add(vm.photos.file(vm.data.pendingPhoto!!))
                assertFalse("Replacing an unreferenced pending image releases its file",replaced.exists())
                instrumentation.runOnMainSync {vm.removePhoto()}
                awaitState {vm.data.pendingPhoto==null}
            }
            val source=File.createTempFile("draft-test-",".jpg",app.cacheDir);files.add(source)
            val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            try {source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}} finally {bitmap.recycle()}
            instrumentation.runOnMainSync {vm.importPhoto(Uri.fromFile(source))}
            awaitState {!vm.busy && vm.data.mealDraft?.photo!=null}
            val first=vm.photos.file(vm.data.mealDraft!!.photo!!);files.add(first)
            instrumentation.runOnMainSync {vm.importPhoto(Uri.fromFile(source))}
            awaitState {!vm.busy && vm.data.mealDraft?.photo!=first.name}
            val second=vm.photos.file(vm.data.mealDraft!!.photo!!);files.add(second)
            instrumentation.runOnMainSync {vm.discardMeal()}
            awaitState {vm.data.mealDraft==null}
            assertFalse("Replacing a draft photo releases its old file",first.exists())
            assertFalse("Discarding a draft releases its unreferenced photo",second.exists())
            instrumentation.runOnMainSync {vm.importPhoto(Uri.fromFile(source))}
            awaitState {!vm.busy && vm.data.mealDraft?.photo!=null}
            val retained=vm.photos.file(vm.data.mealDraft!!.photo!!);files.add(retained)
            LocalStore(app,database).use {db->db.saveMeal(vm.data.mealDraft!!.copy(items=listOf(FoodItem("米饭",100.0,130))),false,clearDraft=false)}
            instrumentation.runOnMainSync {vm.discardMeal()}
            awaitState {vm.data.mealDraft==null}
            assertTrue("Discarding edits must retain the saved meal photo",retained.exists())
        } finally {
            instrumentation.runOnMainSync {owner.clear()}
            files.forEach {it.delete()};app.deleteDatabase(database)
        }
    }
}
