package com.buzbuz.smartautoclicker.core.processing.diagnostics

import android.util.Log

/** Isolates every production consumer from failures in the persistent diagnostic sink. */
internal class SafeDiagnosticLogger(private val delegate: DiagnosticLogger) : DiagnosticLogger {
    override fun startSession(scenarioId: Long, details: String) = safely {
        delegate.startSession(scenarioId, details)
    }

    override fun log(category: String, message: String) = safely {
        delegate.log(category, message)
    }

    override fun endSession(state: String, reason: String) = safely {
        delegate.endSession(state, reason)
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (exception: Exception) {
            Log.w(TAG, "Diagnostic operation failed (${exception.javaClass.simpleName})")
        }
    }

    private companion object {
        const val TAG = "KlickrDiagnostics"
    }
}
