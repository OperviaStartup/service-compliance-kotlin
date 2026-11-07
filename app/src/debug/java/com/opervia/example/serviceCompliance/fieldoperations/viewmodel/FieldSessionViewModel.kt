package com.opervia.example.serviceCompliance.fieldoperations.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

internal data class FieldConnection(val baseUrl: String, val token: String, val obligationId: Long)

/** Keeps the supplied test session in memory across rotation; never writes credentials to disk. */
internal class FieldSessionViewModel : ViewModel() {
    var connection by mutableStateOf<FieldConnection?>(null)
        private set

    fun open(value: FieldConnection) { connection = value }
    fun clear() { connection = null }
}
