package com.buzbuz.smartautoclicker.core.processing.tests

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.processing.diagnostics.DiagnosticLogger
import com.buzbuz.smartautoclicker.core.processing.diagnostics.SafeDiagnosticLogger
import com.buzbuz.smartautoclicker.core.processing.diagnostics.formatDiagnosticLine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class DiagnosticLoggerTests {

    @Test
    fun sinkFailuresNeverEscapeSessionOperations() {
        val failingSink = object : DiagnosticLogger {
            override fun startSession(scenarioId: Long, details: String) = error("storage failure")
            override fun log(category: String, message: String) = error("storage failure")
            override fun endSession(state: String, reason: String) = error("storage failure")
        }
        val logger = SafeDiagnosticLogger(failingSink)

        logger.startSession(1L, "safe metadata")
        logger.log("test", "message")
        logger.endSession("RECORDING", "test")
    }
    @Test
    fun lineFormattingIncludesWallClockElapsedSessionAndCategory() {
        assertEquals(
            "2026-10-07T15:49:02.421+09:00 elapsed=10928455 session=a13f thread=DefaultDispatcher-worker-1 " +
                "[DetectorEngine] state RECORDING -> DETECTING reason=processScreenImages\n",
            formatDiagnosticLine(
                timestamp = "2026-10-07T15:49:02.421+09:00",
                elapsedMs = 10_928_455L,
                sessionId = "a13f",
                threadName = "DefaultDispatcher-worker-1",
                category = "DetectorEngine",
                message = "state RECORDING -> DETECTING reason=processScreenImages",
            ),
        )
    }
}
