package com.opervia.example.serviceCompliance.features.operations

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.opervia.example.serviceCompliance.features.operations.data.LocalFieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExceptionReason
import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExceptionReport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExceptionStoreInstrumentedTest {
    @Test
    fun pendingReportSurvivesStoreRecreationAndIsIsolatedByAccountAndObligation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalFieldOperationsStore(context)
        // Claves propias del test: nunca se eliminan datos de la cuenta del usuario.
        val userId = 999999998L
        val obligationId = 999999998L
        val pending = PendingExceptionReport(999999998, "exception-${UUID.randomUUID()}",
            UUID.randomUUID().toString(), ExceptionReason.SAFETY_RISK, "Descripción de prueba",
            999999998, listOf(999999997), 4)
        try {
            store.saveException(userId, obligationId, pending)
            val reopened = LocalFieldOperationsStore(context)
            assertEquals(pending, reopened.pendingException(userId, obligationId))
            assertTrue(reopened.hasPendingSubmission(userId, obligationId))
            assertNull(reopened.pendingException(userId + 1, obligationId))
            assertNull(reopened.pendingException(userId, obligationId + 1))
            assertFalse(reopened.hasPendingSubmission(userId + 1, obligationId))
            store.clearException(userId, obligationId)
            assertNull(reopened.pendingException(userId, obligationId))
            assertFalse(reopened.hasPendingSubmission(userId, obligationId))
        } finally {
            store.clearException(userId, obligationId)
        }
    }
}
