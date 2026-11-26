package com.opervia.example.serviceCompliance.features.operations

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.opervia.example.serviceCompliance.features.iam.presentation.login.LoginScreen
import com.opervia.example.serviceCompliance.features.iam.presentation.login.LoginUiState
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.FieldTasksContent
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.HistoryScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldTasksState
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.HistoryState
import com.opervia.example.serviceCompliance.ui.theme.OperviaBackdrop
import com.opervia.example.serviceCompliance.ui.theme.ServiceComplianceKotlinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperviaBackdropInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun operatorTabsKeepTheLoginBackdropVisible() {
        val signedIn = mutableStateOf(false)
        compose.setContent {
            ServiceComplianceKotlinTheme {
                OperviaBackdrop {
                    if (signedIn.value) {
                        FieldTasksContent(
                            state = FieldTasksState(loaded = true),
                            fullName = "Operario Demo",
                            executionHistory = emptyList(),
                            localHistoryWarning = null,
                            onOpenObligation = {},
                            onLogout = {},
                            onSynchronize = {},
                            onRefresh = {},
                            historyContent = { HistoryScreen(HistoryState(), {}, {}, {}, {}, {}, {}) }
                        )
                    } else {
                        LoginScreen(LoginUiState(), {}, {}, onLogin = { signedIn.value = true })
                    }
                }
            }
        }

        val loginBackdrop = backdropPixels()
        assertNotEquals(loginBackdrop.first(), loginBackdrop.last())
        compose.onNodeWithText("INICIAR SESIÓN", substring = true, ignoreCase = false)
            .performClick()
        compose.onNodeWithText("Buenos días, Operario").assertIsDisplayed()
        assertEquals("Inicio", loginBackdrop, backdropPixels())
        val tabs = mapOf(
            "Trabajo" to "actividades asignadas",
            "Historial" to "Tus ejecuciones registradas",
            "Perfil" to "Perfil del operario"
        )
        for ((tab, content) in tabs) {
            compose.onNodeWithText(tab).performClick()
            compose.onNodeWithText(content, substring = true).assertIsDisplayed()
            assertEquals(tab, loginBackdrop, backdropPixels())
        }
        compose.onNodeWithContentDescription("Centro de sincronización").performClick()
        compose.onNodeWithText("Centro de sincronización").assertIsDisplayed()
        assertEquals("Sincronización", loginBackdrop, backdropPixels())
    }

    private fun backdropPixels() = compose.onRoot().captureToImage().toPixelMap().let { pixels ->
        listOf(pixels[2, pixels.height / 4], pixels[2, pixels.height * 2 / 3])
    }
}
