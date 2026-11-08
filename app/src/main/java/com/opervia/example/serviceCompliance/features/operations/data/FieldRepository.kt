package com.opervia.example.serviceCompliance.features.operations.data

import com.google.gson.Gson
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailure
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailureKind
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** Maps the remote REST contract to the feature's domain models. */
internal class FieldRepository(
    private val api: OperationsApi,
    private val gson: Gson = Gson()
) : OperationsRepository {
    override suspend fun assignedObligations(): List<AssignedObligation> = apiCall {
        api.assignedObligations().map { it.toDomain() }
    }

    override suspend fun registerExecution(
        obligationId: Long,
        result: ExecutionResult,
        notes: String?
    ): ExecutionRecord = apiCall {
        api.registerExecution(
            RegisterExecutionRequest(
                obligationId = obligationId,
                result = result.name,
                notes = notes.normalizedOrNull()
            )
        ).toDomain()
    }

    override suspend fun execution(id: Long): ExecutionRecord = apiCall {
        api.execution(id).toDomain()
    }

    override suspend fun evidence(id: Long): List<EvidenceRecord> = apiCall {
        api.evidence(id).map { it.toDomain() }
    }

    override suspend fun addEvidence(
        executionId: Long,
        type: EvidenceType,
        url: String,
        description: String?
    ): EvidenceRecord = apiCall {
        api.addEvidence(
            executionId,
            AddEvidenceRequest(
                type = type.name,
                url = url.trim(),
                description = description.normalizedOrNull()
            )
        ).toDomain()
    }

    private suspend fun <T> apiCall(request: suspend () -> T): T = try {
        request()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: HttpException) {
        throw error.toOperationsFailure()
    } catch (error: IOException) {
        throw OperationsFailure(
            OperationsFailureKind.SERVER,
            "No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo."
        )
    }

    private fun HttpException.toOperationsFailure(): OperationsFailure {
        val kind = when (code()) {
            400 -> OperationsFailureKind.INVALID_REQUEST
            401 -> OperationsFailureKind.UNAUTHENTICATED
            403 -> OperationsFailureKind.FORBIDDEN
            404 -> OperationsFailureKind.NOT_FOUND
            409 -> OperationsFailureKind.CONFLICT
            in 500..599 -> OperationsFailureKind.SERVER
            else -> OperationsFailureKind.UNKNOWN
        }
        val apiMessage = runCatching {
            response()?.errorBody()?.string()?.let { gson.fromJson(it, ApiErrorDto::class.java)?.message }
        }.getOrNull()?.takeIf(String::isNotBlank)
        return OperationsFailure(
            kind = kind,
            message = apiMessage ?: "No se pudo completar la operación (${code()})."
        )
    }

    private fun AssignedObligationDto.toDomain() = AssignedObligation(
        id = id,
        title = title,
        description = description.orEmpty(),
        siteName = siteName,
        status = status,
        dueAt = dueAt
    )

    private fun ExecutionDto.toDomain() = ExecutionRecord(
        id = id,
        obligationId = obligationId,
        operatorId = operatorId,
        result = ExecutionResult.valueOf(result),
        notes = notes,
        executedAt = executedAt
    )

    private fun EvidenceDto.toDomain() = EvidenceRecord(
        id = id,
        executionId = executionId,
        type = EvidenceType.valueOf(type),
        url = url,
        description = description,
        createdAt = createdAt
    )

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
