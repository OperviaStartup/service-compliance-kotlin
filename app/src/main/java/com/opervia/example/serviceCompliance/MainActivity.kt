package com.opervia.example.serviceCompliance

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.opervia.example.serviceCompliance.app.AppContainer
import com.opervia.example.serviceCompliance.app.navigation.AppNavHost
import com.opervia.example.serviceCompliance.ui.theme.ServiceComplianceKotlinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val container = AppContainer(applicationContext, BuildConfig.API_BASE_URL)
        setContent {
            ServiceComplianceKotlinTheme(dynamicColor = false) {
                AppNavHost(container)
            }
        }
    }
}
