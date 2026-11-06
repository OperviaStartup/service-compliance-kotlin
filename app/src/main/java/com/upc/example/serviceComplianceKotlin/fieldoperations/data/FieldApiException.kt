package com.upc.example.serviceComplianceKotlin.fieldoperations.data

class FieldApiException(val status: Int, val code: String, message: String) : Exception(message)
