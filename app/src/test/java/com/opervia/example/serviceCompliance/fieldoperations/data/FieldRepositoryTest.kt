package com.opervia.example.serviceCompliance.fieldoperations.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.nio.file.Files

class FieldRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: FieldRepository

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/api/v1").toString().removeSuffix("/")
        repository = FieldRepository(baseUrl, "test-session")
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun reply(status: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
    }

    private fun takeRequest(): RecordedRequest = server.takeRequest()

    // Los métodos @Test usan takeRequest() para validar método, path y cabeceras
}