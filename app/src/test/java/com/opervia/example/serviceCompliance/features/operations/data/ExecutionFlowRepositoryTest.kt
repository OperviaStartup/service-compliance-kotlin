package com.opervia.example.serviceCompliance.features.operations.data

import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

class ExecutionFlowRepositoryTest {
    private lateinit var server: HttpServer
    private lateinit var repository: FieldRepository
    private val requests = ConcurrentLinkedQueue<ReceivedRequest>()
    @Volatile private var body = "{}"
    @Volatile private var status = 200

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(ReceivedRequest(exchange.requestMethod, exchange.requestURI.toString(),
                exchange.requestHeaders.getFirst("Idempotency-Key"), exchange.requestBody.bufferedReader().readText()))
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        repository = FieldRepository(Retrofit.Builder()
            .baseUrl("http://127.0.0.1:${server.address.port}/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(OperationsApi::class.java))
    }

    @After
    fun tearDown() { server.stop(0) }

    @Test
    fun startUsesLifecycleRouteAndStableHeaderWithoutDeclaredResult() = runBlocking {
        body = flowJson().toString()
        val flow = repository.startExecution(120, "start-test-120")
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/obligations/120/execution", request.path)
        assertEquals("start-test-120", request.key)
        assertTrue(request.body.isEmpty())
        assertEquals(310L, flow.id)
        assertNull(flow.result)
        assertNull(flow.confirmedRecord())
    }

    @Test
    fun onlyNotFoundMeansNoCurrentExecution() = runBlocking {
        status = 404
        body = """{"code":"RESOURCE_NOT_FOUND","message":"Sin ejecución"}"""
        assertNull(repository.currentExecution(120))
        status = 403
        body = """{"code":"ACCESS_DENIED","message":"Fuera de alcance"}"""
        try {
            repository.currentExecution(120)
            fail("403 no puede interpretarse como una ejecución sin iniciar")
        } catch (failure: OperationsFailure) {
            assertEquals(OperationsFailureKind.FORBIDDEN, failure.kind)
            assertEquals("ACCESS_DENIED", failure.code)
        }
    }

    @Test
    fun stepUsesServerIdentifiersAndExpectedVersion() = runBlocking {
        body = flowJson().put("version", 8).toString()
        val flow = repository.updateStep(310, 701, true, 7)
        val request = requests.single()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/executions/310/steps/701", request.path)
        assertTrue(JSONObject(request.body).getBoolean("completed"))
        assertEquals(7L, JSONObject(request.body).getLong("expectedVersion"))
        assertEquals(8L, flow.version)
    }

    @Test
    fun submitReplaysExactKeyVersionAndNotesWithoutSelectingResult() = runBlocking {
        body = flowJson().put("state", "SUBMITTED").put("result", "COMPLETED")
            .put("submittedAt", "2026-11-25T19:00:00Z").put("canEdit", false).toString()
        val pending = PendingExecutionSubmission(310, "submit-test-310", 7, "Notas")
        repository.submitExecution(pending)
        val replay = repository.submitExecution(pending)
        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("/api/v1/executions/310/submit", request.path)
            assertEquals("submit-test-310", request.key)
            val payload = JSONObject(request.body)
            assertEquals(7L, payload.getLong("expectedVersion"))
            assertEquals("Notas", payload.getString("notes"))
            assertFalse(payload.has("result"))
        }
        assertEquals("2026-11-25T19:00:00Z", replay.confirmedRecord()?.executedAt)
    }

    @Test
    fun requiredEvidenceCountsAndPhotoAttachmentsControlReadiness() = runBlocking {
        val json = flowJson()
        json.getJSONArray("steps").getJSONObject(0).put("completed", true)
        json.put("photoRequirements", JSONArray("""[{"id":801,"title":"Piso","position":1,"evidenceId":null}]"""))
        body = json.toString()
        assertFalse(repository.currentExecution(120)!!.canSubmit)
        json.getJSONArray("photoRequirements").getJSONObject(0).put("evidenceId", 901)
        json.put("evidenceRequirements", JSONArray("""[{
            "id":802,"type":"QR_CODE","required":true,"minimumCount":1,"maximumCount":2,
            "position":1,"constraints":{},"evidence":[]
        }]"""))
        body = json.toString()
        assertFalse(repository.currentExecution(120)!!.canSubmit)
        json.getJSONArray("evidenceRequirements").getJSONObject(0).put("required", false)
        body = json.toString()
        assertTrue(repository.currentExecution(120)!!.canSubmit)
        json.put("canEdit", false)
        body = json.toString()
        assertFalse(repository.currentExecution(120)!!.canSubmit)
    }

    @Test
    fun versionConflictPreservesBackendCodeAndMessage() = runBlocking {
        status = 409
        body = """{"code":"VERSION_CONFLICT","message":"La ejecución cambió"}"""
        try {
            repository.updateStep(310, 701, true, 0)
            fail("Debe propagar el conflicto")
        } catch (failure: OperationsFailure) {
            assertEquals(OperationsFailureKind.CONFLICT, failure.kind)
            assertEquals("VERSION_CONFLICT", failure.code)
            assertEquals("La ejecución cambió", failure.message)
        }
    }

    @Test
    fun exceptionUsesIndependentResourceAndMapsServerConfirmation() = runBlocking {
        val operation = "00eafdd7-57ec-427e-89b6-ae9b7c80aa1a"
        body = flowJson().put("exceptions", JSONArray().put(JSONObject().put("id", 601)
            .put("stepId", 701).put("reason", "MISSING_SUPPLIES").put("description", "Faltan paños")
            .put("reportedBy", 42).put("reportedAt", "2026-11-25T20:00:00Z")
            .put("operationId", operation).put("evidenceIds", JSONArray(listOf(901L))))).toString()
        val pending = PendingExceptionReport(310, "exception-test-key", operation, ExceptionReason.MISSING_SUPPLIES,
            "Faltan paños", 701, listOf(901), 4)
        val flow = repository.reportException(pending)
        repository.reportException(pending)
        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/executions/310/exceptions", request.path)
            assertEquals("exception-test-key", request.key)
            val payload = JSONObject(request.body)
            assertEquals("MISSING_SUPPLIES", payload.getString("reason"))
            assertEquals("Faltan paños", payload.getString("description"))
            assertEquals(operation, payload.getString("operationId"))
            assertEquals(701L, payload.getLong("stepId"))
            assertEquals(901L, payload.getJSONArray("evidenceIds").getLong(0))
            assertEquals(4L, payload.getLong("expectedVersion"))
            assertFalse(payload.has("result"))
        }
        assertTrue(pending.matches(flow.exceptions.single()))
        assertEquals("2026-11-25T20:00:00Z", flow.exceptions.single().reportedAt)
        assertNull(flow.confirmedRecord())
        assertFalse(flow.canSubmit)
    }

    @Test
    fun exceptionConflictPreservesCodeForCorrection() = runBlocking {
        status = 409
        body = """{"code":"VERSION_CONFLICT","message":"El avance cambió"}"""
        try {
            repository.reportException(PendingExceptionReport(310, "exception-test-key", "test-operation",
                ExceptionReason.OTHER, "Detalle", null, emptyList(), 0))
            fail("Debe informar el conflicto")
        } catch (failure: OperationsFailure) {
            assertEquals(OperationsFailureKind.CONFLICT, failure.kind)
            assertEquals("VERSION_CONFLICT", failure.code)
            assertEquals("El avance cambió", failure.message)
        }
    }

    @Test
    fun qrUsesJsonAndPreservesCapturedValueTimeAndIdempotency() = runBlocking {
        body = """{"id":901,"type":"QR_CODE","textValue":"OPER-123","capturedAt":"2026-11-25T22:00:00Z","checksum":"test"}"""
        val draft = StructuredEvidenceDraft(310, 802, EvidenceType.QR_CODE, "qr-test-key", "2026-11-25T22:00:00Z",
            textValue = "OPER-123", expectedVersion = 4, uploadPending = true)
        val result = repository.uploadStructuredEvidence(draft)
        repository.uploadStructuredEvidence(draft)
        requests.forEach { request ->
            assertEquals("/api/v1/executions/310/evidence-requirements/802/evidence", request.path)
            assertEquals("POST", request.method)
            assertEquals("qr-test-key", request.key)
            val payload = JSONObject(request.body)
            assertEquals("OPER-123", payload.getString("textValue"))
            assertEquals("2026-11-25T22:00:00Z", payload.getString("capturedAt"))
            assertEquals(4L, payload.getLong("expectedVersion"))
            assertFalse(payload.has("latitude"))
            assertFalse(payload.has("result"))
        }
        assertEquals(EvidenceType.QR_CODE, result.type)
        assertEquals("OPER-123", result.textValue)
        assertEquals(2, requests.size)
    }

    @Test
    fun locationUsesJsonAndMapsCoordinatesAndAccuracy() = runBlocking {
        body = """{"id":902,"type":"LOCATION","latitude":-12.1,"longitude":-77.03,"accuracyMeters":8.0,"capturedAt":"2026-11-25T22:00:00Z"}"""
        val result = repository.uploadStructuredEvidence(StructuredEvidenceDraft(310, 803, EvidenceType.LOCATION,
            "location-test-key", "2026-11-25T22:00:00Z", location = PhotoLocation(-12.1, -77.03, 8.0),
            expectedVersion = 5, uploadPending = true))
        val payload = JSONObject(requests.single().body)
        assertEquals(-12.1, payload.getDouble("latitude"), 0.0)
        assertEquals(-77.03, payload.getDouble("longitude"), 0.0)
        assertEquals(8.0, payload.getDouble("accuracyMeters"), 0.0)
        assertFalse(payload.has("textValue"))
        assertEquals(-12.1, requireNotNull(result.latitude), 0.0)
        assertEquals(-77.03, requireNotNull(result.longitude), 0.0)
        assertEquals(8.0, requireNotNull(result.accuracyMeters), 0.0)
    }

    private data class ReceivedRequest(val method: String, val path: String, val key: String?, val body: String)

    private fun flowJson() = JSONObject("""{
        "id":310,"obligationId":120,"operatorId":42,"state":"IN_PROGRESS",
        "obligationStatus":"IN_PROGRESS","result":null,
        "startedAt":"2026-11-25T15:01:00Z","submittedAt":null,
        "startsAt":"2026-11-25T15:00:00Z","dueAt":"2026-11-25T23:00:00Z",
        "timezone":"America/Lima","version":0,"canEdit":true,"notes":null,
        "steps":[{"id":701,"title":"Piso","instructions":null,"position":1,"completed":false}],
        "photoRequirements":[],"evidenceRequirements":[],"exceptions":[]
    }""")
}
