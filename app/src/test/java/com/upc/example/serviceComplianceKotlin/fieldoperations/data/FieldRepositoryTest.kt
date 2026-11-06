package com.upc.example.serviceComplianceKotlin.fieldoperations.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue

/** Checks the actual HTTP requests and JSON mapping, without changing the backend. */
class FieldRepositoryTest {
    private data class Reply(val status: Int, val body: ByteArray)
    private data class Request(val method: String, val path: String, val authorization: String?, val body: ByteArray)

    private lateinit var server: HttpServer
    private lateinit var repository: FieldRepository
    private val replies = ConcurrentLinkedQueue<Reply>()
    private val requests = ConcurrentLinkedQueue<Request>()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1") { exchange ->
            requests.add(Request(exchange.requestMethod, exchange.requestURI.path,
                exchange.requestHeaders.getFirst("Authorization"), exchange.requestBody.use { it.readBytes() }))
            val reply = replies.remove()
            exchange.sendResponseHeaders(reply.status, reply.body.size.toLong())
            exchange.responseBody.use { it.write(reply.body) }
            exchange.close()
        }
        server.start()
        repository = FieldRepository("http://127.0.0.1:${server.address.port}", "test-session")
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun reply(status: Int, body: String) {
        replies.add(Reply(status, body.toByteArray()))
    }

    private val executionJson = """
        {
          "id": 7, "state": "IN_PROGRESS", "obligationStatus": "IN_PROGRESS", "version": 4,
          "canEdit": true,
          "steps": [{"id": 2, "title": "Piso", "instructions": null, "completed": true}],
          "photoRequirements": [{"id": 3, "title": "Foto del piso", "description": null, "evidenceId": 8}],
          "exceptions": [{"reason": "AREA_CLOSED", "description": "Puerta cerrada", "operationId": "operation-1"}]
        }
    """.trimIndent()

    @Test
    fun detailKeepsServerOrderAndNullableFields() = runBlocking {
        reply(200, """{
          "id":99,"title":"Recepcion","description":null,"siteName":"Centro local",
          "status":"ASSIGNED","startsAt":null,"dueAt":"2030-01-01T18:00:00Z",
          "steps":[{"id":9,"title":"Primero","instructions":null},{"id":2,"title":"Segundo","instructions":"Ordenar"}],
          "photoRequirements":[{"id":3,"title":"Foto","description":null}]
        }""")
        val detail = repository.detail(99)
        assertEquals(listOf(9L, 2L), detail.steps.map { it.id })
        assertFalse(detail.steps.first().completed)
        assertNull(detail.description)
        assertNull(detail.startsAt)
        assertNull(detail.photos.first().evidenceId)
        val request = requests.remove()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/obligations/99", request.path)
        assertEquals("Bearer test-session", request.authorization)
    }

    @Test
    fun missingExecutionReturnsNullAndSessionErrorsAreNotHidden() = runBlocking {
        reply(404, """{"code":"NOT_FOUND","message":"Sin ejecucion"}""")
        assertNull(repository.current(99))
        reply(401, """{"code":"UNAUTHORIZED","message":"Sesion vencida"}""")
        try {
            repository.current(99)
            fail("A 401 must not be converted into a missing execution")
        } catch (error: FieldApiException) {
            assertEquals(401, error.status)
            assertEquals("UNAUTHORIZED", error.code)
            assertEquals("Sesion vencida", error.message)
        }
    }

    @Test
    fun mutationsSendTheCurrentVersionWithoutChoosingAResult() = runBlocking {
        reply(201, executionJson)
        val execution = repository.start(99)
        assertEquals(7L, execution.id)
        assertEquals(8L, execution.photos.first().evidenceId)
        assertEquals("operation-1", execution.exceptions.first().operationId)
        val start = requests.remove()
        assertEquals("POST", start.method)
        assertTrue(start.body.isEmpty())

        reply(200, executionJson)
        repository.step(execution, 2, false)
        val step = requests.remove()
        assertEquals("PUT", step.method)
        assertEquals("/api/v1/executions/7/steps/2", step.path)
        assertEquals(false, JSONObject(String(step.body)).getBoolean("completed"))
        assertEquals(4L, JSONObject(String(step.body)).getLong("expectedVersion"))

        reply(200, executionJson)
        repository.report(execution, "AREA_CLOSED", "Puerta cerrada", "operation-1")
        val report = JSONObject(String(requests.remove().body))
        assertEquals("operation-1", report.getString("operationId"))
        assertEquals("Puerta cerrada", report.getString("description"))
        assertEquals(4L, report.getLong("expectedVersion"))

        reply(200, executionJson)
        repository.submit(execution)
        val submit = requests.remove()
        assertEquals("/api/v1/executions/7/submit", submit.path)
        val body = JSONObject(String(submit.body))
        assertEquals(4L, body.getLong("expectedVersion"))
        assertFalse(body.has("result"))
        assertFalse(body.has("state"))
    }

    @Test
    fun uploadKeepsFileBytesAndVersionAndPhotoDownloadUsesTheSession() = runBlocking {
        reply(200, executionJson)
        val execution = repository.start(99)
        requests.remove()
        val file = Files.createTempFile("field-upload-test-", ".jpg").toFile()
        val bytes = "test-photo-bytes".toByteArray()
        try {
            file.writeBytes(bytes)
            reply(200, executionJson)
            repository.upload(execution, 3, file)
            val upload = requests.remove()
            val multipart = String(upload.body)
            assertEquals("/api/v1/executions/7/photo-requirements/3/photo", upload.path)
            assertTrue(multipart.contains("name=\"expectedVersion\"\r\n\r\n4\r\n"))
            assertTrue(multipart.contains("Content-Type: image/jpeg"))
            assertTrue(multipart.contains("test-photo-bytes"))
            replies.add(Reply(200, bytes))
            assertArrayEquals(bytes, repository.photo(8))
            val download = requests.remove()
            assertEquals("/api/v1/evidence/8/content", download.path)
            assertEquals("Bearer test-session", download.authorization)
        } finally {
            file.delete()
        }
    }

    @Test
    fun versionConflictKeepsTheServerErrorAndDoesNotRetry() = runBlocking {
        reply(200, executionJson)
        val execution = repository.start(99)
        requests.remove()
        reply(409, """{"code":"VERSION_CONFLICT","message":"Actualizar avance","currentVersion":5}""")
        try {
            repository.step(execution, 2, false)
            fail("The version conflict must be returned to the ViewModel")
        } catch (error: FieldApiException) {
            assertEquals(409, error.status)
            assertEquals("VERSION_CONFLICT", error.code)
            assertEquals("Actualizar avance", error.message)
        }
        assertEquals(1, requests.size)
    }
}
