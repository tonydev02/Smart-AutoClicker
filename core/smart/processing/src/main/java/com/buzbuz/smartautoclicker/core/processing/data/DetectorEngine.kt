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
package com.buzbuz.smartautoclicker.core.processing.data

import android.content.Context
import android.content.Intent
import android.media.Image
import android.media.projection.MediaProjectionManager
import android.util.Log

import com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.OCRModelsRepository
import com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.domain.OCRAlphabet
import com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.domain.OCRModel
import com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.domain.OCRModelState
import com.buzbuz.smartautoclicker.core.base.data.AppComponentsProvider
import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import com.buzbuz.smartautoclicker.core.bitmaps.BitmapRepository
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.display.recorder.DisplayRecorder
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.detection.NativeDetector
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager
import com.buzbuz.smartautoclicker.core.domain.ext.getAllOCRAlphabets
import com.buzbuz.smartautoclicker.core.domain.model.counter.Counter
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.processing.data.processor.ScenarioProcessor
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ScalingManager
import com.buzbuz.smartautoclicker.core.processing.diagnostics.DiagnosticLogger
import com.buzbuz.smartautoclicker.core.settings.domain.SettingsRepository
import com.buzbuz.smartautoclicker.core.processing.domain.SmartProcessingListener

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * Detects [ScreenEvent] conditions on a display and execute its actions.
 *
 * In order to detect, you must start recording the screen to get images to detect on, this can be done by calling
 * [startScreenRecord]. Or, you can start the detection of a list of [ScreenEvent] by using [startDetection].
 * The states of the recording and the detection are available in [state].
 * Once you no longer needs to capture or detect, call [stopDetection] or [stopScreenRecord] to release all processing resources.
 */
@Singleton
class DetectorEngine @Inject constructor(
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val displayConfigManager: DisplayConfigManager,
    private val bitmapRepository: BitmapRepository,
    private val scalingManager: ScalingManager,
    private val displayRecorder: DisplayRecorder,
    private val actionExecutor: AndroidActionExecutor,
    private val settingsRepository: SettingsRepository,
    private val appComponentsProvider: AppComponentsProvider,
    private val debuggingListener: SmartProcessingListener,
    private val ocrModelsRepository: OCRModelsRepository,
    private val diagnosticLogger: DiagnosticLogger,
) {

    /** Process the events conditions to detect them on the screen. */
    private var scenarioProcessor: ScenarioProcessor? = null
    /** Detect the condition images on the screen image. */
    private var imageDetector: ImageDetector? = null

    /** Coroutine scope for the image processing. */
    private var processingScope: CoroutineScope? = null
    /** Coroutine job for the image currently processed. */
    private var processingJob: Job? = null
    private var processingGeneration: Long = 0L
    private var orientationGeneration: Long = 0L
    /** Coroutine job for the cleaning of the detection once stopped. */
    private var processingShutdownJob: Job? = null
    /** Coroutine job for the debounced orientation change handler. */
    private var orientationChangeJob: Job? = null

    /**
     * When true, [processScreenImages] will exit its loop after the current frame finishes rather
     * than being canceled mid-execution. Used by orientation-change handling so in-progress action
     * sequences are not interrupted.
     */
    @Volatile private var orientationChangeRequested: Boolean = false

    private val screenOrientationListener: (Context) -> Unit = { onScreenOrientationChanged() }

    /** Backing property for [state].*/
    private val _state = MutableStateFlow(DetectorState.CREATED)
    /** Current state of the detector. */
    internal val state: StateFlow<DetectorState> = _state
    private fun transitionTo(newState: DetectorState, reason: String) {
        val oldState = _state.value
        if (oldState != newState) {
            diagnosticLogger.log("DetectorEngine", "state $oldState -> $newState reason=$reason")
        }
        _state.value = newState
        if (newState.name.startsWith("ERROR_")) diagnosticLogger.endSession(newState.name, "ERROR_STATE")
    }

    private suspend fun emitState(newState: DetectorState, reason: String) {
        val oldState = _state.value
        if (oldState != newState) {
            diagnosticLogger.log("DetectorEngine", "state $oldState -> $newState reason=$reason")
        }
        _state.emit(newState)
        if (newState.name.startsWith("ERROR_")) diagnosticLogger.endSession(newState.name, "ERROR_STATE")
    }

    /** Scenario currently processed. Null if not detecting. */
    private var minProcessingDurationNs: Long = DEFAULT_MIN_PROCESSING_DURATION_NS

    /**
     * Start the screen detection.
     *
     * This requires the media projection permission code and its data intent, they both can be retrieved using the
     * results of the activity intent provided by [MediaProjectionManager.createScreenCaptureIntent] (this Intent shows
     * the dialog warning about screen recording privacy). Any attempt to call this method without the correct screen
     * capture intent result will lead to a crash.
     *
     * Once started, you can use [startDetection]. Once you are done, call [stopScreenRecord].
     *
     * @param resultCode the result code provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param data the data intent provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param onRecordingStopped called when the screen recording is no longer running and a new request for media
     * projection should be done.
     */
    internal fun startScreenRecord(
        resultCode: Int,
        data: Intent,
        onRecordingStopped: (() -> Unit)?,
    ) {
        if (_state.value != DetectorState.CREATED) {
            Log.w(TAG, "startScreenRecord: Screen record is already started")
            return
        }

        val displaySize = displayConfigManager.displayConfig.sizePx
        if (displaySize.x <= 0 || displaySize.y <= 0) {
            Log.w(TAG, "startScreenRecord: Invalid display size $displaySize")
            return
        }

        transitionTo(DetectorState.TRANSITIONING, "startScreenRecord")

        Log.i(TAG, "startScreenRecord")
        diagnosticLogger.log("DetectorEngine", "startScreenRecord ENTER size=${displaySize.x}x${displaySize.y}")

        processingScope = CoroutineScope(ioDispatcher.limitedParallelism(1))

        displayConfigManager.addOrientationListener(screenOrientationListener)

        processingScope?.launch {
            displayRecorder.apply {
                diagnosticLogger.log("DisplayRecorder", "startProjection requested")
                startProjection(resultCode, data) {
                    diagnosticLogger.log(
                        "DetectorEngine",
                        "PROJECTION LOST CALLBACK RECEIVED state=${_state.value} " +
                            "processingGeneration=$processingGeneration orientationGeneration=$orientationGeneration " +
                            "orientationChangeRequested=$orientationChangeRequested",
                    )
                    Log.w(TAG, "projection lost")
                    this@DetectorEngine.stopScreenRecord("PROJECTION_LOST")
                    onRecordingStopped?.invoke()
                }
                diagnosticLogger.log("DisplayRecorder", "startProjection completed")
                diagnosticLogger.log("DisplayRecorder", "startScreenRecord dimensions=${displaySize.x}x${displaySize.y}")
                startScreenRecord(displaySize)
                diagnosticLogger.log("DisplayRecorder", "startScreenRecord completed")
            }

            val captureValid = displayRecorder.validateScreenCapture()
            diagnosticLogger.log("DetectorEngine", "startScreenRecord EXIT captureValid=$captureValid")
            emitState(
                if (!captureValid) DetectorState.ERROR_SCREEN_IMAGE_CAPTURE_FAILED else DetectorState.RECORDING,
                "startScreenRecord captureValid=$captureValid",
            )
        }
    }

    /**
     * Start the screen detection.
     *
     * After calling this method, all [Image] displayed on the screen will be checked for the provided clicks conditions
     * fulfillment. For each image, the first event in the list that is detected will be notified through the provided
     * callback.
     * [state] should be RECORDING to capture. Detection can be stopped with [stopDetection] or [stopScreenRecord].
     */
    internal fun startDetection(
        context: Context,
        scenario: Scenario,
        screenEvents: List<ScreenEvent>,
        triggerEvents: List<TriggerEvent>,
        counters: List<Counter>,
        liveDebugging: Boolean,
        generateReport: Boolean,
        imageDetectorFactory: () -> ImageDetector? = NativeDetector::newInstance,
        fillerEvents: List<com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent> = emptyList(),
    ) {
        diagnosticLogger.log("DetectorEngine", "startDetection ENTER state=${_state.value} scenarioId=${scenario.id.databaseId}")
        if (_state.value != DetectorState.RECORDING) {
            Log.w(TAG, "startDetection: Screen record is not started.")
            return
        }

        val detector = imageDetectorFactory()
        if (detector == null) {
            Log.e(TAG, "startDetection: native library not found.")
            transitionTo(DetectorState.ERROR_NATIVE_DETECTOR_LIB_NOT_FOUND, "native detector unavailable")
            return
        }

        transitionTo(DetectorState.TRANSITIONING, "startDetection")
        Log.i(TAG, "startDetection")

        processingScope?.launchProcessingJob {
            // Setup native detector
            imageDetector = detector
            detector.init()

            // Setup text detection models if needed
            val requiredAlphabets = screenEvents.getAllOCRAlphabets()
            if (requiredAlphabets.isNotEmpty()) {
                if (!detector.loadOcrModels(requiredAlphabets)) {
                    transitionTo(DetectorState.ERROR_OCR_MODEL_NOT_FOUND, "OCR model unavailable")
                    return@launchProcessingJob
                }
            }

            // Clear image cache and compute scaling info for detection
            bitmapRepository.clearCache()

            // Set the display projection to the scaled size
            diagnosticLogger.log("DisplayRecorder", "resizeDisplay requested for detection")
            val scaledSize = scalingManager.startScaling(
                quality = scenario.detectionQuality.toDouble(),
                screenEvents = screenEvents,
            )
            displayRecorder.resizeDisplay(displaySize = scaledSize)
            diagnosticLogger.log(
                "DisplayRecorder",
                "resizeDisplay completed dimensions=${scaledSize.x}x${scaledSize.y}",
            )

            // Compute minimal processing duration
            val frameLimit = scenario.computeRate
            minProcessingDurationNs =
                if (frameLimit <= 0.0) DEFAULT_MIN_PROCESSING_DURATION_NS
                else (ONE_SECOND_IN_NANO / frameLimit).toLong()

            Log.i(TAG, "Process scenario at ${if (frameLimit == 0.0) "unlimited" else frameLimit} FPS " +
                    "(${minProcessingDurationNs}ns per loop)")

            // Setup listeners if needed
            if (liveDebugging || generateReport) {
                debuggingListener.onSessionStarted(
                    scenario = scenario,
                    counters = counters,
                    generateLiveEvents = liveDebugging,
                    generateReport = generateReport,
                )
            }

            // Instantiate the processor and initialize its detection state.
            scenarioProcessor = ScenarioProcessor(
                processingTag = appComponentsProvider.originalAppId,
                imageDetector = detector,
                scalingManager = scalingManager,
                randomize = scenario.randomize,
                screenEvents = screenEvents,
                triggerEvents = triggerEvents,
                counters = counters,
                bitmapSupplier = bitmapRepository::getImageConditionBitmap,
                androidExecutor = actionExecutor,
                unblockWorkaroundEnabled = settingsRepository.isInputBlockWorkaroundEnabled(),
                onStopRequested = { stopDetection("ALL_EVENTS_DISABLED") },
                fillerEvents = fillerEvents,
                progressListener = if (liveDebugging || generateReport) debuggingListener else null,
                diagnosticLogger = diagnosticLogger,
            )
            scenarioProcessor?.onScenarioStart(context)
            diagnosticLogger.log("DetectorEngine", "startDetection EXIT state=${_state.value}")
            processScreenImages()
        }
    }

    /**
     * Called when the orientation of the screen changes.
     * As we now have different screen metrics, we need to stop and start the virtual display with the correct one.
     */
    private fun onScreenOrientationChanged() {
        val requestId = ++orientationGeneration
        val currentSize = displayConfigManager.displayConfig.sizePx
        diagnosticLogger.log(
            "DetectorEngine",
            "orientation #$requestId CALLBACK state=${_state.value} size=${currentSize.x}x${currentSize.y}",
        )
        if (_state.value != DetectorState.DETECTING && _state.value != DetectorState.RECORDING) return

        Log.d(TAG, "onOrientationChanged")
        diagnosticLogger.log(
            "DetectorEngine",
            "orientation #$requestId cancelling prior orientation job=${orientationChangeJob?.isActive}",
        )
        orientationChangeJob?.cancel()
        val job = processingScope?.launch {
            delay(ORIENTATION_CHANGE_DEBOUNCE_MS.milliseconds)
            diagnosticLogger.log("DetectorEngine", "orientation #$requestId DEBOUNCE COMPLETE")

            if (_state.value == DetectorState.DETECTING) {
                orientationChangeRequested = true
                diagnosticLogger.log(
                    "DetectorEngine",
                    "orientation #$requestId orientationChangeRequested=true awaiting generation=$processingGeneration",
                )
                diagnosticLogger.log("DetectorEngine", "orientation #$requestId JOIN START")
                val joinedJob = processingJob
                joinedJob?.join()
                diagnosticLogger.log(
                    "DetectorEngine",
                    "orientation #$requestId JOIN END jobComplete=${joinedJob?.isCompleted} " +
                        "jobCancelled=${joinedJob?.isCancelled}",
                )
                orientationChangeRequested = false
                diagnosticLogger.log("DetectorEngine", "orientation #$requestId orientationChangeRequested=false")
                debuggingListener.onEventsProcessingCancelled()
            }

            diagnosticLogger.log("DetectorEngine", "orientation #$requestId refreshScaling START")
            val newSize = scalingManager.refreshScaling()
            diagnosticLogger.log("DetectorEngine", "orientation #$requestId refreshScaling END size=${newSize.x}x${newSize.y}")
            diagnosticLogger.log("DetectorEngine", "orientation #$requestId resizeDisplay START")
            displayRecorder.resizeDisplay(displaySize = newSize)
            diagnosticLogger.log("DetectorEngine", "orientation #$requestId resizeDisplay END")

            if (_state.value == DetectorState.DETECTING) {
                diagnosticLogger.log("DetectorEngine", "orientation #$requestId restart processing loop REQUESTED")
                processingScope?.launchProcessingJob {
                    diagnosticLogger.log("DetectorEngine", "orientation #$requestId restart processing loop LAUNCHED generation=$processingGeneration")
                    processScreenImages()
                }
            }
            diagnosticLogger.log("DetectorEngine", "orientation #$requestId COMPLETE")
        }
        orientationChangeJob = job
        job?.invokeOnCompletion { cause ->
            diagnosticLogger.log(
                "DetectorEngine",
                "orientation #$requestId job ${when {
                    cause == null -> "COMPLETED"
                    cause is kotlinx.coroutines.CancellationException -> "CANCELLED"
                    else -> "FAILED exceptionClass=${cause.javaClass.simpleName}"
                }}",
            )
        }
    }

    /**
     * Stop the screen detection started with [startDetection].
     *
     * After a call to this method, the events provided in the start method will no longer be checked on the current
     * image. Note that this will not stop the screen recording, you should still call [stopScreenRecord] to completely
     * release the [DetectorEngine] resources.
     */
    internal fun stopDetection(reason: String = "USER_OR_EXTERNAL_REQUEST") {
        diagnosticLogger.log("DetectorEngine", "stopDetection ENTER reason=$reason state=${_state.value}")
        if (_state.value != DetectorState.DETECTING) {
            Log.w(TAG, "stopDetection: detection is not started.")
            return
        }
        transitionTo(DetectorState.TRANSITIONING, "stopDetection reason=$reason")

        processingShutdownJob = processingScope?.launch {
            Log.i(TAG, "stopDetection")
            diagnosticLogger.log("DetectorEngine", "stopDetection cleanup START")

            processingJob?.cancelAndJoin()
            processingJob = null
            imageDetector?.close()
            imageDetector = null
            scenarioProcessor?.onScenarioEnd()
            scenarioProcessor = null
            debuggingListener.onSessionEnded()

            scalingManager.stopScaling()
            displayRecorder.resizeDisplay(displayConfigManager.displayConfig.sizePx)

            emitState(DetectorState.RECORDING, "stopDetection cleanup complete")
            processingShutdownJob = null
            diagnosticLogger.log("DetectorEngine", "stopDetection cleanup END state=${_state.value}")
            if (reason != "SCREEN_RECORD_STOP" && reason != "PROJECTION_LOST") {
                diagnosticLogger.endSession(_state.value.name, reason)
            }
        }
    }

    /**
     * Stop the screen recording and the detection, if any.
     *
     * First, calls [stopDetection] if the detection was active. Then, stop the screen recording and release any related
     * resources.
     */
    internal fun stopScreenRecord(reason: String = "SCREEN_RECORD_STOP") {
        diagnosticLogger.log("DetectorEngine", "stopScreenRecord ENTER reason=$reason state=${_state.value}")
        if (_state.value == DetectorState.DETECTING) {
            stopDetection(reason)
            stopRecording(reason)
        } else if (_state.value == DetectorState.RECORDING) {
            stopRecording(reason)
        }
    }

    private fun stopRecording(reason: String) {
        Log.i(TAG, "stopScreenRecord")
        diagnosticLogger.log("DetectorEngine", "stopRecording ENTER")
        transitionTo(DetectorState.TRANSITIONING, "stopRecording")

        processingScope?.launch {
            processingShutdownJob?.join()

            displayConfigManager.removeOrientationListener(screenOrientationListener)
            diagnosticLogger.log("DisplayRecorder", "stopProjection START")
            displayRecorder.stopProjection()
            diagnosticLogger.log("DisplayRecorder", "stopProjection END")
            emitState(DetectorState.CREATED, "stopRecording complete")

            processingScope?.cancel()
            processingScope = null
            diagnosticLogger.log("DetectorEngine", "stopRecording END state=${_state.value}")
            diagnosticLogger.endSession(_state.value.name, reason)
        }
    }

    /** Process the latest images provided by the [DisplayRecorder]. */
    private suspend fun processScreenImages() {
        diagnosticLogger.log(
            "DetectorEngine",
            "processScreenImages ENTER generation=$processingGeneration scopeActive=${processingScope?.isActive}",
        )
        emitState(DetectorState.DETECTING, "processScreenImages")

        var processingDurationNs: Long
        var firstFrame = true
        try {
            while (processingJob?.isActive == true && !orientationChangeRequested) {
                displayRecorder.acquireLatestBitmap()?.let { screenFrame ->
                    if (firstFrame) {
                        diagnosticLogger.log("DetectorEngine", "processing-loop #$processingGeneration first-frame acquired")
                        firstFrame = false
                    }
                    processingDurationNs = measureNanoTime {
                        scenarioProcessor?.process(screenFrame)
                    }

                    // Avoid looping infinitely to quickly for nothing.
                    if (processingDurationNs < minProcessingDurationNs) {
                        delay(duration = max(
                            a = 1,
                            b = (minProcessingDurationNs - processingDurationNs) / ONE_MILLISECOND_IN_NANO,
                        ).milliseconds)
                    }
                } ?: delay(NO_IMAGE_DELAY_MS.milliseconds)
            }
        } finally {
            val exitReason = when {
                orientationChangeRequested -> "orientationRequested=true"
                processingJob?.isCancelled == true -> "processingJob cancelled"
                else -> "normal return"
            }
            diagnosticLogger.log(
                "DetectorEngine",
                "processScreenImages EXIT generation=$processingGeneration $exitReason state=${_state.value}",
            )
        }
    }

    /**
     * Creates a new job executing the provided job automatically once the job is effectively created.
     * This allows to check the job state correctly within the [block].
     *
     * The job will be affected to the [processingJob] variable.
     *
     * @param block the coroutine code which will be invoked in the context of the provided scope.
     */
    private fun CoroutineScope.launchProcessingJob(block: suspend CoroutineScope.() -> Unit) {
        val generation = ++processingGeneration
        diagnosticLogger.log("DetectorEngine", "processing-loop #$generation CREATED")
        val job = launch(
            start = CoroutineStart.LAZY,
        ) {
            diagnosticLogger.log("DetectorEngine", "processing-loop #$generation START")
            block()
        }
        processingJob = job
        job.invokeOnCompletion { cause ->
            diagnosticLogger.log(
                "DetectorEngine",
                "processing-loop #$generation COMPLETED cause=${cause?.javaClass?.simpleName ?: "null"} " +
                    "scopeActive=${processingScope?.isActive}",
            )
        }
        job.start()
    }

    private suspend fun ImageDetector.loadOcrModels(required: Set<OCRAlphabet>): Boolean {
        val ocrDetectModelPath = ocrModelsRepository.getDetectionModel()?.getOCRModelPath()
        val ocrRecoModels = ocrModelsRepository.getTextConditionsRecognitionModels(required)
        if (ocrDetectModelPath.isNullOrEmpty() || ocrRecoModels.size != required.size) {
            Log.e(TAG, "Can't start detection, OCR models config is invalid. " +
                    "Detection:$ocrDetectModelPath; Recognition:$ocrRecoModels")
            return false
        }

        return loadTextDetectionModels(ocrDetectModelPath, ocrRecoModels)
    }
}

private fun OCRModel.getOCRModelPath(): String? =
    (state as? OCRModelState.Installed)?.path

private suspend fun OCRModelsRepository.getTextConditionsRecognitionModels(required: Set<OCRAlphabet>): Map<String, String> =
    buildMap {
        required.forEach { alphabet ->
            val modelPath = getRecognitionModelPath(alphabet) ?: return@forEach
            put(alphabet.name, modelPath)
        }
    }


/** The different states of the [DetectorEngine]. */
internal enum class DetectorState {
    /** The engine is created and ready to be used. */
    CREATED,
    /**
     * The engine is transitioning between two states.
     * During this state, all call to the engine will be ignored.
     */
    TRANSITIONING,
    /** The screen is being recorded. */
    RECORDING,
    /** The screen is being recorded and the detection is running. */
    DETECTING,
    /** The native lib can't be loaded and the detection can't be used. */
    ERROR_NATIVE_DETECTOR_LIB_NOT_FOUND,
    /** The text detection models required for this scenario are not found. */
    ERROR_OCR_MODEL_NOT_FOUND,
    /** The device's GPU driver can't expose screen capture buffers for CPU access. */
    ERROR_SCREEN_IMAGE_CAPTURE_FAILED,
}

/**
 * Waiting delay after getting a null image.
 * This is to avoid spamming when there is no image.
 */
private const val NO_IMAGE_DELAY_MS = 20L
/** Debounce delay for orientation changes, to avoid restarting detection on every intermediate rotation event. */
private const val ORIENTATION_CHANGE_DEBOUNCE_MS = 100L

/** The value of 1 second in nanoseconds. */
private const val ONE_SECOND_IN_NANO = 1000000000L
/** The value of 1 milliseconds  in nanoseconds.*/
private const val ONE_MILLISECOND_IN_NANO = 1000000L
/** The default minimal processing duration in nanoseconds. */
private const val DEFAULT_MIN_PROCESSING_DURATION_NS = ONE_MILLISECOND_IN_NANO

/** Tag for logs. */
private const val TAG = "DetectorEngine"