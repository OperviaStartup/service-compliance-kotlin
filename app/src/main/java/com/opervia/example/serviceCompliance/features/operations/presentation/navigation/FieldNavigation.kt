package com.opervia.example.serviceCompliance.features.operations.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.DetailScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.EvidenceScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.ExceptionReportScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.ExceptionScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.ExecutionScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.ExecutionProgressScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldState
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldViewModel
import com.opervia.example.serviceCompliance.features.operations.presentation.SignatureEvidenceRoute
import com.opervia.example.serviceCompliance.features.operations.presentation.StructuredEvidenceRoute
import com.opervia.example.serviceCompliance.features.operations.presentation.PhotoEvidenceRoute
import com.opervia.example.serviceCompliance.features.operations.data.LocalFieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoKind

private const val DETAIL = "detail"
private const val EXECUTION = "execution"
private const val EXCEPTION = "exception"
private const val EVIDENCE = "evidence"

@Composable
internal fun FieldNavigation(
    model: FieldViewModel,
    state: FieldState,
    nav: NavHostController,
    onExit: () -> Unit,
    repository: OperationsRepository,
    localStore: LocalFieldOperationsStore,
    userId: Long,
    obligationId: Long,
    modifier: Modifier = Modifier
) {
    NavHost(nav, startDestination = DETAIL, modifier = modifier) {
        composable(DETAIL) {
            DetailScreen(state,
                onStart = { model.start { nav.navigate(EXECUTION) { launchSingleTop = true } } },
                onContinue = { nav.navigate(EXECUTION) { launchSingleTop = true } })
        }
        composable(EXECUTION) {
            LaunchedEffect(Unit) { model.refreshAfterEvidence() }
            if (state.detail == null) {
                InfoNote("Actualiza para recuperar el detalle y el avance de esta actividad.")
            } else if (state.detail.startsAt != null) {
                ExecutionProgressScreen(state, model::updateStep, model::submitFlow,
                    onPhoto = { kind, requirementId, evidenceId ->
                        nav.navigate("photo/${kind.name}/$requirementId?evidenceId=${evidenceId ?: -1}") { launchSingleTop = true }
                    }, onReport = { model.openException(); nav.navigate(EXCEPTION) { launchSingleTop = true } },
                    onStructuredEvidence = { requirementId -> nav.navigate("structured/$requirementId") { launchSingleTop = true } },
                    onSignature = { requirementId, evidenceId ->
                        nav.navigate("signature/$requirementId?evidenceId=${evidenceId ?: -1}") { launchSingleTop = true }
                    }, onExit = onExit)
            } else {
                ExecutionScreen(state, onSubmit = model::submit,
                    onEvidence = { nav.navigate(EVIDENCE) }, onReport = { nav.navigate(EXCEPTION) }, onExit = onExit)
            }
        }
        composable("photo/{kind}/{requirementId}?evidenceId={evidenceId}", arguments = listOf(
            navArgument("kind") { type = NavType.StringType },
            navArgument("requirementId") { type = NavType.LongType },
            navArgument("evidenceId") { type = NavType.LongType; defaultValue = -1L }
        )) { entry ->
            val arguments = requireNotNull(entry.arguments)
            val kind = PhotoKind.valueOf(requireNotNull(arguments.getString("kind")))
            PhotoEvidenceRoute(repository, localStore, userId, obligationId, kind,
                arguments.getLong("requirementId"), arguments.getLong("evidenceId").takeIf { it > 0 },
                onDone = { nav.popBackStack() })
        }
        composable("structured/{requirementId}", arguments = listOf(navArgument("requirementId") { type = NavType.LongType })) { entry ->
            StructuredEvidenceRoute(repository, localStore, userId, obligationId,
                requireNotNull(entry.arguments).getLong("requirementId"), onDone = { nav.popBackStack() })
        }
        composable("signature/{requirementId}?evidenceId={evidenceId}", arguments = listOf(
            navArgument("requirementId") { type = NavType.LongType },
            navArgument("evidenceId") { type = NavType.LongType; defaultValue = -1L }
        )) { entry ->
            val arguments = requireNotNull(entry.arguments)
            SignatureEvidenceRoute(repository, localStore, userId, obligationId,
                arguments.getLong("requirementId"), arguments.getLong("evidenceId").takeIf { it > 0 }, onDone = { nav.popBackStack() })
        }
        composable(EXCEPTION) {
            LaunchedEffect(Unit) { model.refreshAfterEvidence() }
            if (state.detail == null) {
                InfoNote("Actualiza para recuperar la actividad antes de reportar un impedimento.")
            } else if (state.detail.startsAt != null) {
                ExceptionReportScreen(state, onReport = model::reportException, onDone = { nav.popBackStack() })
            } else {
                ExceptionScreen(state, onReport = model::submit, onDone = onExit)
            }
        }
        composable(EVIDENCE) {
            LaunchedEffect(state.execution?.id) { model.refreshEvidence() }
            EvidenceScreen(state, onAdd = model::addEvidence, onDone = { nav.popBackStack() })
        }
    }
}
