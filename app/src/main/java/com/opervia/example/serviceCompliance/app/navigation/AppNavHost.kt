package com.opervia.example.serviceCompliance.app.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opervia.example.serviceCompliance.app.AppContainer
import com.opervia.example.serviceCompliance.BuildConfig
import com.opervia.example.serviceCompliance.core.session.UserSession
import com.opervia.example.serviceCompliance.features.operations.presentation.FieldOperationsRoute
import com.opervia.example.serviceCompliance.features.operations.presentation.FieldTasksRoute
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.iam.presentation.login.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.net.URI

private const val LOGIN_ROUTE = "/login"
private const val ACTIVE_ACCOUNT_ROUTE = "/active-account"

@Composable
fun AppNavHost(container: AppContainer) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<UserSession?>(null) }
    var selectedObligationId by rememberSaveable { mutableStateOf<Long?>(null) }
    val executionHistory = remember { mutableStateListOf<ExecutionRecord>() }
    var executionHistoryOwner by remember { mutableStateOf<Long?>(null) }
    var executionHistoryError by remember { mutableStateOf<String?>(null) }
    var restoreComplete by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restoreAttempt by remember { mutableStateOf(0) }
    val localBackend = BuildConfig.DEBUG && URI(BuildConfig.API_BASE_URL).host in setOf("10.0.2.2", "127.0.0.1", "localhost")
    val requiresLocalNetworkPermission = localBackend && Build.VERSION.SDK_INT >= 37
    var localNetworkAccess by remember { mutableStateOf<Boolean?>(null) }
    val localNetworkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localNetworkAccess = granted
    }

    LaunchedEffect(requiresLocalNetworkPermission) {
        if (!requiresLocalNetworkPermission || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkAccess = true
        } else {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    LaunchedEffect(localNetworkAccess, restoreAttempt) {
        if (localNetworkAccess != true) return@LaunchedEffect
        restoreComplete = false
        restoreError = null
        session = try {
            container.authRepository.validateSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            restoreError = error.message ?: "No se pudo validar la sesión. Comprueba tu conexión."
            null
        }
        restoreComplete = true
    }

    LaunchedEffect(session?.userId) {
        executionHistory.clear()
        executionHistoryOwner = null
        executionHistoryError = null
        val userId = session?.userId ?: return@LaunchedEffect
        try {
            executionHistory.addAll(container.localFieldOperationsStore.loadExecutions(userId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            executionHistoryError = "No se pudo recuperar el historial guardado en este dispositivo."
        }
        executionHistoryOwner = userId
    }

    if (localNetworkAccess == false) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("Para conectar con la API local, permite el acceso a la red local.")
            Button(onClick = { localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) {
                Text("Solicitar acceso")
            }
        }
        return
    }
    if (localNetworkAccess == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        return
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

    if (restoreError != null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No se pudo validar tu sesión guardada.")
            Text("Comprueba la conexión e inténtalo de nuevo.")
            Button(onClick = { restoreAttempt++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("  Reintentar")
            }
        }
        return
    }

    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val onLogout: () -> Unit = {
        scope.launch {
            try {
                container.authRepository.logout()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The repository clears the local session even if the API is unavailable.
            }
            session = null
            selectedObligationId = null
            executionHistory.clear()
            executionHistoryOwner = null
            navController.navigate(LOGIN_ROUTE) {
                popUpTo(ACTIVE_ACCOUNT_ROUTE) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = if (session == null) LOGIN_ROUTE else ACTIVE_ACCOUNT_ROUTE
    ) {
        composable(LOGIN_ROUTE) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = container.loginViewModelFactory
            )
            val loginState by loginViewModel.state.collectAsStateWithLifecycle()
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
            val activeSession = session
            if (activeSession != null) {
                if (activeSession.role != "OPERATOR") {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Acceso no disponible")
                        Text("Esta interfaz es exclusiva para cuentas de operario.")
                        Button(onClick = onLogout) { Text("Cerrar sesión") }
                    }
                } else if (executionHistoryOwner != activeSession.userId) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                } else {
                    val operationsRepository = container.operationsRepository
                    val selectedId = selectedObligationId
                    if (selectedId == null) {
                        FieldTasksRoute(
                            repository = operationsRepository,
                            localStore = container.localFieldOperationsStore,
                            userId = activeSession.userId,
                            fullName = activeSession.fullName,
                            executionHistory = executionHistory.toList(),
                            localHistoryWarning = executionHistoryError,
                            onOpenObligation = { selectedObligationId = it },
                            onLogout = onLogout
                        )
                    } else {
                        FieldOperationsRoute(
                            repository = operationsRepository,
                            localStore = container.localFieldOperationsStore,
                            userId = activeSession.userId,
                            obligationId = selectedId,
                            onExit = { selectedObligationId = null },
                            existingExecution = executionHistory.firstOrNull { it.obligationId == selectedId },
                            onExecutionRegistered = { record ->
                                if (executionHistory.none { it.id == record.id }) executionHistory.add(0, record)
                            }
                        )
                    }
                }
            }
        }
    }
}

