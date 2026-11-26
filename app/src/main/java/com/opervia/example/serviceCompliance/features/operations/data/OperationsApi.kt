package com.opervia.example.serviceCompliance.features.operations.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Streaming
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Query
import retrofit2.http.Path

/** Retrofit endpoints currently supported by the field-operation API. */
internal interface OperationsApi {
    @GET("api/v1/executions")
    suspend fun executionHistory(
        @Query("fromDate") fromDate: String,
        @Query("toDate") toDate: String,
        @Query("query") query: String?,
        @Query("reviewStatus") reviewStatus: String?,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int = 50
    ): ExecutionHistoryPageDto

    @GET("api/v1/obligations")
    suspend fun assignedObligations(): List<AssignedObligationDto>

    @GET("api/v1/obligations/{id}")
    suspend fun obligationDetail(@Path("id") id: Long): ObligationDetailDto

    @GET("api/v1/obligations/{id}/execution")
    suspend fun currentExecution(@Path("id") obligationId: Long): ExecutionFlowDto

    @POST("api/v1/obligations/{id}/execution")
    suspend fun startExecution(
        @Path("id") obligationId: Long,
        @Header("Idempotency-Key") idempotencyKey: String
    ): ExecutionFlowDto

    @PUT("api/v1/executions/{id}/steps/{stepId}")
    suspend fun updateStep(
        @Path("id") executionId: Long,
        @Path("stepId") stepId: Long,
        @Body request: UpdateExecutionStepRequest
    ): ExecutionFlowDto

    @POST("api/v1/executions/{id}/submit")
    suspend fun submitExecution(
        @Path("id") executionId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SubmitExecutionRequest
    ): ExecutionFlowDto

    @POST("api/v1/executions/{id}/exceptions")
    suspend fun reportException(
        @Path("id") executionId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: ReportExceptionRequest
    ): ExecutionFlowDto

    @Multipart
    @POST("api/v1/executions/{id}/photo-requirements/{requirementId}/photo")
    suspend fun uploadRequiredPhoto(
        @Path("id") executionId: Long,
        @Path("requirementId") requirementId: Long,
        @Part file: MultipartBody.Part,
        @Part("expectedVersion") expectedVersion: RequestBody,
        @Part("capturedAt") capturedAt: RequestBody
    ): ExecutionFlowDto

    @Multipart
    @POST("api/v1/executions/{id}/evidence-requirements/{requirementId}/evidence")
    suspend fun uploadEvidencePhoto(
        @Path("id") executionId: Long,
        @Path("requirementId") requirementId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part file: MultipartBody.Part,
        @Part("expectedVersion") expectedVersion: RequestBody,
        @Part("capturedAt") capturedAt: RequestBody,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?,
        @Part("accuracyMeters") accuracyMeters: RequestBody?
    ): RequiredEvidenceDto

    @POST("api/v1/executions/{id}/evidence-requirements/{requirementId}/evidence")
    suspend fun uploadStructuredEvidence(
        @Path("id") executionId: Long,
        @Path("requirementId") requirementId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: StructuredEvidenceRequest
    ): RequiredEvidenceDto

    @Streaming
    @GET("api/v1/evidence/{id}/content")
    suspend fun photoContent(@Path("id") evidenceId: Long): ResponseBody

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

internal data class ApiErrorDto(val message: String?, val code: String?)
