package com.buzbuz.smartautoclicker.core.processing.diagnostics

import android.util.Log

/** Keeps unexpected logger implementations or failures from escaping into automation code. */
fun DiagnosticLogger?.logSafely(category: String, message: String) {
    try {
        this?.log(category, message)
    } catch (exception: Exception) {
        Log.w("KlickrDiagnostics", "Diagnostic write failed (${exception.javaClass.simpleName})")
    }
}
