package com.opervia.example.serviceCompliance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.opervia.example.serviceCompliance.app.AppContainer
import com.opervia.example.serviceCompliance.app.navigation.AppNavHost
import com.opervia.example.serviceCompliance.ui.theme.ServiceComplianceKotlinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = AppContainer(applicationContext, "http://10.0.2.2:8080/")
        setContent {
            ServiceComplianceKotlinTheme(dynamicColor = false) {
                AppNavHost(container)
            }
        }
    }
}
