package com.opervia.example.serviceCompliance.features.operations

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.opervia.example.serviceCompliance.features.operations.data.*
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.StructuredEvidenceScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.StructuredEvidenceState
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrImageReaderInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pngAndJpegChosenThroughContentUriAreReallyDecoded() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = LocalPhotoFiles(File(context.filesDir, "field-photos"))
        val userId = 999999996L
        val bitmap = BarcodeEncoder().encodeBitmap("OPER-RECEPCION-01", BarcodeFormat.QR_CODE, 512, 512)
        try {
            for (format in listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.JPEG)) {
                val draft = PhotoDraft(999999996, 1, PhotoKind.PHOTO_REQUIREMENT, files.create(userId, 999999996), "image-test")
                try {
                    files.file(userId, draft).outputStream().use { bitmap.compress(format, 95, it) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fieldphotos", files.file(userId, draft))
                    val decoded = AndroidQrImageReader(context).read(uri.toString())
                    assertEquals("OPER-RECEPCION-01", decoded)
                    if (format == Bitmap.CompressFormat.PNG) {
                        val directory = File(context.cacheDir, "qr-image-review").apply { mkdirs() }
                        File(directory, "qr-from-image.png").writeBytes(files.file(userId, draft).readBytes())
                        File(directory, "decoded-qr.txt").writeText(decoded)
                    }
                } finally { files.discard(userId, draft) }
            }
        } finally { bitmap.recycle() }
    }

    @Test fun damagedFileAndImageWithoutQrAreRejected() {
        try { decodeQrImage(byteArrayOf(1, 2, 3)); fail("El archivo no es una imagen") }
        catch (failure: OperationsFailure) { assertEquals(OperationsFailureKind.INVALID_REQUEST, failure.kind) }
        val blank = createBitmap(64, 64)
        try {
            blank.eraseColor(android.graphics.Color.WHITE)
            val bytes = ByteArrayOutputStream().use { output -> blank.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            try { decodeQrImage(bytes); fail("La imagen no contiene QR") }
            catch (failure: OperationsFailure) { assertEquals(OperationsFailureKind.INVALID_REQUEST, failure.kind) }
        } finally { blank.recycle() }
    }

    @Test fun arbitraryFilePathsAndRemoteUrlsAreNotRead() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (uri in listOf("file:///data/private-file.png", "https://example.test/qr.png")) {
            try { AndroidQrImageReader(context).read(uri); fail("Debe usarse el selector de contenido") }
            catch (failure: OperationsFailure) { assertEquals(OperationsFailureKind.INVALID_REQUEST, failure.kind) }
        }
    }

    @Test fun qrScreenOffersCameraAndImageAndPickerDoesNotRegisterAutomatically() {
        var selected = 0
        var scanned = 0
        var uploaded = 0
        compose.setContent {
            MaterialTheme {
                StructuredEvidenceScreen(state(EvidenceType.QR_CODE), onScan = { scanned++ }, onPickImage = { selected++ },
                    onLocation = {}, onUpload = { uploaded++ }, onDiscard = {}, onRefresh = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Escanear código QR").assertIsDisplayed()
        compose.onNodeWithText("Seleccionar imagen del QR").performClick()
        compose.runOnIdle { assertEquals(1, selected); assertEquals(0, scanned); assertEquals(0, uploaded) }
    }

    @Test fun imageSelectionIsNotOfferedForLocationRequirements() {
        compose.setContent {
            MaterialTheme {
                StructuredEvidenceScreen(state(EvidenceType.LOCATION), onScan = {}, onPickImage = {}, onLocation = {},
                    onUpload = {}, onDiscard = {}, onRefresh = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Seleccionar imagen del QR").assertDoesNotExist()
        compose.onNodeWithText("Obtener ubicación actual").assertIsDisplayed()
    }

    private fun state(type: EvidenceType): StructuredEvidenceState {
        val requirement = ExecutionEvidenceRequirement(802, type, true, 1, 1, 1, emptyMap(), emptyList())
        val flow = ExecutionFlow(310, 120, 42, ExecutionState.IN_PROGRESS, "IN_PROGRESS", null,
            "2026-11-25T20:00:00Z", null, "2026-11-25T19:00:00Z", "2026-11-26T03:00:00Z",
            "America/Lima", 3, true, null, emptyList(), emptyList(), listOf(requirement))
        return StructuredEvidenceState(flow = flow, requirement = requirement, flowConfirmed = true)
    }
}
