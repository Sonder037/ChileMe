package me.chile.app

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.chile.app.data.PhotoStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoStoreTest {
    @Test fun contentUriImportMakesPrivateCopyAndLeavesGalleryOriginal() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"chile-content-import-test.jpg")
            put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChileAudit")
            put(MediaStore.Images.Media.IS_PENDING,1)
        })!!
        val store=PhotoStore(context)
        var imported: File?=null
        var deleted=false
        try {
            val bitmap=Bitmap.createBitmap(1800,900,Bitmap.Config.ARGB_8888)
            try {resolver.openOutputStream(uri)!!.use {assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it))}} finally {bitmap.recycle()}
            resolver.update(uri,ContentValues().apply {put(MediaStore.Images.Media.IS_PENDING,0)},null,null)
            imported=store.file(store.import(uri))
            assertTrue(imported.exists())
            resolver.openInputStream(uri)!!.use {assertTrue(it.read()!=-1)}
            resolver.delete(uri,null,null)
            deleted=true
            val preview=store.preview(imported.name,200)
            assertNotNull("Private copy must survive loss of gallery URI",preview)
            try {assertEquals(200,preview!!.width);assertEquals(100,preview.height)} finally {preview?.recycle()}
        } finally {imported?.delete();if(!deleted)resolver.delete(uri,null,null)}
    }
    @Test fun importReleasesOwnedCaptureButPreservesOtherSourcesAndFailedCaptures() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val camera=File(context.cacheDir,"camera").apply {mkdirs()}
        val captured=File.createTempFile("capture-",".jpg",camera)
        val external=File.createTempFile("gallery-test-",".jpg",context.cacheDir)
        val broken=File.createTempFile("capture-",".jpg",camera).apply {writeText("not an image")}
        val imported=mutableListOf<File>()
        try {
            val bitmap=Bitmap.createBitmap(2000,1000,Bitmap.Config.ARGB_8888)
            try {for(file in listOf(captured,external))file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it))}} finally {bitmap.recycle()}
            val store=PhotoStore(context)
            imported.add(store.file(store.import(Uri.fromFile(captured))))
            assertFalse("Compressed import must release its owned camera original",captured.exists())
            val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeFile(imported.first().path,options)
            assertEquals(1600,options.outWidth);assertEquals(800,options.outHeight)
            val preview=store.preview(imported.first().name,400)
            assertNotNull(preview)
            try {
                assertEquals(400,preview!!.width);assertEquals(200,preview.height)
                assertTrue("Thumbnail allocation must stay below a full-resolution image",preview.allocationByteCount<=400*200*4)
            } finally {preview?.recycle()}
            BitmapFactory.decodeFile(imported.first().path,options)
            assertEquals("Preview must not resize the stored AI image",1600,options.outWidth)
            assertNull(store.preview("../outside.jpg"))
            assertNull(store.preview("00000000-0000-0000-0000-000000000000.jpg"))
            imported.add(store.file(store.import(Uri.fromFile(external))))
            assertTrue("Gallery sources are never deleted",external.exists())
            assertTrue(runCatching {store.import(Uri.fromFile(broken))}.isFailure)
            assertTrue("Keep the original when import fails",broken.exists())
        } finally {
            (imported+listOf(captured,external,broken)).forEach {it.delete()}
        }
    }
}
