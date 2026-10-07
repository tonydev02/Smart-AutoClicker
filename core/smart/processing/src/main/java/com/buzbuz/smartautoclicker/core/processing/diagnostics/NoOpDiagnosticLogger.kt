package com.buzbuz.smartautoclicker.core.processing.diagnostics

/** No-op diagnostic sink for isolated processing tests. */
internal object NoOpDiagnosticLogger : DiagnosticLogger {
    override fun startSession(scenarioId: Long, details: String) = Unit
    override fun log(category: String, message: String) = Unit
    override fun endSession(state: String, reason: String) = Unit
}
