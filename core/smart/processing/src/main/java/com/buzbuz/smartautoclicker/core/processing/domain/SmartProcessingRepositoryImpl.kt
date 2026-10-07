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
@file:JvmName("SmartProcessingRepositoryKt")

package com.buzbuz.smartautoclicker.core.processing.domain

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

import com.buzbuz.smartautoclicker.core.base.addDumpTabulationLvl
import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.Main
import com.buzbuz.smartautoclicker.core.base.dumpWithTimeout
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.processing.data.DetectorEngine
import com.buzbuz.smartautoclicker.core.processing.data.DetectorState
import com.buzbuz.smartautoclicker.core.processing.diagnostics.DiagnosticLogger
import com.buzbuz.smartautoclicker.core.processing.domain.model.DetectionState
import com.buzbuz.smartautoclicker.core.processing.domain.model.toDetectionState
import com.buzbuz.smartautoclicker.core.processing.domain.trying.ActionTry
import com.buzbuz.smartautoclicker.core.processing.domain.trying.ScreenConditionTry
import com.buzbuz.smartautoclicker.core.processing.domain.trying.ImageEventTry
import com.buzbuz.smartautoclicker.core.processing.domain.trying.ScenarioTry

import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class SmartProcessingRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    @Dispatcher(Main) mainDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    private val scenarioRepository: IRepository,
    private val detectorEngine: DetectorEngine,
    private val diagnosticLogger: DiagnosticLogger,
) : SmartProcessingRepository {

    private val coroutineScopeMain: CoroutineScope =
        CoroutineScope(SupervisorJob() + mainDispatcher)
    private val coroutineScopeIo: CoroutineScope =
        CoroutineScope(SupervisorJob() + ioDispatcher)

    private val wakeLock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).let { powerManager ->
            @Suppress("DEPRECATION") // Deprecated except for use case without a layout
            powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Klickr::Detection").apply {
                setReferenceCounted(false)
            }
        }

    private var projectionErrorHandler: (() -> Unit)? = null

    /** Stop the detection automatically after selected delay */
    private var autoStopJob: Job? = null

    private val _scenarioId: MutableStateFlow<Identifier?> = MutableStateFlow(null)
    override val scenarioId: StateFlow<Identifier?> = _scenarioId

    override val detectionState: Flow<DetectionState> = detectorEngine.state
        .mapNotNull { it.toDetectionState() }

    private val shouldKeepScreenOn: Flow<Boolean> = _scenarioId
        .combine(detectionState) { id, state ->
            id ?: return@combine false
            val scenario = scenarioRepository.getScenario(id.databaseId) ?: return@combine false

            state == DetectionState.DETECTING && scenario.keepScreenOn
        }
        .distinctUntilChanged()

    override val canStartDetection: Flow<Boolean> = scenarioId
        .filterNotNull()
        .flatMapLatest { scenarioRepository.getEventsFlow(it.databaseId) }
        .combine(detectionState) { events, state ->
            if (state == DetectionState.INACTIVE || state == DetectionState.ERROR_NO_NATIVE_LIB)
                return@combine false

            events.forEach {
                event -> if (event.enabledOnStart) return@combine true
            }
            false
        }


    init {
        shouldKeepScreenOn.onEach(::updateWakeLockState).launchIn(coroutineScopeIo)
    }

    override fun setScenarioId(identifier: Identifier, markAsUsed: Boolean) {
        _scenarioId.value = identifier

        if (markAsUsed) {
            coroutineScopeIo.launch { scenarioRepository.markAsUsed(identifier) }
        }
    }

    override fun setProjectionErrorHandler(handler: () -> Unit) {
        projectionErrorHandler = handler
    }

    override fun getScenarioId(): Identifier? = _scenarioId.value

    override fun isRunning(): Boolean =
        detectorEngine.state.value == DetectorState.DETECTING

    override fun startScreenRecord(resultCode: Int, data: Intent) {
        detectorEngine.startScreenRecord(resultCode, data) {
            coroutineScopeMain.launch { projectionErrorHandler?.invoke() }
        }
    }

    override suspend fun startDetection(
        context: Context,
        liveDebugging: Boolean,
        generateReport: Boolean,
        autoStopDuration: Duration?,
    ) {
        val id = scenarioId.value?.databaseId ?: return
        diagnosticLogger.startSession(
            scenarioId = id,
            details = "autoStopDurationPresent=${autoStopDuration != null} " +
                "autoStopDurationMs=${autoStopDuration?.inWholeMilliseconds} detectorState=${detectorEngine.state.value}",
        )
        diagnosticLogger.log(
            "SmartProcessingRepository",
            "startDetection requested scenarioId=$id autoStopDurationMs=${autoStopDuration?.inWholeMilliseconds}",
        )
        val scenario = scenarioRepository.getScenario(id) ?: run {
            diagnosticLogger.endSession(detectorEngine.state.value.name, "SCENARIO_NOT_FOUND")
            return
        }
        val events = scenarioRepository.getScreenEvents(id)
        val triggerEvents = scenarioRepository.getTriggerEvents(id)
        val fillerEvents = scenarioRepository.getFillerEvents(id)
        val counters = scenarioRepository.getCounters(id)
        diagnosticLogger.log(
            "SmartProcessingRepository",
            "detector state before start=${detectorEngine.state.value}",
        )
        detectorEngine.startDetection(
            context = context,
            scenario = scenario,
            screenEvents = events,
            triggerEvents = triggerEvents,
            counters = counters,
            liveDebugging = liveDebugging,
            generateReport = generateReport,
            fillerEvents = fillerEvents,
        )
        diagnosticLogger.log(
            "SmartProcessingRepository",
            "startDetection returned detectorState=${detectorEngine.state.value}",
        )

        autoStopDuration?.let { duration ->
            if (autoStopJob?.isActive == true) {
                diagnosticLogger.log("SmartProcessingRepository", "auto-stop cancelled")
            }
            autoStopJob?.cancel()
            diagnosticLogger.log(
                "SmartProcessingRepository",
                "auto-stop scheduled durationMs=${duration.inWholeMilliseconds}",
            )
            autoStopJob = coroutineScopeIo.launch {
                delay(duration)
                diagnosticLogger.log("SmartProcessingRepository", "auto-stop fired")
                stopDetection(StopReason.AUTO_STOP)
            }
        }
    }

    override fun stopDetection() {
        stopDetection(StopReason.USER_OR_EXTERNAL_REQUEST)
    }

    private fun stopDetection(reason: StopReason) {
        diagnosticLogger.log("SmartProcessingRepository", "stopDetection requested reason=$reason")
        detectorEngine.stopDetection(reason.name)
        if (autoStopJob?.isActive == true && reason != StopReason.AUTO_STOP) {
            diagnosticLogger.log("SmartProcessingRepository", "auto-stop cancelled")
        }
        autoStopJob?.cancel()
        autoStopJob = null
    }

    override fun stopScreenRecord() {
        diagnosticLogger.log("SmartProcessingRepository", "stopScreenRecord requested")
        projectionErrorHandler = null
        detectorEngine.stopScreenRecord()
        _scenarioId.value = null
    }

    override suspend fun tryEvent(context: Context, scenario: Scenario, event: ScreenEvent) {
        val counters = scenarioRepository.getCounters(scenario.id.databaseId)
        val triedElement = ImageEventTry(scenario, counters, event)

        tryElement(
            context,
            triedElement,
        )
    }

    override suspend fun tryScreenCondition(context: Context, scenario: Scenario, condition: ScreenCondition) {
        val counters = scenarioRepository.getCounters(scenario.id.databaseId)
        val triedElement = ScreenConditionTry(scenario, counters, condition)

        tryElement(
            context = context,
            elementTry = triedElement,
        )
    }

    override suspend fun tryAction(context: Context,  scenario: Scenario, action: Action) {
        val counters = scenarioRepository.getCounters(scenario.id.databaseId)

        tryElement(
            context = context,
            elementTry = ActionTry(scenario, counters, action),
        )
    }

    private fun tryElement(context: Context, elementTry: ScenarioTry) {
        Log.d(TAG, "Trying element: Scenario=${elementTry.scenario}; ImageEvents=${elementTry.screenEvents}")
        detectorEngine.startDetection(
            context = context,
            scenario = elementTry.scenario,
            screenEvents = elementTry.screenEvents,
            triggerEvents = elementTry.triggerEvents,
            counters = elementTry.counters,
            liveDebugging = true,
            generateReport = false,
        )
    }

    @SuppressLint("WakelockTimeout")
    private fun updateWakeLockState(keepScreenOn: Boolean) {
        Log.i(TAG, "updateWakeLockState: keepScreenOn=$keepScreenOn")
        if (keepScreenOn) wakeLock.acquire()
        else wakeLock.release()
    }

    override fun dump(writer: PrintWriter, prefix: CharSequence) {
        val contentPrefix = prefix.addDumpTabulationLvl()

        writer.apply {
            append(prefix).println("* SmartProcessingRepository:")

            append(contentPrefix)
                .append("- scenarioId=${scenarioId.value}; ")
                .append("canStartDetection=${canStartDetection.dumpWithTimeout() ?: false}; ")
                .append("detectionState=${detectionState.dumpWithTimeout() ?: DetectionState.INACTIVE}; ")
                .println()
        }
    }
}

private const val TAG = "SmartProcessingRepository"

/** The minimum detection quality for the algorithm. */
const val DETECTION_QUALITY_MIN = com.buzbuz.smartautoclicker.core.detection.DETECTION_QUALITY_MIN