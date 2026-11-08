package com.opervia.example.serviceCompliance.features.operations.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Retrofit endpoints currently supported by the field-operation API. */
internal interface OperationsApi {
    @GET("api/v1/obligations")
    suspend fun assignedObligations(): List<AssignedObligationDto>

    @POST("api/v1/executions")
    suspend fun registerExecution(@Body request: RegisterExecutionRequest): ExecutionDto

    @GET("api/v1/executions/{id}")
    suspend fun execution(@Path("id") id: Long): ExecutionDto

    @GET("api/v1/executions/{id}/evidence")
    suspend fun evidence(@Path("id") id: Long): List<EvidenceDto>

    @POST("api/v1/executions/{id}/evidence")
    suspend fun addEvidence(
        @Path("id") executionId: Long,
        @Body request: AddEvidenceRequest
    ): EvidenceDto
}

internal data class AssignedObligationDto(
    val id: Long,
    val title: String,
    val description: String?,
    val siteName: String,
    val status: String,
    val dueAt: String
)

internal data class ExecutionDto(
    val id: Long,
    val obligationId: Long,
    val operatorId: Long,
    val result: String,
    val notes: String?,
    val executedAt: String
)

internal data class EvidenceDto(
    val id: Long,
    val executionId: Long,
    val type: String,
    val url: String,
    val description: String?,
    val createdAt: String
)

internal data class RegisterExecutionRequest(
    val obligationId: Long,
    val result: String,
    val notes: String?
)

internal data class AddEvidenceRequest(
    val type: String,
    val url: String,
    val description: String?
)

internal data class ApiErrorDto(val message: String?)
