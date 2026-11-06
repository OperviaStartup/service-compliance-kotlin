package com.upc.example.serviceComplianceKotlin.fieldoperations.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upc.example.serviceComplianceKotlin.fieldoperations.theme.*

@Composable
internal fun Page(footer: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        if (footer != null) Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = footer)
    }
}

@Composable
internal fun FieldCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun Section(title: String, icon: FieldSymbol = FieldSymbol.Steps, trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    FieldCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon)
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FieldMuted) }
        }
        content()
    }
}

@Composable
internal fun IconTile(symbol: FieldSymbol, tint: Color = FieldBlue, background: Color = FieldPaleBlue) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(background), contentAlignment = Alignment.Center) {
        FieldIcon(symbol, tint = tint)
    }
}

@Composable
internal fun StatusPill(text: String, positive: Boolean = false, warning: Boolean = false) {
    val tint = if (warning) Color(0xFFB44925) else if (positive) FieldTeal else FieldBlue
    Surface(color = tint.copy(alpha = .1f), shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun InfoNote(text: String, warning: Boolean = false) {
    val tint = if (warning) Color(0xFFB07818) else FieldBlue
    Surface(color = if (warning) Color(0xFFFFF5DE) else Color(0xFFDFF0FF), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldIcon(if (warning) FieldSymbol.Warning else FieldSymbol.Info, tint = tint)
            Text(text, style = MaterialTheme.typography.bodySmall, color = FieldNavy, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { Text(text) }
}

@Composable
internal fun StepNumber(number: Int) {
    Box(Modifier.size(28.dp).background(FieldPaleBlue, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Text(number.toString(), color = FieldBlue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
