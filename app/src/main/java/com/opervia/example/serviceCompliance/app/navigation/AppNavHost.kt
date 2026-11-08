package com.opervia.example.serviceCompliance.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opervia.example.serviceCompliance.app.AppContainer
import com.opervia.example.serviceCompliance.core.session.UserSession
import com.opervia.example.serviceCompliance.features.iam.presentation.home.AuthenticatedHomeScreen
import com.opervia.example.serviceCompliance.features.iam.presentation.login.*
import kotlinx.coroutines.launch

private const val LOGIN_ROUTE = "/login"
private const val ACTIVE_ACCOUNT_ROUTE = "/active-account"

@Composable
fun AppNavHost(container: AppContainer) {
    var session by remember { mutableStateOf<UserSession?>(null) }
    var restoreComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session = try {
            container.authRepository.validateSession()
        } catch (_: Exception) {
            // Keep the encrypted session on transient network errors; show Login for retry.
            null
        }
        restoreComplete = true
    }

    if (!restoreComplete) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text("Verificando sesión…")
        }
        return
    }

    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    NavHost(
        navController = navController,
        startDestination = if (session == null) LOGIN_ROUTE else ACTIVE_ACCOUNT_ROUTE
    ) {
        composable(LOGIN_ROUTE) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = container.loginViewModelFactory
            )
            val loginState by loginViewModel.state.collectAsState()
            LoginScreen(
                state = loginState,
                onIdentifierChanged = loginViewModel::identifierChanged,
                onPasswordChanged = loginViewModel::passwordChanged,
                onForgotPassword = loginViewModel::forgotPasswordClicked,
                onLogin = {
                    loginViewModel.login { authenticatedSession ->
                        session = authenticatedSession
                        navController.navigate(ACTIVE_ACCOUNT_ROUTE) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(ACTIVE_ACCOUNT_ROUTE) {
            AuthenticatedHomeScreen(
                fullName = session?.fullName.orEmpty(),
                onLogout = {
                    scope.launch {
                        runCatching { container.authRepository.logout() }
                        session = null
                        navController.navigate(LOGIN_ROUTE) {
                            popUpTo(ACTIVE_ACCOUNT_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}

