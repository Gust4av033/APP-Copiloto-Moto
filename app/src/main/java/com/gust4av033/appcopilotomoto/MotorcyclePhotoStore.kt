package com.gust4av033.appcopilotomoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID

/** Copies a bounded, resized image to private storage. No URI grant or EXIF location is retained. */
class MotorcyclePhotoStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "motorcycle_photos").also { check(it.isDirectory || it.mkdirs()) }

    fun importPhoto(uri: Uri): String {
        val source = File.createTempFile("motorcycle_import_", ".image", context.cacheDir)
        var output: File? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { stream ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 20L * 1024 * 1024)
                        stream.write(buffer, 0, count)
                    }
                }
            } ?: error("Unreadable image")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
            val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("Unsupported image")
            try {
                val orientation = runCatching { ExifInterface(source.path)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                    .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                        ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                    }
                }
                val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                try {
                    val name = "moto_${UUID.randomUUID()}.jpg"
                    output = File(directory, name)
                    output.outputStream().use { check(oriented.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                    return name
                } finally { if (oriented !== decoded) oriented.recycle() }
            } finally { decoded.recycle() }
        } catch (failure: Exception) {
            output?.delete()
            throw failure
        } finally { source.delete() }
    }

    private fun file(name: String): File {
        require(Regex("moto_[a-f0-9-]+\\.jpg").matches(name))
        return File(directory, name)
    }
    fun read(name: String): Bitmap? = BitmapFactory.decodeFile(file(name).path)
    fun delete(name: String) { file(name).delete() }
}
