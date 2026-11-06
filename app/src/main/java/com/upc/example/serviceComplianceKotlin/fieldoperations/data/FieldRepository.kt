package com.upc.example.serviceComplianceKotlin.fieldoperations.data

import com.upc.example.serviceComplianceKotlin.fieldoperations.model.Execution
import com.upc.example.serviceComplianceKotlin.fieldoperations.model.ObligationDetail
import com.upc.example.serviceComplianceKotlin.fieldoperations.model.PhotoRequirement
import com.upc.example.serviceComplianceKotlin.fieldoperations.model.WorkException
import com.upc.example.serviceComplianceKotlin.fieldoperations.model.WorkStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID


class FieldRepository(baseUrl: String, private val token: String) {
    private val root = baseUrl.trim().trimEnd('/')

    init {
        val uri = URI(root)
        val validAddress = uri.host != null && uri.userInfo == null &&
            uri.query == null && uri.fragment == null
        require(validAddress)

        val localAddress = uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost")
        val secureConnection = uri.scheme == "https"
        val localConnection = uri.scheme == "http" && localAddress
        require(secureConnection || localConnection)

        val validToken = token.isNotBlank() && !token.contains('\n') && !token.contains('\r')
        require(validToken)
    }

    suspend fun detail(id: Long): ObligationDetail {
        val response = request("GET", "/obligations/$id")
        val steps = readSteps(response.getJSONArray("steps"))
        val photos = readPhotos(response.getJSONArray("photoRequirements"))

        return ObligationDetail(
            id = response.getLong("id"),
            title = response.getString("title"),
            description = readOptionalString(response, "description"),
            siteName = response.getString("siteName"),
            status = response.getString("status"),
            startsAt = readOptionalString(response, "startsAt"),
            dueAt = response.getString("dueAt"),
            steps = steps,
            photos = photos
        )
    }

    suspend fun current(id: Long): Execution? {
        try {
            val response = request("GET", "/obligations/$id/execution")
            return readExecution(response)
        } catch (error: FieldApiException) {
            if (error.status == 404) {
                return null
            }
            throw error
        }
    }

    suspend fun start(id: Long): Execution {
        val response = request("POST", "/obligations/$id/execution")
        return readExecution(response)
    }

    suspend fun step(execution: Execution, id: Long, completed: Boolean): Execution {
        val body = JSONObject()
        body.put("completed", completed)
        body.put("expectedVersion", execution.version)

        val path = "/executions/${execution.id}/steps/$id"
        val response = request("PUT", path, body = body)
        return readExecution(response)
    }

    suspend fun upload(execution: Execution, id: Long, file: File): Execution {
        val path = "/executions/${execution.id}/photo-requirements/$id/photo"
        val response = request("POST", path, photo = file, version = execution.version)
        return readExecution(response)
    }

    suspend fun report(
        execution: Execution,
        reason: String,
        description: String,
        operationId: String
    ): Execution {
        val body = JSONObject()
        body.put("reason", reason)
        body.put("description", description)
        body.put("operationId", operationId)
        body.put("expectedVersion", execution.version)

        val path = "/executions/${execution.id}/exceptions"
        val response = request("POST", path, body = body)
        return readExecution(response)
    }

    suspend fun submit(execution: Execution): Execution {
        val body = JSONObject()
        body.put("expectedVersion", execution.version)

        val path = "/executions/${execution.id}/submit"
        val response = request("POST", path, body = body)
        return readExecution(response)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        photo: File? = null,
        version: Long? = null
    ): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = URI("$root/api/v1$path").toURL()
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")

                if (photo != null) {
                    writePhoto(connection, photo, version)
                } else if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val bytes = body.toString().toByteArray(Charsets.UTF_8)
                    connection.outputStream.use { output ->
                        output.write(bytes)
                    }
                }

                val status = connection.responseCode
                val success = status in 200..299
                val stream = if (success) connection.inputStream else connection.errorStream
                var responseText = ""
                if (stream != null) {
                    responseText = stream.bufferedReader().use { reader -> reader.readText() }
                }

                if (!success) {
                    throw readApiError(status, responseText)
                }
                JSONObject(responseText)
            } finally {
                connection.disconnect()
            }
        }
    }


    private fun writePhoto(connection: HttpURLConnection, photo: File, version: Long?) {
        require(photo.length() in 1..(5L * 1024 * 1024)) {
            "La fotografía debe pesar hasta 5 MB."
        }
        val boundary = "field-${UUID.randomUUID()}"
        connection.doOutput = true
        connection.setChunkedStreamingMode(8192)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val versionPart = "--$boundary\r\nContent-Disposition: form-data; name=\"expectedVersion\"\r\n\r\n$version\r\n"
        val photoPart = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"capture.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n"
        val endPart = "\r\n--$boundary--\r\n"

        connection.outputStream.use { output ->
            output.write(versionPart.toByteArray(Charsets.UTF_8))
            output.write(photoPart.toByteArray(Charsets.UTF_8))
            photo.inputStream().use { input ->
                input.copyTo(output)
            }
            output.write(endPart.toByteArray(Charsets.UTF_8))
        }
    }

    private fun readApiError(status: Int, responseText: String): FieldApiException {
        var code = ""
        var message = "No se pudo realizar la operación ($status)."
        val error = try {
            JSONObject(responseText)
        } catch (_: Exception) {
            null
        }

        if (error != null) {
            code = error.optString("code")
            val serverMessage = error.optString("message")
            if (serverMessage.isNotBlank()) {
                message = serverMessage
            }
        }
        return FieldApiException(status, code, message)
    }

    suspend fun photo(id: Long): ByteArray {
        return withContext(Dispatchers.IO) {
            val url = URI("$root/api/v1/evidence/$id/content").toURL()
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.useCaches = false
                connection.setRequestProperty("Authorization", "Bearer $token")

                val status = connection.responseCode
                if (status !in 200..299) {
                    throw FieldApiException(status, "PHOTO_DOWNLOAD_FAILED", "No se pudo consultar la fotografía.")
                }

                connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count = input.read(buffer)
                    while (count >= 0) {
                        require(output.size() + count <= 5 * 1024 * 1024) {
                            "La fotografía supera el tamaño permitido."
                        }
                        output.write(buffer, 0, count)
                        count = input.read(buffer)
                    }
                    output.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun readExecution(response: JSONObject): Execution {
        val steps = readSteps(response.getJSONArray("steps"))
        val photos = readPhotos(response.getJSONArray("photoRequirements"))
        val exceptions = readExceptions(response.getJSONArray("exceptions"))

        return Execution(
            id = response.getLong("id"),
            state = response.getString("state"),
            obligationStatus = response.getString("obligationStatus"),
            version = response.getLong("version"),
            canEdit = response.getBoolean("canEdit"),
            steps = steps,
            photos = photos,
            exceptions = exceptions
        )
    }

    private fun readSteps(array: JSONArray): List<WorkStep> {
        val steps = mutableListOf<WorkStep>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val step = WorkStep(
                id = item.getLong("id"),
                title = item.getString("title"),
                instructions = readOptionalString(item, "instructions"),
                completed = item.optBoolean("completed")
            )
            steps.add(step)
        }
        return steps
    }

    private fun readPhotos(array: JSONArray): List<PhotoRequirement> {
        val photos = mutableListOf<PhotoRequirement>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            var evidenceId: Long? = null
            if (!item.isNull("evidenceId")) {
                evidenceId = item.getLong("evidenceId")
            }
            val photo = PhotoRequirement(
                id = item.getLong("id"),
                title = item.getString("title"),
                description = readOptionalString(item, "description"),
                evidenceId = evidenceId
            )
            photos.add(photo)
        }
        return photos
    }

    private fun readExceptions(array: JSONArray): List<WorkException> {
        val exceptions = mutableListOf<WorkException>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val exception = WorkException(
                reason = item.getString("reason"),
                description = item.getString("description"),
                operationId = item.getString("operationId")
            )
            exceptions.add(exception)
        }
        return exceptions
    }

    private fun readOptionalString(item: JSONObject, key: String): String? {
        if (item.isNull(key)) {
            return null
        }
        return item.getString(key)
    }
}
