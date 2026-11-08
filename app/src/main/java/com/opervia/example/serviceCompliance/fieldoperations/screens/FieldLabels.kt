package com.opervia.example.serviceCompliance.fieldoperations.screens

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun statusLabel(status: String): String {
    return when (status) {
        "ASSIGNED" -> "Asignada"
        "IN_PROGRESS" -> "En curso"
        "SUBMITTED" -> "Completada y enviada"
        "OVERDUE" -> "Vencida"
        else -> "No disponible"
    }
}

internal fun dateLabel(value: String): String {
    try {
        val locale = Locale.forLanguageTag("es-PE")
        val zone = ZoneId.of("America/Lima")
        val format = DateTimeFormatter.ofPattern("dd MMM · HH:mm", locale).withZone(zone)
        val date = Instant.parse(value)
        return format.format(date)
    } catch (_: Exception) {
        return value
    }
}

internal fun reasonLabel(reason: String): String {
    return when (reason) {
        "AREA_CLOSED" -> "Área cerrada"
        "MISSING_SUPPLIES" -> "Faltan insumos"
        "SAFETY_RISK" -> "Riesgo de seguridad"
        else -> "Otro"
    }
}
