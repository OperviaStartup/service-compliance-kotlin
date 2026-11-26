package com.opervia.example.serviceCompliance.features.operations.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.opervia.example.serviceCompliance.features.operations.domain.model.MAX_PHOTO_BYTES
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailure
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailureKind
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class QrImageDecoderTest {
    @Test fun decodesQrPixelsWithoutInventingOrChangingTheValue() {
        val pixels = qr("OPER-IMAGEN-01")
        assertEquals("OPER-IMAGEN-01", decodeQrPixels(256, 256, pixels))
    }

    @Test fun rotatedAndInvertedQrImagesAreDecoded() {
        val pixels = qr("OPER-ROTADO-01")
        val rotated = IntArray(pixels.size)
        for (y in 0 until 256) for (x in 0 until 256) rotated[x * 256 + (255 - y)] = pixels[y * 256 + x]
        assertEquals("OPER-ROTADO-01", decodeQrPixels(256, 256, rotated))
        val inverted = pixels.map { if (it == -1) 0xff000000.toInt() else -1 }.toIntArray()
        assertEquals("OPER-ROTADO-01", decodeQrPixels(256, 256, inverted))
    }

    @Test fun blankImageDoesNotBecomeQrEvidence() {
        try { decodeQrPixels(64, 64, IntArray(64 * 64) { -1 }); fail("No contiene QR") }
        catch (failure: OperationsFailure) { assertEquals(OperationsFailureKind.INVALID_REQUEST, failure.kind) }
    }

    @Test fun transparentBackgroundIsReadAsDisplayedOnWhite() {
        val pixels = qr("OPER-TRANSPARENTE").map { if (it == -1) 0 else it }.toIntArray()
        assertEquals("OPER-TRANSPARENTE", decodeQrPixels(256, 256, pixels))
    }

    @Test fun otherBarcodeFormatsAreNotAcceptedAsQr() {
        val barcode = MultiFormatWriter().encode("123456789012", BarcodeFormat.CODE_128, 256, 256)
        val pixels = IntArray(256 * 256) { i -> if (barcode[i % 256, i / 256]) 0xff000000.toInt() else -1 }
        try { decodeQrPixels(256, 256, pixels); fail("CODE_128 no es QR") }
        catch (_: OperationsFailure) { }
    }

    @Test fun inputBytesArePreservedWithinTheSizeLimit() {
        val data = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(data, readQrImageBytes(ByteArrayInputStream(data)))
    }

    @Test fun emptyAndOversizedImagesAreRejectedBeforeDecoding() {
        for (data in listOf(ByteArray(0), ByteArray(MAX_PHOTO_BYTES + 1))) {
            try { readQrImageBytes(ByteArrayInputStream(data)); fail("Debe rechazar el tamaño inválido") }
            catch (failure: OperationsFailure) { assertEquals(OperationsFailureKind.INVALID_REQUEST, failure.kind) }
        }
    }

    private fun qr(value: String): IntArray {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 256, 256)
        return IntArray(256 * 256) { i -> if (matrix[i % 256, i / 256]) 0xff000000.toInt() else -1 }
    }
}
