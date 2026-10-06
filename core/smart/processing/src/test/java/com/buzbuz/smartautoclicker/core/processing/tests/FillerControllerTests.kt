package com.buzbuz.smartautoclicker.core.processing.tests

import android.graphics.Rect
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.core.processing.data.processor.FillerController
import com.buzbuz.smartautoclicker.core.processing.data.processor.state.EventsState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FillerControllerTests {

    @Test
    fun disabledFillerDoesNotRunUntilEnabled() = runTest {
        val calls = mutableListOf<Long>()
        val controller = FillerController(listOf(filler(1, false)), backgroundScope) { event, _ ->
            calls += event.id.databaseId
            awaitCancellation()
        }
        controller.start()

        runCurrent()
        assertTrue(calls.isEmpty())

        controller.setEnabled(filler(1, false), true)
        runCurrent()
        assertEquals(listOf(1L), calls)
        controller.stop()
    }

    @Test
    fun completedMovementImmediatelyStartsNextMovement() = runTest {
        val completions = ArrayDeque<CompletableDeferred<Unit>>()
        val starts = mutableListOf<Long>()
        val controller = FillerController(listOf(filler(1, true)), backgroundScope) { event, _ ->
            starts += event.id.databaseId
            val completion = CompletableDeferred<Unit>().also(completions::addLast)
            completion.await()
            true
        }
        controller.start()

        runCurrent()
        assertEquals(1, starts.size)
        completions.removeFirst().complete(Unit)
        runCurrent()
        assertEquals("repeat belongs to the controller, not a recognition frame", 2, starts.size)
        controller.stop()
    }

    @Test
    fun foregroundPreemptsAndResumesOnlyAfterFullSequence() = runTest {
        val permits = mutableListOf<() -> Boolean>()
        val calls = mutableListOf<Long>()
        val controller = FillerController(listOf(filler(1, true)), backgroundScope) { event, mayDispatch ->
            calls += event.id.databaseId
            permits += mayDispatch
            awaitCancellation()
        }
        controller.start()

        runCurrent()
        assertEquals(1, calls.size)
        controller.onForegroundStarted()
        runCurrent()
        assertFalse("invalidated filler token cannot dispatch", permits.first().invoke())
        assertEquals("no filler run during foreground actions", 1, calls.size)

        controller.onForegroundFinished()
        runCurrent()
        assertEquals("still enabled filler resumes after action sequence", 2, calls.size)
        controller.stop()
    }

    @Test
    fun enablingAnotherFillerCancelsThePreviousLoop() = runTest {
        var active = 0
        var maximumActive = 0
        val started = mutableListOf<Long>()
        val controller = FillerController(listOf(filler(1, true), filler(2, false)), backgroundScope) { event, _ ->
            active++
            maximumActive = maxOf(maximumActive, active)
            started += event.id.databaseId
            try {
                awaitCancellation()
            } finally {
                active--
            }
        }
        controller.start()

        runCurrent()
        assertEquals(listOf(1L), started)
        controller.setEnabled(filler(2, false), true)
        runCurrent()

        assertEquals(listOf(1L, 2L), started)
        assertEquals(1, maximumActive)
        assertFalse(controller.isEnabled(1L))
        assertTrue(controller.isEnabled(2L))
        controller.stop()
    }

    @Test
    fun enablingFillerBDisablesFillerAInProcessingState() {
        val first = filler(1, true)
        val second = filler(2, false)
        val state = EventsState(emptyList(), emptyList(), listOf(first, second))

        assertTrue(state.isEventEnabled(first.id.databaseId))
        state.enableEvent(second.id.databaseId)

        assertFalse(state.isEventEnabled(first.id.databaseId))
        assertTrue(state.isEventEnabled(second.id.databaseId))
    }

    @Test
    fun malformedMultipleEnabledOnStartFillersStillSelectAtMostOne() = runTest {
        val calls = mutableListOf<Long>()
        val controller = FillerController(listOf(filler(1, true), filler(2, true)), backgroundScope) { event, _ ->
            calls += event.id.databaseId
            awaitCancellation()
        }
        controller.start()

        runCurrent()

        assertEquals(listOf(1L), calls)
        assertTrue(controller.isEnabled(1L))
        assertFalse(controller.isEnabled(2L))
        controller.stop()
    }

    private fun filler(id: Long, enabled: Boolean) = FillerEvent(
        id = Identifier(databaseId = id),
        scenarioId = Identifier(databaseId = 100L),
        name = "Filler $id",
        enabledOnStart = enabled,
        movement = RandomMovement(
            id = Identifier(databaseId = id + 1000L),
            eventId = Identifier(databaseId = id),
            name = "Movement",
            priority = 0,
            area = Rect(0, 0, 100, 100),
            durationMs = 3_000L,
        ),
    )
}
