package com.opervia.example.serviceCompliance.features.operations.data

import com.opervia.example.serviceCompliance.features.operations.domain.model.StructuredEvidenceDraft
import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExceptionReport
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExecutionSubmission
import com.opervia.example.serviceCompliance.features.operations.domain.repository.FieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.PhotoDraftStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.StructuredEvidenceStore
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoDraft
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoKind
import java.util.UUID
import kotlinx.coroutines.flow.first

private val Context.fieldOperationsDataStore by preferencesDataStore(name = "field_operations")

/** Persists the operator's cached obligations and confirmed executions, separated by user. */
class LocalFieldOperationsStore(context: Context) : FieldOperationsStore, PhotoDraftStore, StructuredEvidenceStore {
    private val dataStore: DataStore<Preferences> = context.applicationContext.fieldOperationsDataStore
    private val gson = Gson()
    private val obligationListType = object : TypeToken<List<AssignedObligation>>() {}.type
    private val executionListType = object : TypeToken<List<ExecutionRecord>>() {}.type

    suspend fun loadObligations(userId: Long): List<AssignedObligation> {
        val preferences = dataStore.data.first()
        return decodeObligations(preferences[obligationsKey(userId)])
    }

    suspend fun loadExecutions(userId: Long): List<ExecutionRecord> {
        val preferences = dataStore.data.first()
        return decodeExecutions(preferences[executionsKey(userId)])
    }

    suspend fun saveObligations(userId: Long, current: List<AssignedObligation>): List<AssignedObligation> {
        val updatedPreferences = dataStore.edit { preferences ->
            val historicalObligations = decodeObligations(preferences[obligationsKey(userId)])
            val executions = decodeExecutions(preferences[executionsKey(userId)])
            val obligationIdsWithExecution = executions.map { it.obligationId }.toSet()
            val merged = (current + historicalObligations.filter { it.id in obligationIdsWithExecution })
                .distinctBy { it.id }
            preferences[obligationsKey(userId)] = gson.toJson(merged)
        }
        return decodeObligations(updatedPreferences[obligationsKey(userId)])
    }

    override suspend fun saveExecution(
        userId: Long,
        execution: ExecutionRecord,
        obligation: AssignedObligation?
    ) {
        dataStore.edit { preferences ->
            val executions = decodeExecutions(preferences[executionsKey(userId)])
            preferences[executionsKey(userId)] = gson.toJson(
                listOf(execution) + executions.filterNot { it.id == execution.id }
            )

            if (obligation != null) {
                val obligations = decodeObligations(preferences[obligationsKey(userId)])
                preferences[obligationsKey(userId)] = gson.toJson(
                    (listOf(obligation) + obligations.filterNot { it.id == obligation.id })
                        .distinctBy { it.id }
                )
            }
        }
    }

    override suspend fun startKey(userId: Long, obligationId: Long): String {
        val key = stringPreferencesKey("start_${userId}_$obligationId")
        val updated = dataStore.edit { preferences ->
            if (preferences[key] == null) preferences[key] = "start-${UUID.randomUUID()}"
        }
        return checkNotNull(updated[key])
    }

    override suspend fun pendingSubmission(userId: Long, obligationId: Long): PendingExecutionSubmission? {
        val json = dataStore.data.first()[submissionKey(userId, obligationId)] ?: return null
        return gson.fromJson(json, PendingExecutionSubmission::class.java)
    }

    override suspend fun saveSubmission(userId: Long, obligationId: Long, submission: PendingExecutionSubmission) {
        dataStore.edit { it[submissionKey(userId, obligationId)] = gson.toJson(submission) }
    }

    override suspend fun clearSubmission(userId: Long, obligationId: Long) {
        dataStore.edit { it.remove(submissionKey(userId, obligationId)) }
    }

    override suspend fun pendingException(userId: Long, obligationId: Long): PendingExceptionReport? {
        val json = dataStore.data.first()[exceptionKey(userId, obligationId)] ?: return null
        return gson.fromJson(json, PendingExceptionReport::class.java)
    }

    override suspend fun saveException(userId: Long, obligationId: Long, report: PendingExceptionReport) {
        dataStore.edit { it[exceptionKey(userId, obligationId)] = gson.toJson(report) }
    }

    override suspend fun clearException(userId: Long, obligationId: Long) {
        dataStore.edit { it.remove(exceptionKey(userId, obligationId)) }
    }

    private fun exceptionKey(userId: Long, obligationId: Long) =
        stringPreferencesKey("exception_${userId}_$obligationId")

    private fun submissionKey(userId: Long, obligationId: Long) =
        stringPreferencesKey("submit_${userId}_$obligationId")

    override suspend fun photoDraft(userId: Long, executionId: Long, kind: PhotoKind, requirementId: Long): PhotoDraft? {
        val json = dataStore.data.first()[photoKey(userId, executionId, kind, requirementId)] ?: return null
        return gson.fromJson(json, PhotoDraft::class.java)
    }

    override suspend fun hasPendingSubmission(userId: Long, obligationId: Long) =
        pendingSubmission(userId, obligationId) != null || pendingException(userId, obligationId) != null

    override suspend fun savePhotoDraft(userId: Long, draft: PhotoDraft) {
        dataStore.edit { it[photoKey(userId, draft.executionId, draft.kind, draft.requirementId)] = gson.toJson(draft) }
    }

    override suspend fun removePhotoDraft(userId: Long, draft: PhotoDraft) {
        dataStore.edit { it.remove(photoKey(userId, draft.executionId, draft.kind, draft.requirementId)) }
    }

    override suspend fun structuredDraft(userId: Long, executionId: Long, requirementId: Long): StructuredEvidenceDraft? {
        val json = dataStore.data.first()[structuredKey(userId, executionId, requirementId)] ?: return null
        return gson.fromJson(json, StructuredEvidenceDraft::class.java)
    }

    override suspend fun saveStructuredDraft(userId: Long, draft: StructuredEvidenceDraft) {
        dataStore.edit { it[structuredKey(userId, draft.executionId, draft.requirementId)] = gson.toJson(draft) }
    }

    override suspend fun removeStructuredDraft(userId: Long, draft: StructuredEvidenceDraft) {
        dataStore.edit { it.remove(structuredKey(userId, draft.executionId, draft.requirementId)) }
    }

    private fun structuredKey(userId: Long, executionId: Long, requirementId: Long) =
        stringPreferencesKey("structured_${userId}_${executionId}_$requirementId")

    private fun photoKey(userId: Long, executionId: Long, kind: PhotoKind, requirementId: Long) =
        stringPreferencesKey("photo_${userId}_${executionId}_${kind.name}_$requirementId")

    private fun obligationsKey(userId: Long) = stringPreferencesKey("obligations_$userId")

    private fun executionsKey(userId: Long) = stringPreferencesKey("executions_$userId")

    private fun decodeObligations(json: String?): List<AssignedObligation> {
        if (json.isNullOrBlank()) return emptyList()
        return gson.fromJson<List<AssignedObligation>>(json, obligationListType).orEmpty()
    }

    private fun decodeExecutions(json: String?): List<ExecutionRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return gson.fromJson<List<ExecutionRecord>>(json, executionListType).orEmpty()
    }
}
