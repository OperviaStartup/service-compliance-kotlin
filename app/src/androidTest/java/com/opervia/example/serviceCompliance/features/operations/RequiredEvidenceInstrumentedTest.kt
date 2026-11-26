package com.opervia.example.serviceCompliance.features.operations

import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.opervia.example.serviceCompliance.features.operations.data.*
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.presentation.components.Page
import com.opervia.example.serviceCompliance.features.operations.presentation.components.SignaturePad
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequiredEvidenceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun drawingInsideScrollablePageProducesTheActualPngSignature() {
        var strokes by mutableStateOf(emptyList<List<SignaturePoint>>())
        compose.setContent { MaterialTheme { Page { SignaturePad(strokes) { strokes = it } } } }
        compose.onNodeWithContentDescription("Panel para dibujar la firma").performTouchInput {
            swipe(Offset(width * .2f, height * .7f), Offset(width * .8f, height * .3f), 500)
        }
        compose.runOnIdle {
            assertTrue(strokes.isNotEmpty())
            val bytes = signaturePng(strokes)
            assertTrue(bytes.size < MAX_SIGNATURE_BYTES)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = java.io.File(context.cacheDir, "evidence-review").apply { mkdirs() }
            java.io.File(directory, "signature-demo.png").writeBytes(bytes)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            assertEquals(1000, bitmap.width)
            assertEquals(500, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.any { it != android.graphics.Color.WHITE })
            bitmap.recycle()
        }
    }

    @Test fun emptyPanelCannotBeConvertedIntoRegisteredSignature() {
        try { signaturePng(emptyList()); fail("La firma vacía no se debe preparar") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun realQrDecoderReadsTheExpectedCodeAndScanContractUsesInstalledActivity() {
        val value = "OPER-RECEPCION-01"
        val bitmap = BarcodeEncoder().encodeBitmap(value, BarcodeFormat.QR_CODE, 512, 512)
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
                mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
            assertEquals(value, result.text)
            assertEquals(BarcodeFormat.QR_CODE, result.barcodeFormat)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val intent = ScanContract().createIntent(context, ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE))
            assertNotNull(context.packageManager.resolveActivity(intent, 0))
            val directory = java.io.File(context.cacheDir, "evidence-review").apply { mkdirs() }
            java.io.File(directory, "qr-demo.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @Test fun structuredCapturePersistsWithAccountAndRequirementIsolation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalFieldOperationsStore(context)
        val userId = 999999997L
        val draft = StructuredEvidenceDraft(999999997, 999999997, EvidenceType.LOCATION,
            "evidence-${UUID.randomUUID()}", "2026-11-25T22:00:00Z", location = PhotoLocation(-12.1, -77.03, 8.0),
            expectedVersion = 3, uploadPending = true)
        try {
            store.saveStructuredDraft(userId, draft)
            val reopened = LocalFieldOperationsStore(context)
            assertEquals(draft, reopened.structuredDraft(userId, draft.executionId, draft.requirementId))
            assertNull(reopened.structuredDraft(userId + 1, draft.executionId, draft.requirementId))
            assertNull(reopened.structuredDraft(userId, draft.executionId + 1, draft.requirementId))
            assertNull(reopened.structuredDraft(userId, draft.executionId, draft.requirementId + 1))
        } finally { store.removeStructuredDraft(userId, draft) }
    }
}
