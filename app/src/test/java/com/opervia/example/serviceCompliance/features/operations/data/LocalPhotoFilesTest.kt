package com.opervia.example.serviceCompliance.features.operations.data

import com.opervia.example.serviceCompliance.features.operations.PhotoTestData
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalPhotoFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun filesStayInsideTheirAccountAndExecution() {
        val files = LocalPhotoFiles(temporary.root) { 1 to 1 }
        val name = files.create(42, 310)
        val draft = PhotoDraft(310, 801, PhotoKind.PHOTO_REQUIREMENT, name, "test-photo-1")
        val own = files.file(42, draft)
        assertTrue(own.isFile)
        assertFalse(files.file(43, draft).exists())
        val expectedDirectory = File(temporary.root.canonicalFile, "user-42/execution-310")
        assertEquals(expectedDirectory, own.parentFile)
    }

    @Test
    fun traversalIsRejected() {
        val files = LocalPhotoFiles(temporary.root) { 1 to 1 }
        val draft = PhotoDraft(310, 801, PhotoKind.PHOTO_REQUIREMENT, "../../secret.txt", "test-photo-1")
        try { files.file(42, draft); fail("No debe aceptar rutas fuera del directorio") }
        catch (_: IllegalArgumentException) { }
    }

    @Test
    fun inspectionKeepsOriginalBytesMimeAndChecksum() {
        val files = LocalPhotoFiles(temporary.root) { 1 to 1 }
        val draft = PhotoDraft(310, 801, PhotoKind.PHOTO_REQUIREMENT, files.create(42, 310), "test-photo-1")
        files.file(42, draft).writeBytes(PhotoTestData.png)
        val photo = files.inspect(42, draft)
        assertEquals("image/png", photo.contentType)
        assertArrayEquals(PhotoTestData.png, photo.bytes)
        assertEquals(photoChecksum(PhotoTestData.png), photo.checksum)
        assertNotNull(java.time.Instant.parse(photo.capturedAt))
    }

    @Test
    fun oversizedPhotoIsRejectedBeforeDecoding() {
        var decoded = false
        val files = LocalPhotoFiles(temporary.root) { decoded = true; 1 to 1 }
        val draft = PhotoDraft(310, 801, PhotoKind.PHOTO_REQUIREMENT, files.create(42, 310), "test-photo-1")
        java.io.RandomAccessFile(files.file(42, draft), "rw").use { it.setLength(MAX_PHOTO_BYTES + 1L) }
        try { files.inspect(42, draft); fail("Debe rechazar más de 5 MB") }
        catch (_: OperationsFailure) { }
        assertFalse(decoded)
    }

    @Test
    fun excessiveDimensionsAreRejected() {
        val files = LocalPhotoFiles(temporary.root) { 6000 to 6000 }
        val draft = PhotoDraft(310, 801, PhotoKind.PHOTO_REQUIREMENT, files.create(42, 310), "test-photo-1")
        files.file(42, draft).writeBytes(PhotoTestData.png)
        try { files.inspect(42, draft); fail("Debe rechazar dimensiones excesivas") }
        catch (_: OperationsFailure) { }
    }

    @Test
    fun discardingOneDraftDoesNotDeleteAnotherAccountFile() {
        val files = LocalPhotoFiles(temporary.root) { 1 to 1 }
        val first = PhotoDraft(310, 801, PhotoKind.PHOTO_REQUIREMENT, files.create(42, 310), "test-photo-1")
        val other = PhotoDraft(310, 801, PhotoKind.PHOTO_REQUIREMENT, files.create(43, 310), "test-photo-2")
        files.discard(42, first)
        assertFalse(files.file(42, first).exists())
        assertTrue(files.file(43, other).exists())
    }
}
