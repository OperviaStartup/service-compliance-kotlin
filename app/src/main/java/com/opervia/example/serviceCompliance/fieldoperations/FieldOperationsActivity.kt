package com.opervia.example.serviceCompliance.fieldoperations

import com.opervia.example.serviceCompliance.fieldoperations.data.FieldRepository
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge


class FieldOperationsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val baseUrl = intent.getStringExtra(EXTRA_URL)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val valid = runCatching {
            require(id > 0)
            FieldRepository(requireNotNull(baseUrl), requireNotNull(token))
        }.isSuccess
        if (!valid || baseUrl == null || token == null) { finish(); return }
        setContent {
            FieldOperationsRoute(baseUrl, token, id, onExit = { finish() })
        }
    }
    companion object {
        private const val EXTRA_ID = "field.obligationId"
        private const val EXTRA_URL = "field.baseUrl"
        private const val EXTRA_TOKEN = "field.session"
        fun intent(context: Context, baseUrl: String, bearerToken: String, obligationId: Long): Intent =
            Intent(context, FieldOperationsActivity::class.java).putExtra(EXTRA_ID, obligationId)
                .putExtra(EXTRA_URL, baseUrl).putExtra(EXTRA_TOKEN, bearerToken)
    }
}
