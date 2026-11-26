package com.opervia.example.serviceCompliance.features.operations.data

import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.math.BigDecimal
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** Maps the remote REST contract to the feature's domain models. */
internal class FieldRepository(
    private val api: OperationsApi,
    private val gson: Gson = Gson()
) : OperationsRepository {
    override suspend fun executionHistory(query: ExecutionHistoryQuery, cursor: String?): ExecutionHistoryPage = apiCall {
        val response = api.executionHistory(query.fromDate.toString(), query.toDate.toString(),
            query.query.trim().takeIf(String::isNotBlank), query.reviewStatus?.name, cursor)
        ExecutionHistoryPage(response.items.map { item ->
            val state = ExecutionState.valueOf(item.state)
            require(state == ExecutionState.SUBMITTED || state == ExecutionState.LEGACY)
            require(state != ExecutionState.SUBMITTED || item.submittedAt != null)
            val decision = item.complianceResult?.let {
                val outcome = HistoryReviewStatus.valueOf(it.outcome)
                require(outcome != HistoryReviewStatus.PENDING_REVIEW)
                HistoryComplianceResult(it.id, it.caseId, outcome, it.decisionVersion, it.decidedAt, it.explanation)
            }
            ExecutionHistoryItem(item.id, item.obligationId, item.activity, item.siteName, item.timezone,
                state, ExecutionResult.valueOf(item.result), item.executedAt, item.submittedAt, item.notes,
                HistorySynchronizationState.valueOf(item.synchronizationState), decision)
        }, response.nextCursor, response.hasMore, response.asOf).also { it.validate() }
    }

    override suspend fun assignedObligations(): List<AssignedObligation> = apiCall {
        api.assignedObligations().map { it.toDomain() }
    }

    override suspend fun obligationDetail(id: Long): ObligationDetail = apiCall {
        api.obligationDetail(id).toDomain()
    }

    override suspend fun currentExecution(obligationId: Long): ExecutionFlow? = try {
        apiCall { api.currentExecution(obligationId).toDomain() }
    } catch (failure: OperationsFailure) {
        if (failure.kind == OperationsFailureKind.NOT_FOUND) null else throw failure
    }

    override suspend fun startExecution(obligationId: Long, idempotencyKey: String): ExecutionFlow = apiCall {
        api.startExecution(obligationId, idempotencyKey).toDomain()
    }

    override suspend fun updateStep(
        executionId: Long, stepId: Long, completed: Boolean, expectedVersion: Long
    ): ExecutionFlow = apiCall {
        api.updateStep(executionId, stepId, UpdateExecutionStepRequest(completed, expectedVersion)).toDomain()
    }

    override suspend fun submitExecution(submission: PendingExecutionSubmission): ExecutionFlow = apiCall {
        api.submitExecution(submission.executionId, submission.idempotencyKey,
            SubmitExecutionRequest(submission.expectedVersion, submission.notes)).toDomain()
    }

    override suspend fun reportException(report: PendingExceptionReport): ExecutionFlow = apiCall {
        api.reportException(report.executionId, report.idempotencyKey,
            ReportExceptionRequest(report.reason.name, report.description, report.stepId, report.operationId,
                report.evidenceIds, report.expectedVersion)).toDomain()
    }

    override suspend fun uploadPhoto(upload: PhotoUpload): Long = apiCall {
        val draft = upload.draft
        require(upload.bytes.isNotEmpty() && upload.bytes.size <= MAX_PHOTO_BYTES)
        val mime = requireNotNull(draft.contentType)
        require(mime == "image/jpeg" || mime == "image/png")
        if (upload.type == EvidenceType.SIGNATURE) {
            require(draft.kind == PhotoKind.EVIDENCE_REQUIREMENT && mime == "image/png" && upload.bytes.size <= MAX_SIGNATURE_BYTES)
        } else require(upload.type == EvidenceType.PHOTO)
        val extension = if (mime == "image/png") "png" else "jpg"
        val file = MultipartBody.Part.createFormData("file", "evidence.$extension", upload.bytes.toRequestBody(mime.toMediaType()))
        val version = requireNotNull(draft.expectedVersion).toString().asTextPart()
        val capturedAt = requireNotNull(draft.capturedAt).asTextPart()
        if (draft.kind == PhotoKind.PHOTO_REQUIREMENT) {
            val response = api.uploadRequiredPhoto(draft.executionId, draft.requirementId, file, version, capturedAt)
            response.photoRequirements.firstOrNull { it.id == draft.requirementId }?.evidenceId
                ?: throw OperationsFailure(OperationsFailureKind.UNKNOWN, "El servidor no confirmó la fotografía.")
        } else {
            val location = draft.location
            api.uploadEvidencePhoto(draft.executionId, draft.requirementId, draft.idempotencyKey,
                file, version, capturedAt, location?.latitude.asDecimalPart(),
                location?.longitude.asDecimalPart(), location?.accuracyMeters.asDecimalPart()).id
        }
    }

    override suspend fun uploadStructuredEvidence(draft: StructuredEvidenceDraft): RequiredEvidence = apiCall {
        require(draft.type == EvidenceType.QR_CODE || draft.type == EvidenceType.LOCATION)
        api.uploadStructuredEvidence(draft.executionId, draft.requirementId, draft.idempotencyKey,
            StructuredEvidenceRequest(draft.textValue, draft.location?.latitude, draft.location?.longitude,
                draft.location?.accuracyMeters, draft.capturedAt, requireNotNull(draft.expectedVersion))).toDomain()
    }

    override suspend fun photoContent(evidenceId: Long): PrivatePhoto = withContext(Dispatchers.IO) { apiCall {
        api.photoContent(evidenceId).use { response ->
            val mime = response.contentType()?.toString()?.substringBefore(';')
            if (mime !in setOf("image/jpeg", "image/png") || response.contentLength() > MAX_PHOTO_BYTES) {
                throw OperationsFailure(OperationsFailureKind.UNKNOWN, "El servidor no devolvió una fotografía válida.")
            }
            val output = java.io.ByteArrayOutputStream()
            response.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read != -1) {
                    if (output.size() + read > MAX_PHOTO_BYTES) {
                        throw OperationsFailure(OperationsFailureKind.UNKNOWN, "La fotografía supera el tamaño permitido.")
                    }
                    output.write(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            if (output.size() == 0) throw OperationsFailure(OperationsFailureKind.UNKNOWN, "La fotografía está vacía.")
            PrivatePhoto(output.toByteArray(), requireNotNull(mime))
        }
    } }

    private fun String.asTextPart() = toRequestBody("text/plain".toMediaType())
    private fun Double?.asDecimalPart() = this?.let { BigDecimal.valueOf(it).toPlainString().asTextPart() }

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
        val apiError = runCatching {
            response()?.errorBody()?.string()?.let { gson.fromJson(it, ApiErrorDto::class.java) }
        }.getOrNull()
        return OperationsFailure(
            kind = kind,
            message = apiError?.message?.takeIf(String::isNotBlank) ?: "No se pudo completar la operación (${code()}).",
            code = apiError?.code
        )
    }

    private fun ExecutionFlowDto.toDomain() = ExecutionFlow(
        id = id,
        obligationId = obligationId,
        operatorId = operatorId,
        state = ExecutionState.valueOf(state),
        obligationStatus = obligationStatus,
        result = result?.let(ExecutionResult::valueOf),
        startedAt = startedAt,
        submittedAt = submittedAt,
        startsAt = startsAt,
        dueAt = dueAt,
        timezone = timezone,
        version = version,
        canEdit = canEdit,
        notes = notes,
        steps = steps.sortedBy { it.position }.map {
            ExecutionStep(it.id, it.title, it.instructions, it.position, it.completed)
        },
        photoRequirements = photoRequirements.sortedBy { it.position }.map {
            ExecutionPhotoRequirement(it.id, it.title, it.description, it.position, it.evidenceId, it.contentPath)
        },
        evidenceRequirements = evidenceRequirements.sortedBy { it.position }.map { requirement ->
            ExecutionEvidenceRequirement(requirement.id, EvidenceType.valueOf(requirement.type),
                requirement.required, requirement.minimumCount, requirement.maximumCount, requirement.position,
                requirement.constraints, requirement.evidence.map {
                    it.toDomain()
                })
        },
        exceptions = exceptions.map {
            ExecutionException(it.id, it.stepId, ExceptionReason.valueOf(it.reason), it.description,
                it.reportedBy, it.reportedAt, it.operationId, it.evidenceIds)
        }
    )

    private fun RequiredEvidenceDto.toDomain() = RequiredEvidence(id, EvidenceType.valueOf(type), contentPath,
        textValue, capturedAt, checksum, latitude, longitude, accuracyMeters)

    private fun AssignedObligationDto.toDomain() = AssignedObligation(
        id = id,
        title = title,
        description = description.orEmpty(),
        siteName = siteName,
        status = status,
        dueAt = dueAt
    )

    private fun ObligationDetailDto.toDomain() = ObligationDetail(
        obligation = AssignedObligation(
            id = id,
            title = title,
            description = description.orEmpty(),
            siteName = siteName,
            status = status,
            dueAt = dueAt
        ),
        startsAt = startsAt,
        timezone = timezone,
        steps = steps.sortedBy { it.position }.map { step ->
            ObligationStep(step.id, step.title, step.instructions, step.position)
        },
        photoRequirements = photoRequirements.sortedBy { it.position }.map { requirement ->
            ObligationPhotoRequirement(requirement.id, requirement.title, requirement.description, requirement.position)
        }
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
