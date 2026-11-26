package com.opervia.example.serviceCompliance.features.operations

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.HistoryScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.HistoryState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun submittedCompletionIsPendingUntilThereIsAnExplicitSupervisorDecision() {
        show(HistoryState(items = listOf(record(null)), asOf = "2026-11-25T20:00:00Z"))
        compose.onNodeWithText("Pendiente de revisión", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Resultado de ejecución: Completada").assertIsDisplayed()
        compose.onNodeWithText("Conforme", substring = true).assertDoesNotExist()
    }

    @Test fun explicitDecisionAndExplanationAreVisibleInHistoryDetails() {
        show(HistoryState(items = listOf(record(HistoryReviewStatus.COMPLIANT)), asOf = "2026-11-25T20:00:00Z"))
        compose.onNodeWithText("Recepción verificada").performClick()
        compose.onNodeWithText("Revisión: Conforme").assertIsDisplayed()
        compose.onNodeWithText("Evaluación confirmada por el supervisor.").assertIsDisplayed()
    }

    @Test fun evaluationFilterOffersAcceptedExceptionWithoutCallingItCompliant() {
        var selected: HistoryReviewStatus? = null
        compose.setContent {
            MaterialTheme {
                HistoryScreen(HistoryState(), {}, {}, { selected = it }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Filtros").performClick()
        compose.onNodeWithText("Excepción aceptada").assertIsDisplayed()
        compose.onNodeWithText("Excepción aceptada").performClick()
        compose.runOnIdle { assertEquals(HistoryReviewStatus.EXCEPTION_ACCEPTED, selected) }
    }

    @Test fun loadMoreUsesTheExistingHistoryAction() {
        var loads = 0
        show(HistoryState(items = listOf(record(null)), hasMore = true, nextCursor = "next-page"), onMore = { loads++ })
        compose.onNodeWithText("Cargar más").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, loads) }
    }

    private fun show(state: HistoryState, onMore: () -> Unit = {}) {
        compose.setContent { MaterialTheme { HistoryScreen(state, {}, {}, {}, {}, onMore, {}) } }
    }
    private fun record(outcome: HistoryReviewStatus?) = ExecutionHistoryItem(1, 2, "Recepción verificada", "Centro",
        "America/Lima", ExecutionState.SUBMITTED, ExecutionResult.COMPLETED, "2026-11-25T15:00:00Z",
        "2026-11-25T17:00:00Z", null, HistorySynchronizationState.SYNCED,
        outcome?.let { HistoryComplianceResult(3, 4, it, 1, "2026-11-25T18:00:00Z", "Evaluación confirmada por el supervisor.") })
}
