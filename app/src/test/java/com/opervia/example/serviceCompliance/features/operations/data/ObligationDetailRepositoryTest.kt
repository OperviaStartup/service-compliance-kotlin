package com.opervia.example.serviceCompliance.features.operations.data

import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailure
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailureKind
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

class ObligationDetailRepositoryTest {
    private lateinit var server: HttpServer
    private lateinit var repository: FieldRepository
    private val requests = ConcurrentLinkedQueue<String>()
    @Volatile private var responseBody = "{}"
    @Volatile private var responseStatus = 200

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI}")
            val bytes = responseBody.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:${server.address.port}/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OperationsApi::class.java)
        repository = FieldRepository(api)
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun detailUsesSelectedIdWithoutReloadingTheList() = runBlocking {
        responseBody = detailJson().toString()

        val detail = repository.obligationDetail(120)

        assertEquals(listOf("GET /api/v1/obligations/120"), requests.toList())
        assertEquals(120L, detail.obligation.id)
        assertEquals("Limpiar recepción", detail.obligation.title)
        assertEquals("Recepción principal", detail.obligation.siteName)
        assertEquals("ASSIGNED", detail.obligation.status)
        assertEquals("2026-11-25T15:00:00Z", detail.startsAt)
        assertEquals("2026-11-25T18:00:00Z", detail.obligation.dueAt)
        assertEquals("America/Lima", detail.timezone)
    }

    @Test
    fun stepsAndPhotoRequirementsPreserveServerIdsAndOrder() = runBlocking {
        responseBody = detailJson().toString()

        val detail = repository.obligationDetail(120)

        assertEquals(listOf(701L, 702L), detail.steps.map { it.id })
        assertEquals(listOf(1, 2), detail.steps.map { it.position })
        assertEquals("Limpiar y secar.", detail.steps.first().instructions)
        assertNull(detail.steps.last().instructions)
        assertEquals(listOf(801L, 802L), detail.photoRequirements.map { it.id })
        assertEquals("Mostrar el piso completo.", detail.photoRequirements.first().description)
        assertNull(detail.photoRequirements.last().description)
    }

    @Test
    fun legacyDetailDoesNotInventScheduleStepsOrPhotos() = runBlocking {
        responseBody = detailJson()
            .put("startsAt", JSONObject.NULL)
            .put("steps", JSONArray())
            .put("photoRequirements", JSONArray())
            .toString()

        val detail = repository.obligationDetail(120)

        assertNull(detail.startsAt)
        assertTrue(detail.steps.isEmpty())
        assertTrue(detail.photoRequirements.isEmpty())
    }

    @Test
    fun overdueStatusComesFromTheDetailResponse() = runBlocking {
        responseBody = detailJson().put("status", "OVERDUE").toString()

        assertEquals("OVERDUE", repository.obligationDetail(120).obligation.status)
    }

    @Test
    fun forbiddenDetailKeepsAuthorizationFailure() {
        assertFailure(403, OperationsFailureKind.FORBIDDEN)
    }

    @Test
    fun removedDetailKeepsNotFoundFailure() {
        assertFailure(404, OperationsFailureKind.NOT_FOUND)
    }

    @Test
    fun expiredSessionKeepsUnauthenticatedFailure() {
        assertFailure(401, OperationsFailureKind.UNAUTHENTICATED)
    }

    private fun assertFailure(status: Int, kind: OperationsFailureKind) = runBlocking {
        responseStatus = status
        responseBody = """{"code":"TEST_ERROR","message":"Recurso no disponible"}"""
        try {
            repository.obligationDetail(120)
            fail("No debe devolver un detalle cuando el servidor lo rechaza")
        } catch (failure: OperationsFailure) {
            assertEquals(kind, failure.kind)
            assertEquals("Recurso no disponible", failure.message)
        }
    }

    private fun detailJson() = JSONObject(
        """
        {
          "id":120,"companyId":10,"siteId":20,"assignedOperatorId":42,
          "title":"Limpiar recepción","description":"Seguir los pasos indicados.",
          "siteName":"Recepción principal","status":"ASSIGNED",
          "startsAt":"2026-11-25T15:00:00Z","dueAt":"2026-11-25T18:00:00Z",
          "timezone":"America/Lima",
          "steps":[
            {"id":702,"title":"Mostrador","instructions":null,"position":2},
            {"id":701,"title":"Piso","instructions":"Limpiar y secar.","position":1}
          ],
          "photoRequirements":[
            {"id":802,"title":"Mostrador limpio","description":null,"position":2},
            {"id":801,"title":"Piso terminado","description":"Mostrar el piso completo.","position":1}
          ]
        }
        """.trimIndent()
    )
}
