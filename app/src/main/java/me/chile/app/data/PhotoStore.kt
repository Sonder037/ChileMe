package me.chile.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID

class PhotoStore(private val context: Context) {
    private val directory by lazy {File(context.filesDir,"photos").apply { mkdirs() }}
    fun preview(name: String,maxEdge: Int=800): Bitmap? {
        require(maxEdge in 1..1600)
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file(name))) {decoder,info,_->
                val ratio=maxEdge.toDouble()/maxOf(info.size.width,info.size.height,maxEdge)
                decoder.setTargetSize((info.size.width*ratio).toInt().coerceAtLeast(1),(info.size.height*ratio).toInt().coerceAtLeast(1))
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }
    fun import(uri: Uri): String {
        val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
            require(info.size.width.toLong()*info.size.height <= 120_000_000) { "图片尺寸过大" }
            val ratio=1600.0/maxOf(info.size.width,info.size.height).coerceAtLeast(1600)
            decoder.setTargetSize((info.size.width*ratio).toInt().coerceAtLeast(1),(info.size.height*ratio).toInt().coerceAtLeast(1))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        // ImageDecoder applies orientation; re-encoding removes EXIF/location metadata.
        val file=File(directory,"${UUID.randomUUID()}.jpg")
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)) }; require(file.length()<=4*1024*1024) { "图片超过 4MB" } }
        catch(e: Exception) { file.delete();throw e } finally { bitmap.recycle() }
        // Only release originals created by CameraSheet after the private copy is complete.
        if(uri.scheme=="file")runCatching {
            val source=File(requireNotNull(uri.path)).canonicalFile
            if(source.parentFile==File(context.cacheDir,"camera").canonicalFile && source.name.startsWith("capture-") && source.extension=="jpg")source.delete()
        }
        return file.name
    }
    fun file(name: String): File { require(name.matches(Regex("[a-f0-9-]+\\.jpg")));return File(directory,name) }
}
