package com.opervia.example.serviceCompliance.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opervia.example.serviceCompliance.screens.IntegrationPendingScreen

/** The team's login/list graph will replace this placeholder; debug tooling is excluded. */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "integration-pending") {
        composable("integration-pending") { IntegrationPendingScreen() }
    }
}
