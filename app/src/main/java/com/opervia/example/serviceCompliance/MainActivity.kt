package com.opervia.example.serviceCompliance

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.opervia.example.serviceCompliance.app.AppContainer
import com.opervia.example.serviceCompliance.app.navigation.AppNavHost
import com.opervia.example.serviceCompliance.ui.theme.ServiceComplianceKotlinTheme
import androidx.compose.material3.MaterialTheme
import com.opervia.example.serviceCompliance.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val container = AppContainer(applicationContext, "http://10.0.2.2:8080/")
        setContent {
            ServiceComplianceKotlinTheme(dynamicColor = false) {
                AppNavHost(container)
            }
        }
    }
}
