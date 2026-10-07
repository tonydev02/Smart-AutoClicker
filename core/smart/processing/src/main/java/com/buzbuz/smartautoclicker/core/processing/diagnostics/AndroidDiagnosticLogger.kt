/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.processing.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
internal class AndroidDiagnosticLogger @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DiagnosticLogger {
    private val lock = Any()
    private var sessionId: String? = null
    private var writer: BufferedWriter? = null

    override fun startSession(scenarioId: Long, details: String) = synchronized(lock) {
        closeWriter()
        sessionId = UUID.randomUUID().toString().substringBefore('-')
        try {
            writer = openSessionWriter()
            logLocked("Session", "SESSION START scenarioId=$scenarioId $details")
        } catch (exception: Exception) {
            fail(exception)
        }
    }

    override fun log(category: String, message: String) = synchronized(lock) {
        logLocked(category, message)
    }

    override fun endSession(state: String, reason: String) = synchronized(lock) {
        logLocked("Session", "SESSION END state=$state reason=$reason")
        closeWriter()
        sessionId = null
    }

    private fun logLocked(category: String, message: String) {
        val currentSession = sessionId ?: return
        try {
            val timestamp = TIMESTAMP_FORMAT.format(Date())
            val line = formatDiagnosticLine(
                timestamp = timestamp,
                elapsedMs = android.os.SystemClock.elapsedRealtime(),
                sessionId = currentSession,
                threadName = Thread.currentThread().name,
                category = category,
                message = message,
            )
            writer?.apply {
                write(line)
                flush()
            } ?: Log.w(TAG, "Persistent diagnostics unavailable; session=$currentSession")
        } catch (exception: Exception) {
            fail(exception)
        }
    }

    private fun openSessionWriter(): BufferedWriter {
        val filename = "klickr-diagnostic-${FILE_FORMAT.format(Date())}.log"
        val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Klickr/Diagnostics")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert returned null")
            context.contentResolver.openOutputStream(uri, "wa")
                ?: throw IllegalStateException("MediaStore stream unavailable")
        } else {
            val directory = context.getExternalFilesDir("Diagnostics")
                ?: throw IllegalStateException("App-specific external storage unavailable")
            directory.mkdirs()
            directory.resolve(filename).outputStream()
        }
        return BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to close diagnostics (${exception.javaClass.simpleName})")
        } finally {
            writer = null
        }
    }

    private fun fail(exception: Exception) {
        Log.w(TAG, "Persistent diagnostics unavailable (${exception.javaClass.simpleName})")
        writer = null
    }

    private companion object {
        const val TAG = "KlickrDiagnostics"
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        val FILE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
internal fun formatDiagnosticLine(
    timestamp: String,
    elapsedMs: Long,
    sessionId: String,
    threadName: String,
    category: String,
    message: String,
): String =
    "$timestamp elapsed=$elapsedMs session=$sessionId thread=$threadName [$category] $message\n"
