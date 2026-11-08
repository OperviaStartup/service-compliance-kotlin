package com.opervia.example.serviceCompliance.features.operations.data

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
import kotlinx.coroutines.flow.first

private val Context.fieldOperationsDataStore by preferencesDataStore(name = "field_operations")

/** Persists the operator's cached obligations and confirmed executions, separated by user. */
class LocalFieldOperationsStore(context: Context) {
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

    suspend fun saveExecution(
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
