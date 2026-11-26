package com.opervia.example.serviceCompliance.features.operations

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.opervia.example.serviceCompliance.features.operations.data.FieldPhotoProvider
import com.opervia.example.serviceCompliance.features.operations.data.LocalPhotoFiles
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoDraft
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoKind
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.decodePhotoPreview
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoFilesInstrumentedTest {
    @Test
    fun cameraExifOrientationIsAppliedOnlyToPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = LocalPhotoFiles(File(context.filesDir, "field-photos"))
        val userId = 999999999L
        val draft = PhotoDraft(999999999, 1, PhotoKind.PHOTO_REQUIREMENT,
            files.create(userId, 999999999), "instrumented-orientation")
        try {
            val file = files.file(userId, draft)
            val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val original = file.readBytes()
            val preview = requireNotNull(decodePhotoPreview(original))
            assertEquals(8, preview.width)
            assertEquals(16, preview.height)
            assertArrayEquals(original, file.readBytes())
            preview.recycle()
        } finally { files.discard(userId, draft) }
    }
    @Test
    fun privateCaptureFileResolvesThroughConfiguredProviderAndDecodesOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = LocalPhotoFiles(File(context.filesDir, "field-photos"))
        val userId = 999999999L
        val draft = PhotoDraft(999999999, 1, PhotoKind.PHOTO_REQUIREMENT,
            files.create(userId, 999999999), "instrumented-only")
        try {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            files.file(userId, draft).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            val captured = files.inspect(userId, draft)
            assertEquals("image/jpeg", captured.contentType)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fieldphotos", files.file(userId, draft))
            assertEquals("content", uri.scheme)
            assertArrayEquals(captured.bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            @Suppress("DEPRECATION")
            val provider = context.packageManager.getProviderInfo(ComponentName(context, FieldPhotoProvider::class.java), 0)
            assertFalse(provider.exported)
            assertTrue(provider.grantUriPermissions)
        } finally { files.discard(userId, draft) }
    }
}
