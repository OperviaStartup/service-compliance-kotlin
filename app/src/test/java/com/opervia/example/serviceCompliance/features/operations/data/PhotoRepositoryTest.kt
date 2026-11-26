package com.opervia.example.serviceCompliance.features.operations.data

import com.opervia.example.serviceCompliance.features.operations.PhotoTestData
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

class PhotoRepositoryTest {
    private lateinit var server: HttpServer
    private lateinit var repository: FieldRepository
    private val requests = ConcurrentLinkedQueue<Request>()
    @Volatile private var response = ByteArray(0)
    @Volatile private var mime = "application/json"
    @Volatile private var status = 200

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(Request(exchange.requestURI.toString(), exchange.requestMethod,
                exchange.requestHeaders.getFirst("Idempotency-Key"), exchange.requestBody.readBytes()))
            exchange.responseHeaders.add("Content-Type", mime)
            exchange.sendResponseHeaders(status, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        repository = FieldRepository(Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(OperationsApi::class.java))
    }

    @After
    fun tearDown() { server.stop(0) }

    private fun draft(kind: PhotoKind) = PhotoDraft(310, 801, kind, "00000000-0000-0000-0000-000000000001.jpg",
        "photo-stable-key", "2026-11-25T20:00:00Z", "image/png", photoChecksum(PhotoTestData.png),
        expectedVersion = 7, uploadPending = true)

    @Test
    fun legacyPhotoUsesMultipartFileVersionAndCaptureTime() = runBlocking {
        response = """{"photoRequirements":[{"id":801,"evidenceId":901}]}""".toByteArray()
        assertEquals(901L, repository.uploadPhoto(PhotoUpload(draft(PhotoKind.PHOTO_REQUIREMENT), PhotoTestData.png)))
        val request = requests.single()
        assertEquals("/api/v1/executions/310/photo-requirements/801/photo", request.path)
        assertEquals("POST", request.method)
        val raw = String(request.bytes, Charsets.ISO_8859_1)
        assertTrue(raw.contains("name=\"file\"; filename=\"evidence.png\""))
        assertTrue(raw.contains("Content-Type: image/png"))
        assertTrue(raw.contains("name=\"expectedVersion\""))
        assertTrue(raw.contains("2026-11-25T20:00:00Z"))
        assertTrue(raw.contains(String(PhotoTestData.png, Charsets.ISO_8859_1)))
        assertFalse(raw.contains("name=\"url\""))
    }

    @Test
    fun typedPhotoReusesKeyAndSendsOnlyRealLocationWhenPresent() = runBlocking {
        response = """{"id":901,"type":"PHOTO"}""".toByteArray()
        val draft = draft(PhotoKind.EVIDENCE_REQUIREMENT).copy(location = PhotoLocation(-12.1, -77.03, 8.0))
        repeat(2) { repository.uploadPhoto(PhotoUpload(draft, PhotoTestData.png)) }
        assertEquals(2, requests.size)
        requests.forEach {
            assertEquals("/api/v1/executions/310/evidence-requirements/801/evidence", it.path)
            assertEquals("photo-stable-key", it.key)
            val raw = String(it.bytes, Charsets.ISO_8859_1)
            assertTrue(raw.contains("name=\"latitude\""))
            assertTrue(raw.contains("-12.1"))
            assertTrue(raw.contains("name=\"accuracyMeters\""))
            assertTrue(raw.contains(String(PhotoTestData.png, Charsets.ISO_8859_1)))
        }
    }

    @Test
    fun optionalLocationIsOmittedInsteadOfInvented() = runBlocking {
        response = """{"id":901,"type":"PHOTO"}""".toByteArray()
        repository.uploadPhoto(PhotoUpload(draft(PhotoKind.EVIDENCE_REQUIREMENT), PhotoTestData.png))
        val raw = String(requests.single().bytes, Charsets.ISO_8859_1)
        assertFalse(raw.contains("name=\"latitude\""))
        assertFalse(raw.contains("name=\"longitude\""))
    }

    @Test
    fun contentUsesPrivateRelativeRouteAndPreservesBytes() = runBlocking {
        response = PhotoTestData.png
        mime = "image/png"
        val photo = repository.photoContent(901)
        assertEquals("/api/v1/evidence/901/content", requests.single().path)
        assertEquals("image/png", photo.contentType)
        assertArrayEquals(PhotoTestData.png, photo.bytes)
    }

    @Test
    fun invalidContentAndAccessRejectionAreNotImages() = runBlocking {
        response = "<html>Error</html>".toByteArray()
        mime = "text/html"
        try { repository.photoContent(901); fail("HTML no es una foto") }
        catch (_: OperationsFailure) { }
        status = 403
        mime = "application/json"
        response = """{"code":"ACCESS_DENIED","message":"No autorizado"}""".toByteArray()
        try { repository.photoContent(901); fail("Debe respetar autorización") }
        catch (error: OperationsFailure) { assertEquals(OperationsFailureKind.FORBIDDEN, error.kind) }
    }

    @Test
    fun oversizedDownloadIsRejected() = runBlocking {
        response = ByteArray(MAX_PHOTO_BYTES + 1)
        mime = "image/png"
        try { repository.photoContent(901); fail("Debe limitar la descarga") }
        catch (_: OperationsFailure) { }
    }

    @Test
    fun signatureUsesPngMultipartAndStableVersionKeyAndTime() = runBlocking {
        response = """{"id":901,"type":"SIGNATURE"}""".toByteArray()
        val draft = draft(PhotoKind.EVIDENCE_REQUIREMENT).copy(idempotencyKey = "signature-test-key")
        assertEquals(901L, repository.uploadPhoto(PhotoUpload(draft, PhotoTestData.png, EvidenceType.SIGNATURE)))
        val request = requests.single()
        assertEquals("/api/v1/executions/310/evidence-requirements/801/evidence", request.path)
        assertEquals("signature-test-key", request.key)
        val raw = String(request.bytes, Charsets.ISO_8859_1)
        assertTrue(raw.contains("Content-Type: image/png"))
        assertTrue(raw.contains("name=\"expectedVersion\""))
        assertTrue(raw.contains("2026-11-25T20:00:00Z"))
        assertTrue(raw.contains(String(PhotoTestData.png, Charsets.ISO_8859_1)))
        assertFalse(raw.contains("name=\"latitude\""))
    }

    @Test
    fun signatureRejectsJpegOrMoreThanOneMegabyteBeforeHttp() = runBlocking {
        val draft = draft(PhotoKind.EVIDENCE_REQUIREMENT)
        try {
            repository.uploadPhoto(PhotoUpload(draft.copy(contentType = "image/jpeg"), PhotoTestData.png, EvidenceType.SIGNATURE))
            fail("SIGNATURE no admite JPEG")
        } catch (_: IllegalArgumentException) { }
        try {
            repository.uploadPhoto(PhotoUpload(draft, ByteArray(MAX_SIGNATURE_BYTES + 1), EvidenceType.SIGNATURE))
            fail("SIGNATURE no admite más de 1 MB")
        } catch (_: IllegalArgumentException) { }
        assertTrue(requests.isEmpty())
    }

    private data class Request(val path: String, val method: String, val key: String?, val bytes: ByteArray)
}
