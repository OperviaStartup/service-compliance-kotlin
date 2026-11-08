package com.opervia.example.serviceCompliance.features.iam.presentation.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opervia.example.serviceCompliance.R
import com.opervia.example.serviceCompliance.ui.theme.OperviaAction
import com.opervia.example.serviceCompliance.ui.theme.OperviaDanger
import com.opervia.example.serviceCompliance.ui.theme.OperviaInk
import com.opervia.example.serviceCompliance.ui.theme.OperviaMuted
import com.opervia.example.serviceCompliance.ui.theme.OperviaSuccess
import com.opervia.example.serviceCompliance.ui.theme.ServiceComplianceKotlinTheme

@Composable
fun LoginScreen(
    state: LoginUiState,
    onIdentifierChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onForgotPassword: () -> Unit = {}
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.login_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.opervia_logo),
                contentDescription = "Opervia Service Compliance",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(188.dp).height(56.dp)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Service Compliance",
                color = OperviaSuccess,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(28.dp))

            Text(
                "Iniciar sesión",
                color = OperviaInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Ingresa con tus credenciales asignadas.",
                color = Color(0xFF536478),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                    FieldHeading(
                        label = "Correo electrónico",
                        valid = state.identifierValid,
                        showStatus = state.identifier.isNotBlank() &&
                                state.identifierError == null && !state.messageIsError
                    )
                    OutlinedTextField(
                        value = state.identifier,
                        onValueChange = onIdentifierChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("nombre@empresa.com") },
                        leadingIcon = { Text("✉", color = OperviaAction, fontSize = 18.sp) },
                        singleLine = true,
                        isError = state.identifierError != null || state.messageIsError,
                        colors = loginFieldColors(state.identifierError != null || state.messageIsError),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(8.dp)
                    )
                    state.identifierError?.let {
                        Text(it, color = OperviaDanger, style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.height(12.dp))

                    FieldHeading(
                        label = "Contraseña",
                        valid = state.passwordValid,
                        showStatus = state.password.isNotEmpty() &&
                                state.passwordError == null && !state.messageIsError
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ingresa tu contraseña") },
                        leadingIcon = {Text("▣", color = OperviaAction, fontSize = 18.sp) },
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "◉" else "◎", color = OperviaAction, fontSize = 18.sp)
                            }
                        },
                        singleLine = true,
                        isError = state.passwordError != null || state.messageIsError,
                        colors = loginFieldColors(state.passwordError != null || state.messageIsError),
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(8.dp)
                    )
                    state.passwordError?.let {
                        Text(it, color = OperviaDanger, style = MaterialTheme.typography.labelSmall)
                    }

                    state.message?.let {
                        Spacer(Modifier.height(10.dp))

                        Text(
                            it,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (state.messageIsError) OperviaDanger else OperviaMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onLogin,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0878EF))
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )

                            Spacer(Modifier.width(8.dp))

                            Text("AUTENTICANDO…", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("INICIAR SESIÓN  →", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = onForgotPassword,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    ) {
                        Text("¿Olvidaste tu contraseña?", color = Color(0xFF00847E))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFB7C9D8)))
                Text("  ⓘ  ", color = Color(0xFF64798B), fontSize = 12.sp)
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFB7C9D8)))
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Las cuentas son gestionadas por Supervisión Opervia.",
                color = Color(0xFF64798B),
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿Problemas para entrar? ", color = OperviaMuted, fontSize = 10.sp)
                Text("Contactar a soporte", color = OperviaAction, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun loginFieldColors(isError: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = if (isError) OperviaDanger else OperviaAction,
    unfocusedBorderColor = if (isError) OperviaDanger else Color(0xFFE3EEFF),
    focusedContainerColor = if (isError) Color(0xFFFFEEEE) else Color(0xFFE5F0FF),
    unfocusedContainerColor = if (isError) Color(0xFFFFEEEE) else Color(0xFFE5F0FF),
    errorContainerColor = Color(0xFFFFEEEE)
)

@Composable
private fun FieldHeading(label: String, valid: Boolean, showStatus: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = OperviaInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (showStatus) {
            Text(
                if (valid) "● Válido" else "● Revisa",
                color = if (valid) Color(0xFF00A875) else OperviaDanger,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginPreview() {
    ServiceComplianceKotlinTheme(dynamicColor = false) {
        LoginScreen(
            state = LoginUiState(),
            onIdentifierChanged = {},
            onPasswordChanged = {},
            onLogin = {},
            onForgotPassword = {}
        )
    }
}
