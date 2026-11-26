package com.opervia.example.serviceCompliance.features.operations.data

import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ExecutionHistoryRepositoryTest {
    private lateinit var server: HttpServer
    private lateinit var repository: FieldRepository
    private val requests = ConcurrentLinkedQueue<Map<String, String>>()
    @Volatile private var body = "{}"
    @Volatile private var status = 200

    @Before fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/executions") { exchange ->
            assertEquals("GET", exchange.requestMethod)
            val query = exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
                val pair = it.split('=', limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }
            requests.add(query)
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        repository = FieldRepository(Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(OperationsApi::class.java))
    }
    @After fun tearDown() { server.stop(0) }
    private fun query() = ExecutionHistoryQuery(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25))

    @Test fun sendsDatesSearchReviewAndCursorWithoutClientIdentityParameters() = runBlocking {
        body = page().toString()
        repository.executionHistory(query().copy(query = " Recepción %_ ", reviewStatus = HistoryReviewStatus.COMPLIANT), "opaque-next")
        val parameters = requests.single()
        assertEquals("2026-11-25", parameters["fromDate"])
        assertEquals("2026-11-25", parameters["toDate"])
        assertEquals("Recepción %_", parameters["query"])
        assertEquals("COMPLIANT", parameters["reviewStatus"])
        assertEquals("opaque-next", parameters["cursor"])
        assertEquals("50", parameters["limit"])
        assertFalse(parameters.containsKey("operatorId"))
        assertFalse(parameters.containsKey("companyId"))
        assertFalse(parameters.containsKey("siteId"))
    }

    @Test fun completedExecutionDoesNotImplyComplianceAndUsesSubmissionTime() = runBlocking {
        body = page(item()).toString()
        val record = repository.executionHistory(query()).items.single()
        assertEquals(HistoryReviewStatus.PENDING_REVIEW, record.reviewStatus)
        assertEquals(ExecutionResult.COMPLETED, record.result)
        assertEquals("2026-11-25T17:00:00Z", record.recordedAt)
        assertEquals(HistorySynchronizationState.SYNCED, record.synchronizationState)
        assertNull(record.complianceResult)
    }

    @Test fun mapsOnlyExplicitCanonicalDecisionsIncludingExceptionAccepted() = runBlocking {
        for (outcome in listOf("COMPLIANT", "EXCEPTION_ACCEPTED", "NON_COMPLIANT")) {
            val decision = JSONObject().put("id", 30).put("caseId", 20).put("outcome", outcome)
                .put("decisionVersion", 2).put("decidedAt", "2026-11-25T18:00:00Z").put("explanation", "Decisión real")
            body = page(item().put("complianceResult", decision)).toString()
            val record = repository.executionHistory(query()).items.single()
            assertEquals(HistoryReviewStatus.valueOf(outcome), record.reviewStatus)
            assertEquals(2L, record.complianceResult?.decisionVersion)
            assertEquals("Decisión real", record.complianceResult?.explanation)
        }
    }

    @Test fun legacyRecordRetainsItsDateAndDeclaredPartialResult() = runBlocking {
        body = page(item().put("state", "LEGACY").put("result", "PARTIAL").put("submittedAt", JSONObject.NULL)).toString()
        val record = repository.executionHistory(query()).items.single()
        assertEquals(ExecutionState.LEGACY, record.state)
        assertEquals(ExecutionResult.PARTIAL, record.result)
        assertEquals(record.executedAt, record.recordedAt)
        assertEquals(HistoryReviewStatus.PENDING_REVIEW, record.reviewStatus)
    }

    @Test fun paginationKeepsTheOpaqueCursorAndServerCutoff() = runBlocking {
        body = page(item()).put("hasMore", true).put("nextCursor", "opaque-page-2").toString()
        val result = repository.executionHistory(query())
        assertTrue(result.hasMore)
        assertEquals("opaque-page-2", result.nextCursor)
        assertEquals("2026-11-25T20:00:00Z", result.asOf)
        assertFalse(requests.single().containsKey("query"))
        assertFalse(requests.single().containsKey("reviewStatus"))
    }

    @Test fun pendingExecutionAndRetryableOperationAreNotAcceptedAsPersistedHistory() = runBlocking {
        for (row in listOf(item().put("state", "IN_PROGRESS"), item().put("synchronizationState", "RETRYABLE"))) {
            body = page(row).toString()
            try { repository.executionHistory(query()); fail("No es un registro final confirmado") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun pageWithoutRequiredNextCursorIsRejected() = runBlocking {
        body = page(item()).put("hasMore", true).toString()
        try { repository.executionHistory(query()); fail("Falta cursor") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun authorizationAndFilterErrorsAreNotConvertedToAnEmptyHistory() = runBlocking {
        for ((code, kind) in listOf(401 to OperationsFailureKind.UNAUTHENTICATED,
            403 to OperationsFailureKind.FORBIDDEN, 400 to OperationsFailureKind.INVALID_REQUEST)) {
            status = code
            body = """{"code":"REJECTED","message":"Consulta rechazada"}"""
            try { repository.executionHistory(query()); fail("Debe mostrar el error") }
            catch (failure: OperationsFailure) {
                assertEquals(kind, failure.kind)
                assertEquals("REJECTED", failure.code)
            }
        }
    }

    private fun page(row: JSONObject? = null) = JSONObject().put("items", JSONArray().also { if (row != null) it.put(row) })
        .put("nextCursor", JSONObject.NULL).put("hasMore", false).put("asOf", "2026-11-25T20:00:00Z")
    private fun item() = JSONObject("""{"id":1,"obligationId":2,"activity":"Recepción","siteName":"Centro",
        "timezone":"America/Lima","state":"SUBMITTED","result":"COMPLETED",
        "executedAt":"2026-11-25T15:00:00Z","submittedAt":"2026-11-25T17:00:00Z","notes":null,
        "synchronizationState":"SYNCED","complianceResult":null}""")
}
