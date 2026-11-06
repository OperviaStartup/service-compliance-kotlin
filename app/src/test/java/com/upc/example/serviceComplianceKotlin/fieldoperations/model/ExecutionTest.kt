package com.upc.example.serviceComplianceKotlin.fieldoperations.model

import com.upc.example.serviceComplianceKotlin.fieldoperations.data.FieldRepository
import org.junit.Assert.*
import org.junit.Test

class ExecutionTest {
    private fun execution(checked: Boolean, evidence: Long?, editable: Boolean = true) = Execution(
        7, "IN_PROGRESS", "IN_PROGRESS", 4, editable,
        listOf(WorkStep(2, "Paso", null, checked)),
        listOf(PhotoRequirement(3, "Foto", null, evidence)), emptyList())

    @Test fun missingPhotoOrStepPreventsSubmit() {
        assertFalse(execution(true, null).readyToSubmit)
        assertFalse(execution(false, 8).readyToSubmit)
        assertTrue(execution(true, 8).readyToSubmit)
    }
    @Test fun serverEditPermissionPreventsSubmit() {
        assertFalse(execution(true, 8, false).readyToSubmit)
    }
    @Test fun explicitlyNoPhotosStillRequiresSteps() {
        assertTrue(execution(true, null).copy(photos = emptyList()).readyToSubmit)
        assertFalse(execution(true, null).copy(steps = emptyList(), photos = emptyList()).readyToSubmit)
    }
    @Test fun credentialsAreNotAllowedInUrlAndRemoteHttpIsRejected() {
        listOf("http://example.org", "https://user:password@example.org", "https://example.org?token=x").forEach {
            assertThrows(IllegalArgumentException::class.java) { FieldRepository(it, "test-session") }
        }
    }
}
