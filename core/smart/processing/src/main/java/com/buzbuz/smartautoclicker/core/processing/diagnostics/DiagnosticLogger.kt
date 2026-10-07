package com.buzbuz.smartautoclicker.core.processing.diagnostics

interface DiagnosticLogger {
    fun startSession(scenarioId: Long, details: String)
    fun log(category: String, message: String)
    fun endSession(state: String, reason: String)
}
