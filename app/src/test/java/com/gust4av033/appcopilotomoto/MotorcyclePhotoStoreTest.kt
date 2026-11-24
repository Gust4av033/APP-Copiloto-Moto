package com.gust4av033.appcopilotomoto

import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MotorcyclePhotoStoreTest {
    @Test fun imageIsPrivateResizedAndHasNoSourceLocationMetadata() {
        val context = RuntimeEnvironment.getApplication()
        val source = File.createTempFile("photo-test", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "13/1,42/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            saveAttributes()
        }
        val store = MotorcyclePhotoStore(context)
        val name = store.importPhoto(Uri.fromFile(source))
        source.delete() // The stored copy must remain usable without the source or a URI grant.
        val saved = store.read(name)!!
        assertTrue(saved.height <= 1280 && saved.width <= 1280)
        assertTrue(saved.height > saved.width)
        saved.recycle()
        val privateFile = File(context.filesDir, "motorcycle_photos/$name")
        assertNull(ExifInterface(privateFile.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        store.delete(name)
        assertFalse(privateFile.exists())
    }

    @Test fun invalidPhotoDoesNotReplaceExistingPrivateImageAndTemporaryFilesAreRemoved() {
        val context = RuntimeEnvironment.getApplication()
        val store = MotorcyclePhotoStore(context)
        val valid = File.createTempFile("photo-valid", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        valid.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        val old = store.importPhoto(Uri.fromFile(valid))
        val invalid = File.createTempFile("photo-invalid", ".jpg", context.cacheDir).apply { writeText("not an image") }
        assertTrue(runCatching { store.importPhoto(Uri.fromFile(invalid)) }.isFailure)
        assertNotNull(store.read(old))
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("motorcycle_import_") })
        store.delete(old); valid.delete(); invalid.delete()
        assertTrue(runCatching { store.read("../outside.jpg") }.isFailure)
    }
}
