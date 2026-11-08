package com.opervia.example.serviceCompliance.fieldoperations.data

class FieldApiException(val status: Int, val code: String, message: String) : Exception(message)
