package com.upc.example.serviceComplianceKotlin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.upc.example.serviceComplianceKotlin.fieldoperations.FieldOperationsRoute
import com.upc.example.serviceComplianceKotlin.fieldoperations.screens.FieldEntryScreen
import com.upc.example.serviceComplianceKotlin.fieldoperations.viewmodel.FieldSessionViewModel

private const val ENTRY = "field-entry"
private const val FLOW = "field-flow"

/** MainActivity owns this graph, matching the professor's Navigation example. */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val session: FieldSessionViewModel = viewModel()

    NavHost(navController = navController, startDestination = ENTRY) {
        composable(ENTRY) {
            FieldEntryScreen(onOpen = { connection ->
                session.open(connection)
                navController.navigate(FLOW)
            })
        }
        composable(FLOW) {
            val connection = session.connection
            if (connection == null) {
                // After process recreation, ask for the session again rather than persisting its token.
                LaunchedEffect(Unit) { navController.popBackStack(ENTRY, false) }
            } else {
                FieldOperationsRoute(connection.baseUrl, connection.token, connection.obligationId, onExit = {
                    navController.popBackStack()
                    session.clear()
                })
            }
        }
    }
}
