package com.upc.example.serviceComplianceKotlin.fieldoperations.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.upc.example.serviceComplianceKotlin.fieldoperations.screens.DetailScreen
import com.upc.example.serviceComplianceKotlin.fieldoperations.screens.ExecutionScreen
import com.upc.example.serviceComplianceKotlin.fieldoperations.screens.PhotoScreen
import com.upc.example.serviceComplianceKotlin.fieldoperations.screens.ExceptionScreen
import com.upc.example.serviceComplianceKotlin.fieldoperations.viewmodel.FieldState
import com.upc.example.serviceComplianceKotlin.fieldoperations.viewmodel.FieldViewModel

private const val DETAIL = "detail"
private const val EXECUTION = "execution"
private const val EXCEPTION = "exception"
private const val PHOTO = "photo"

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
            DetailScreen(state, onStart = { model.start() }, onContinue = { nav.navigate(EXECUTION) })
        }
        composable(EXECUTION) {
            ExecutionScreen(state, onCheck = model::check,
                onPhoto = { nav.navigate("$PHOTO/$it") }, onReport = { nav.navigate(EXCEPTION) },
                onSubmit = model::submit, onExit = onExit)
        }
        composable("$PHOTO/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull()
            val requirement = state.execution?.photos?.find { it.id == id }
            if (requirement != null) PhotoScreen(requirement, state,
                onUpload = { model.upload(requirement.id, it) }, onLoad = model::loadPhoto,
                onDone = { nav.popBackStack() })
        }
        composable(EXCEPTION) {
            ExceptionScreen(state, onReport = model::report, onDone = { nav.popBackStack() })
        }
    }
}
