/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.processing.data.processor

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting

import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.domain.model.counter.Counter
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ScalingManager
import com.buzbuz.smartautoclicker.core.processing.diagnostics.DiagnosticLogger
import com.buzbuz.smartautoclicker.core.processing.domain.EventType
import com.buzbuz.smartautoclicker.core.processing.data.processor.state.ProcessingState
import com.buzbuz.smartautoclicker.core.processing.domain.SmartProcessingListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext

/**
 * Process a screen image and tries to detect the list of [ScreenEvent] on it.
 *
 * @param imageDetector the detector for images.
 * @param randomize true to randomize the actions values a bit to avoid being taken for a bot.
 * @param screenEvents the list of scenario events to be detected.
 * @param bitmapSupplier provides the conditions bitmaps.
 * @param androidExecutor execute the actions requiring an interaction with Android.
 * @param onStopRequested called when an end condition of the scenario have been reached or all events are disabled.
 * @param progressListener the object to notify for detection progress. Can be null if not required.
 */
internal class ScenarioProcessor(
    private val processingTag: String,
    private val imageDetector: ImageDetector,
    scalingManager: ScalingManager,
    randomize: Boolean,
    screenEvents: List<ScreenEvent>,
    triggerEvents: List<TriggerEvent>,
    counters: List<Counter>,
    private val bitmapSupplier: suspend (String, Int, Int) -> Bitmap?,
    androidExecutor: AndroidActionExecutor,
    unblockWorkaroundEnabled: Boolean = false,
    private val onStopRequested: () -> Unit,
    private val progressListener: SmartProcessingListener?,
    fillerEvents: List<FillerEvent> = emptyList(),
    private val diagnosticLogger: DiagnosticLogger? = null,
) {

    private val fillerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Handle the processing state of the scenario. */
    @VisibleForTesting internal val processingState: ProcessingState = ProcessingState(
        screenEvents = screenEvents,
        triggerEvents = triggerEvents,
        counters = counters,
        progressListener = progressListener,
        fillerEvents = fillerEvents,
        onFillerEventStateChanged = { event, enabled -> fillerController.setEnabled(event, enabled) },
    )
    /** Check conditions and tell if they are fulfilled. */
    private val conditionsVerifier = ConditionsVerifier(
        state = processingState,
        imageDetector = imageDetector,
        scalingManager = scalingManager,
        bitmapSupplier = bitmapSupplier,
        progressListener = progressListener,
    )
    /** Execute the detected event actions. */
    private val actionExecutor = ActionExecutor(
        androidExecutor = androidExecutor,
        processingState = processingState,
        randomize = randomize,
        unblockWorkaroundEnabled = unblockWorkaroundEnabled,
        diagnosticLogger = diagnosticLogger,
    )
    private val fillerController = FillerController(fillerEvents, fillerScope) { filler, mayDispatch ->
        actionExecutor.executeFillerRandomMovement(filler.movement, mayDispatch)
    }

    fun onScenarioStart(context: Context) {
        diagnosticLogger?.log("ScenarioProcessor", "ScenarioProcessor start")
        processingState.onProcessingStarted(context)
        fillerController.start()
    }

    fun onScenarioEnd() {
        diagnosticLogger?.log("ScenarioProcessor", "ScenarioProcessor end")
        fillerController.stop()
        fillerScope.cancel()
        processingState.onProcessingStopped()
    }

    /**
     * Find an event with the conditions fulfilled on the current image.
     *
     * @param screenFrame the bitmap containing the current screen display.
     *
     * @return the first Event with all conditions fulfilled, or null if none has been found.
     */
    suspend fun process(screenFrame: Bitmap) {
        // No more events enabled, there is nothing more to do. Stop the detection.
        if (processingState.areAllEventsDisabled()) {
            diagnosticLogger?.log("ScenarioProcessor", "ALL_EVENTS_DISABLED")
            onStopRequested()
            return
        }

        // Handle all trigger events enabled during previous processing
        if (!processingState.areAllTriggerEventsDisabled()) {
            progressListener?.onEventsListProcessingStarted(EventType.Trigger)
            processTriggerEvents()
            progressListener?.onEventsProcessingCompleted(EventType.Trigger)
        }

        // Reset any values that needs to be reset for each iteration
        // After the triggers to let them handle changes, before the image processing to start capturing values before
        processingState.clearIterationState()

        // Handle the image detection
        if (!processingState.areAllScreenEventsDisabled()) {
            progressListener?.onEventsListProcessingStarted(EventType.Screen)
            processScreenEvents(screenFrame)
            progressListener?.onEventsProcessingCompleted(EventType.Screen)
        }

        // Loop is completed
        actionExecutor.onScenarioLoopFinished()

        return
    }

    private suspend fun processTriggerEvents() {
        for (triggerEvent in processingState.getTriggerEvents()) {
            // Enabled state of the event might have changed during the loop
            if (!processingState.isEventEnabled(triggerEvent.id.databaseId)) continue

            // No conditions ? This should not happen, skip this event
            if (triggerEvent.conditions.isEmpty()) continue

            progressListener?.onEventProcessingStarted(triggerEvent)
            val results = conditionsVerifier.verifyConditions(
                operator = triggerEvent.conditionOperator,
                conditions = triggerEvent.conditions,
            )

            progressListener?.onEventProcessingCompleted(
                triggerEvent,
                results.fulfilled == true,
                results.getAllTriggerConditionsResults(),
            )
            if (results.fulfilled == true) {
                executeForegroundActions(triggerEvent, results) {
                    progressListener?.onEventActionsExecuted(triggerEvent, results.getAllTriggerConditionsResults())
                }
            }
        }
    }

    private suspend fun processScreenEvents(screenFrame: Bitmap) {
        // Set the current screen image
        imageDetector.setScreenBitmap(screenFrame, processingTag)

        try {
            // Check all events
            for (screenEvent in processingState.getScreenEvents()) {
                // Enabled state of the event might have changed during the loop
                if (!processingState.isEventEnabled(screenEvent.id.databaseId)) continue

                // No conditions ? This should not happen, skip this event
                if (screenEvent.conditions.isEmpty()) continue

                // Event is under cooldown, skip it
                if (processingState.isCooldownRunning(screenEvent)) continue

                progressListener?.onEventProcessingStarted(screenEvent)
                val results = conditionsVerifier.verifyConditions(
                    operator = screenEvent.conditionOperator,
                    conditions = screenEvent.conditions,
                )

                progressListener?.onEventProcessingCompleted(
                    screenEvent,
                    results.fulfilled == true,
                    results.getAllScreenConditionsResults(),
                )
                if (results.fulfilled == true) {
                    executeForegroundActions(screenEvent, results) {
                        progressListener?.onEventActionsExecuted(screenEvent, results.getAllScreenConditionsResults())
                    }

                    processingState.startCooldownIfNeeded(screenEvent)
                    if (!screenEvent.keepDetecting) break
                }

                // Stop processing if requested
                yield()
            }
        } finally {
            // We are done processing this frame, release it
            imageDetector.releaseScreenBitmap(screenFrame)
        }
    }

    private suspend fun executeForegroundActions(
        event: com.buzbuz.smartautoclicker.core.domain.model.event.Event,
        results: ConditionsResults,
        onActionsExecuted: () -> Unit,
    ) {
        withContext(Dispatchers.Main.immediate) { fillerController.onForegroundStarted() }
        val trigger = event as? TriggerEvent
        if (trigger != null) {
            val safeName = trigger.name.replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(40)
            diagnosticLogger?.log(
                "ScenarioProcessor",
                "TRIGGER FOREGROUND START eventId=${trigger.id.databaseId} name=$safeName",
            )
        }
        try {
            actionExecutor.executeActions(event, results)
            onActionsExecuted()
        } finally {
            if (trigger != null) {
                diagnosticLogger?.log(
                    "ScenarioProcessor",
                    "TRIGGER FOREGROUND END eventId=${trigger.id.databaseId}",
                )
            }
            withContext(Dispatchers.Main.immediate) { fillerController.onForegroundFinished() }
        }
    }
}
