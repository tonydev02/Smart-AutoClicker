/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.processing.tests

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.common.actions.model.ActionNotificationRequest
import com.buzbuz.smartautoclicker.core.detection.DetectionResult
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.EXACT
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.action.toggleevent.EventToggle
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.counter.ComparisonOperation
import com.buzbuz.smartautoclicker.core.domain.model.counter.Counter
import com.buzbuz.smartautoclicker.core.domain.model.counter.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.processing.data.processor.ScenarioProcessor
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ScalingManager
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ScreenConditionScalingInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when` as mockWhen
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.PrintWriter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class ScenarioProcessorFillerIntegrationTests {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fulfilledScreenEventPreemptsActionsThenResumesFiller() = runTest {
        val executor = ControlledActionExecutor(FILLER_DURATION, setOf(SCREEN_ACTION_ONE, SCREEN_ACTION_TWO))
        val condition = newDetectedCondition(SCREEN_EVENT_ID)
        val processor = newProcessor(
            executor = executor,
            screenEvents = listOf(screenEvent(SCREEN_EVENT_ID, condition, listOf(click(SCREEN_EVENT_ID, SCREEN_ACTION_ONE), click(SCREEN_EVENT_ID, SCREEN_ACTION_TWO)))),
            fillerEvents = listOf(fillerEvent()),
        )
        processor.onScenarioStart(Mockito.mock(Context::class.java))

        val process = launch { processor.process(newScreenFrame()) }
        try {
            runCurrent()
            executor.fillerStarted.await()
            executor.foregroundStarted(SCREEN_ACTION_ONE)
            assertEquals("only initial filler dispatch before screen action sequence", 1, executor.fillerDispatchCount)

            executor.releaseForeground(SCREEN_ACTION_ONE)
            runCurrent()
            executor.foregroundStarted(SCREEN_ACTION_TWO)
            assertEquals("filler remains paused between actions", 1, executor.fillerDispatchCount)

            executor.releaseForeground(SCREEN_ACTION_TWO)
            runCurrent()
            process.join()
            executor.fillerResumed.await()
            assertEquals(2, executor.fillerDispatchCount)
        } finally {
            processor.onScenarioEnd()
        }
    }

    @Test
    fun screenEventCanDisableFillerDuringItsActionSequence() = runTest {
        val filler = fillerEvent()
        val toggler = ToggleEvent(
            id = id(503L),
            eventId = SCREEN_EVENT_ID,
            name = "Disable filler",
            priority = 1,
            eventToggles = listOf(
                EventToggle(
                    id = id(504L),
                    actionId = id(503L),
                    targetEventId = filler.id,
                    toggleType = ToggleEvent.ToggleType.DISABLE,
                ),
            ),
        )
        val executor = ControlledActionExecutor(FILLER_DURATION, setOf(SCREEN_ACTION_ONE, SCREEN_ACTION_TWO))
        val condition = newDetectedCondition(SCREEN_EVENT_ID)
        val processor = newProcessor(
            executor = executor,
            screenEvents = listOf(
                screenEvent(
                    SCREEN_EVENT_ID,
                    condition,
                    listOf(click(SCREEN_EVENT_ID, SCREEN_ACTION_ONE), toggler, click(SCREEN_EVENT_ID, SCREEN_ACTION_TWO)),
                ),
            ),
            fillerEvents = listOf(filler),
        )
        processor.onScenarioStart(Mockito.mock(Context::class.java))

        val process = launch { processor.process(newScreenFrame()) }
        try {
            runCurrent()
            executor.foregroundStarted(SCREEN_ACTION_ONE)
            executor.releaseForeground(SCREEN_ACTION_ONE)
            runCurrent()
            executor.foregroundStarted(SCREEN_ACTION_TWO)
            assertEquals(1, executor.fillerDispatchCount)

            executor.releaseForeground(SCREEN_ACTION_TWO)
            runCurrent()
            process.join()
            assertEquals("disabled filler must not resume after foreground completion", 1, executor.fillerDispatchCount)
        } finally {
            processor.onScenarioEnd()
        }
    }

    @Test
    fun fulfilledTriggerEventPreemptsActionsThenResumesFiller() = runTest {
        val trigger = triggerEvent(
            TRIGGER_EVENT_ID,
            listOf(click(TRIGGER_EVENT_ID, TRIGGER_ACTION_ONE), click(TRIGGER_EVENT_ID, TRIGGER_ACTION_TWO)),
        )
        val executor = ControlledActionExecutor(FILLER_DURATION, setOf(TRIGGER_ACTION_ONE, TRIGGER_ACTION_TWO))
        val processor = newProcessor(
            executor = executor,
            triggerEvents = listOf(trigger),
            counters = listOf(Counter("score", 0.0, SCENARIO_ID)),
            fillerEvents = listOf(fillerEvent()),
        )
        processor.onScenarioStart(Mockito.mock(Context::class.java))

        val process = launch { processor.process(newScreenFrame()) }
        try {
            runCurrent()
            executor.fillerStarted.await()
            executor.foregroundStarted(TRIGGER_ACTION_ONE)
            assertEquals(1, executor.fillerDispatchCount)

            executor.releaseForeground(TRIGGER_ACTION_ONE)
            runCurrent()
            executor.foregroundStarted(TRIGGER_ACTION_TWO)
            assertEquals("filler remains paused during trigger action sequence", 1, executor.fillerDispatchCount)

            executor.releaseForeground(TRIGGER_ACTION_TWO)
            runCurrent()
            process.join()
            executor.fillerResumed.await()
            assertEquals(2, executor.fillerDispatchCount)
        } finally {
            processor.onScenarioEnd()
        }
    }

    @Test
    fun triggerActionsRemainBeforeScreenActions() = runTest {
        val trigger = triggerEvent(TRIGGER_EVENT_ID, listOf(click(TRIGGER_EVENT_ID, TRIGGER_ORDER_ACTION)))
        val condition = newDetectedCondition(SCREEN_EVENT_ID)
        val screen = screenEvent(SCREEN_EVENT_ID, condition, listOf(click(SCREEN_EVENT_ID, SCREEN_ORDER_ACTION)))
        val executor = ControlledActionExecutor(FILLER_DURATION, emptySet())
        val processor = newProcessor(
            executor = executor,
            screenEvents = listOf(screen),
            triggerEvents = listOf(trigger),
            counters = listOf(Counter("score", 0.0, SCENARIO_ID)),
        )
        processor.onScenarioStart(Mockito.mock(Context::class.java))

        try {
            processor.process(newScreenFrame())
            assertEquals(listOf(TRIGGER_ORDER_ACTION, SCREEN_ORDER_ACTION), executor.foregroundDurations)
        } finally {
            processor.onScenarioEnd()
        }
    }

    private fun newProcessor(
        executor: AndroidActionExecutor,
        screenEvents: List<ScreenEvent> = emptyList(),
        triggerEvents: List<TriggerEvent> = emptyList(),
        counters: List<Counter> = emptyList(),
        fillerEvents: List<FillerEvent> = emptyList(),
    ): ScenarioProcessor {
        val scalingManager = Mockito.mock(ScalingManager::class.java)
        val imageDetector = Mockito.mock(ImageDetector::class.java)
        val conditionBitmaps = mutableMapOf<String, Bitmap>()
        mockWhen(scalingManager.scaleUpDetectionResult(any())).thenAnswer { it.getArgument(0) }
        screenEvents.flatMap { it.conditions }.filterIsInstance<ScreenCondition.Image>().forEach { condition ->
            val bitmap = Mockito.mock(Bitmap::class.java)
            conditionBitmaps[condition.path] = bitmap
            mockWhen(scalingManager.getScreenConditionScalingInfo(condition)).thenReturn(
                ScreenConditionScalingInfo.Image(condition, condition.area, condition.area),
            )
            mockWhen(
                imageDetector.detectImage(
                    eq(bitmap),
                    eq(condition.area.width()),
                    eq(condition.area.height()),
                    eq(condition.area),
                    eq(condition.threshold),
                ),
            ).thenReturn(DetectionResult(true))
        }

        return ScenarioProcessor(
            processingTag = "filler-test",
            imageDetector = imageDetector,
            scalingManager = scalingManager,
            randomize = false,
            screenEvents = screenEvents,
            triggerEvents = triggerEvents,
            counters = counters,
            bitmapSupplier = { path, _, _ -> conditionBitmaps[path] },
            androidExecutor = executor,
            onStopRequested = {},
            progressListener = null,
            fillerEvents = fillerEvents,
        )
    }

    private fun newDetectedCondition(eventId: Identifier): ScreenCondition.Image = ScreenCondition.Image(
        id = id(301L),
        eventId = eventId,
        name = "Detected image",
        threshold = 10,
        shouldBeDetected = true,
        priority = 0,
        path = "condition-image",
        area = Rect(0, 0, 10, 10),
        detectionType = EXACT,
    )

    private fun screenEvent(
        id: Identifier,
        condition: ScreenCondition.Image,
        actions: List<com.buzbuz.smartautoclicker.core.domain.model.action.Action>,
    ) = ScreenEvent(
        id = id,
        scenarioId = SCENARIO_ID,
        name = "Screen event",
        conditionOperator = AND,
        actions = actions,
        conditions = listOf(condition),
        enabledOnStart = true,
        priority = 0,
        keepDetecting = false,
        cooldownMs = 0L,
    )

    private fun triggerEvent(id: Identifier, actions: List<Click>) = TriggerEvent(
        id = id,
        scenarioId = SCENARIO_ID,
        name = "Trigger event",
        conditionOperator = AND,
        actions = actions,
        conditions = listOf(
            TriggerCondition.OnCounterCountReached(
                id = id(302L),
                eventId = id,
                name = "Score is zero",
                counterName = "score",
                comparisonOperation = ComparisonOperation.EQUALS,
                counterValue = CounterOperationValue.Number(0.0),
            ),
        ),
    )

    private fun fillerEvent() = FillerEvent(
        id = FILLER_EVENT_ID,
        scenarioId = SCENARIO_ID,
        name = "Filler",
        enabledOnStart = true,
        movement = RandomMovement(
            id = id(303L),
            eventId = FILLER_EVENT_ID,
            name = "Random movement",
            priority = 0,
            area = Rect(0, 0, 20, 20),
            durationMs = FILLER_DURATION,
        ),
    )

    private fun click(eventId: Identifier, durationMs: Long) = Click(
        id = id(durationMs + 1000),
        eventId = eventId,
        name = "Click",
        priority = 0,
        pressDuration = durationMs,
        positionType = Click.PositionType.USER_SELECTED,
        position = Point(1, 1),
    )

    private fun newScreenFrame(): Bitmap = Mockito.mock(Bitmap::class.java).apply {
        mockWhen(width).thenReturn(100)
        mockWhen(height).thenReturn(100)
        mockWhen(config).thenReturn(Bitmap.Config.ARGB_8888)
    }

    private fun id(value: Long) = Identifier(databaseId = value)

    private class ControlledActionExecutor(
        private val fillerDuration: Long,
        foregroundGatedDurations: Set<Long>,
    ) : AndroidActionExecutor {
        private val foregroundStarted = foregroundGatedDurations.associateWith { CompletableDeferred<Unit>() }
        private val foregroundReleases = foregroundGatedDurations.associateWith { CompletableDeferred<Unit>() }
        val foregroundDurations = mutableListOf<Long>()
        val fillerStarted = CompletableDeferred<Unit>()
        val fillerResumed = CompletableDeferred<Unit>()
        var fillerDispatchCount: Int = 0
            private set

        override fun init(service: AccessibilityService) = Unit
        override fun resetState() = Unit
        override fun clear() = Unit
        override fun dump(writer: PrintWriter, prefix: CharSequence) = Unit
        override fun performGlobalAction(globalAction: Int) = Unit
        override fun writeTextOnFocusedItem(text: String, validate: Boolean) = Unit
        override fun startActivity(intent: Intent) = Unit
        override fun sendBroadcast(intent: Intent) = Unit
        override fun postNotification(notificationRequest: ActionNotificationRequest) = Unit

        override suspend fun dispatchGesture(gestureDescription: GestureDescription) {
            val duration = gestureDescription.getStroke(0).duration
            if (duration == fillerDuration) {
                fillerDispatchCount++
                if (fillerDispatchCount == 1) fillerStarted.complete(Unit)
                else fillerResumed.complete(Unit)
                awaitCancellation()
            }

            foregroundDurations += duration
            foregroundStarted[duration]?.complete(Unit)
            foregroundReleases[duration]?.await()
        }

        suspend fun foregroundStarted(durationMs: Long) = foregroundStarted.getValue(durationMs).await()
        fun releaseForeground(durationMs: Long) { foregroundReleases.getValue(durationMs).complete(Unit) }
    }

    private companion object {
        val SCENARIO_ID = Identifier(databaseId = 100L)
        val SCREEN_EVENT_ID = Identifier(databaseId = 101L)
        val TRIGGER_EVENT_ID = Identifier(databaseId = 102L)
        val FILLER_EVENT_ID = Identifier(databaseId = 103L)
        const val FILLER_DURATION = 900L
        const val SCREEN_ACTION_ONE = 101L
        const val SCREEN_ACTION_TWO = 102L
        const val TRIGGER_ACTION_ONE = 201L
        const val TRIGGER_ACTION_TWO = 202L
        const val TRIGGER_ORDER_ACTION = 301L
        const val SCREEN_ORDER_ACTION = 401L
    }
}
