package com.opervia.example.serviceCompliance.features.operations.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.DetailScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.EvidenceScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.ExceptionScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.ExecutionScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldState
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldViewModel

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
    modifier: Modifier = Modifier
) {
    NavHost(nav, startDestination = DETAIL, modifier = modifier) {
        composable(DETAIL) {
            DetailScreen(state, onStart = { nav.navigate(EXECUTION) }, onContinue = { nav.navigate(EXECUTION) })
        }
        composable(EXECUTION) {
            ExecutionScreen(state, onSubmit = model::submit,
                onEvidence = { nav.navigate(EVIDENCE) }, onReport = { nav.navigate(EXCEPTION) }, onExit = onExit)
        }
        composable(EXCEPTION) {
            ExceptionScreen(state, onReport = model::submit, onDone = onExit)
        }
        composable(EVIDENCE) {
            LaunchedEffect(state.execution?.id) { model.refreshEvidence() }
            EvidenceScreen(state, onAdd = model::addEvidence, onDone = { nav.popBackStack() })
        }
    }
}
