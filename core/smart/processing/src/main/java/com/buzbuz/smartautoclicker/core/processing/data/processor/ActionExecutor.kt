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

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent as AndroidIntent
import android.graphics.Path
import android.graphics.Point
import android.util.Log

import com.buzbuz.smartautoclicker.core.base.workarounds.UnblockGestureScheduler
import com.buzbuz.smartautoclicker.core.base.workarounds.buildUnblockGesture
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.common.actions.gesture.buildSingleStroke
import com.buzbuz.smartautoclicker.core.common.actions.gesture.line
import com.buzbuz.smartautoclicker.core.common.actions.gesture.moveTo
import com.buzbuz.smartautoclicker.core.common.actions.gesture.addRandomizedStroke
import com.buzbuz.smartautoclicker.core.common.actions.model.ActionNotificationRequest
import com.buzbuz.smartautoclicker.core.common.actions.text.findCounterReferences
import com.buzbuz.smartautoclicker.core.common.actions.text.replaceCounterReferences
import com.buzbuz.smartautoclicker.core.common.actions.utils.getPauseDurationMs
import com.buzbuz.smartautoclicker.core.domain.model.counter.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.action.Intent
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.action.Swipe
import com.buzbuz.smartautoclicker.core.domain.model.action.MultiTouch
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.domain.model.action.TouchStroke
import com.buzbuz.smartautoclicker.core.domain.model.action.TouchMode
import com.buzbuz.smartautoclicker.core.domain.model.action.PauseMode
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.action.ChangeCounter
import com.buzbuz.smartautoclicker.core.domain.model.action.Notification
import com.buzbuz.smartautoclicker.core.domain.model.action.SetText
import com.buzbuz.smartautoclicker.core.domain.model.action.MAX_WEBHOOK_TELEGRAM_MESSAGE_LENGTH
import com.buzbuz.smartautoclicker.core.domain.model.action.Webhook
import com.buzbuz.smartautoclicker.core.domain.model.action.WebhookMode
import com.buzbuz.smartautoclicker.core.domain.model.action.SystemAction
import com.buzbuz.smartautoclicker.core.domain.model.action.intent.putDomainExtra
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.processing.data.processor.state.ProcessingState

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import kotlinx.coroutines.CancellationException
import kotlin.random.Random

/**
 * Execute the actions of an event.
 *
 * @param androidExecutor the executor for the actions requiring an interaction with Android.
 * @param processingState the state of the current processing (counters, enabled events...).
 * @param randomize true to randomize the actions values a bit (positions, timers...), false to be precise.
 */
internal class ActionExecutor(
    private val androidExecutor: AndroidActionExecutor,
    private val processingState: ProcessingState,
    randomize: Boolean,
    unblockWorkaroundEnabled: Boolean = false,
    private val webhookHttpClient: WebhookHttpClient = HttpUrlConnectionWebhookHttpClient(),
) {

    init { androidExecutor.resetState() }

    private val random: Random? =
        if (randomize) Random(System.currentTimeMillis()) else null

    private val randomAreaRandom: Random = Random(System.nanoTime())
    private val pauseRangeRandom: Random = Random(System.nanoTime())
    private val unblockGestureScheduler: UnblockGestureScheduler? =
        if (unblockWorkaroundEnabled) UnblockGestureScheduler()
        else null


    suspend fun onScenarioLoopFinished() {
        if (unblockGestureScheduler?.shouldTrigger() == true) {
            withContext(Dispatchers.Main) {
                Log.i(TAG, "Injecting unblock gesture")
                androidExecutor.dispatchGesture(
                    GestureDescription.Builder().buildUnblockGesture()
                )
            }
        }
    }

    suspend fun executeActions(event: Event, results: ConditionsResults? = null) {
        event.actions.forEach { action ->
            when (action) {
                is Click -> executeClick(event, action, results)
                is MultiTouch -> executeMultiTouch(action)
                is RandomMovement -> executeRandomMovement(action)
                is Swipe -> executeSwipe(action)
                is Pause -> executePause(action)
                is Intent -> executeIntent(action)
                is ToggleEvent -> executeToggleEvent(action)
                is ChangeCounter -> executeChangeCounter(action)
                is Notification -> executeNotification(event, action)
                is SystemAction -> executeSystemAction(action)
                is SetText -> executeSetText(action)
                is Webhook -> executeWebhook(action)
            }
        }
    }

    private suspend fun executeClick(event: Event, click: Click, results: ConditionsResults?) {
        val clickPath = when (click.positionType) {
            Click.PositionType.USER_SELECTED -> {
                click.position?.let { position ->
                    Path().apply { moveTo(position, random) }
                }
            }

            Click.PositionType.ON_DETECTED_CONDITION ->
                getOnConditionClickPath(event, click, results)
        } ?: return

        val clickGesture = GestureDescription.Builder().buildSingleStroke(
            path = clickPath,
            durationMs = click.pressDuration!!,
            random = random,
        )

        withContext(Dispatchers.Main) {
            androidExecutor.dispatchGesture(clickGesture)
        }
    }

    private fun getOnConditionClickPath(event: Event, click: Click, results: ConditionsResults?): Path? {
        if (event !is ScreenEvent) return null

        val result = when {
            event.conditionOperator == OR -> results?.getFirstScreenConditionDetectedResult()
            click.clickOnConditionId != null -> results?.getScreenConditionResult(click.clickOnConditionId!!.databaseId)
            else -> null
        }

        if (result == null) {
            Log.w(TAG, "Click is invalid, can't execute")
            return null
        }

        return Path().apply {
            moveTo(
                position = Point(
                    (result.position?.x ?: 0) + (click.clickOffset?.x ?: 0),
                    (result.position?.y ?: 0) + (click.clickOffset?.y ?: 0),
                ),
                random = random,
            )
        }
    }

    /**
     * Execute the provided swipe.
     * @param swipe the swipe to be executed.
     */
    private suspend fun executeSwipe(swipe: Swipe) {
        val swipeGesture = GestureDescription.Builder().buildSingleStroke(
            path =
                if (swipe.from == null || swipe.to == null) return
                else Path().apply { line(swipe.from, swipe.to, random) },
            durationMs = swipe.swipeDuration!!,
            random = random,
        )

        withContext(Dispatchers.Main) {
            androidExecutor.dispatchGesture(swipeGesture)
        }
    }

    private suspend fun executeMultiTouch(action: MultiTouch) {
        val first = action.firstTouch
        val second = action.secondTouch
        if (!first.isComplete() || !second.isComplete()) return
        val firstDuration = first.durationMs!!
        val secondDuration = second.durationMs!!
        val gesture = GestureDescription.Builder()
            .addRandomizedStroke(first.toPath(randomAreaRandom), firstDuration, startTime = 0L, random = random)
            .addRandomizedStroke(second.toPath(randomAreaRandom), secondDuration, startTime = 0L, random = random)
            .build()

        withContext(Dispatchers.Main) {
            androidExecutor.dispatchGesture(gesture)
        }
    }

    private suspend fun executeRandomMovement(action: RandomMovement) {
        executeRandomMovement(action) { true }
    }

    /**
     * Execute the same random path generation used by a normal action, validating filler ownership
     * on the main thread immediately before handing the gesture to AccessibilityService.
     */
    internal suspend fun executeFillerRandomMovement(action: RandomMovement, mayDispatch: () -> Boolean): Boolean =
        executeRandomMovement(action, mayDispatch)

    private suspend fun executeRandomMovement(action: RandomMovement, mayDispatch: () -> Boolean): Boolean {
        if (!action.isComplete()) return false
        val area = action.area ?: return false
        val durationMs = action.durationMs ?: return false
        val gesture = GestureDescription.Builder()
            .addRandomizedStroke(
                path = generateRandomAreaPath(area, durationMs, randomAreaRandom, action.endPosition),
                durationMs = durationMs,
                startTime = 0L,
                random = random,
            )
            .build()

        return withContext(Dispatchers.Main) {
            if (!mayDispatch()) return@withContext false
            androidExecutor.dispatchGesture(gesture)
            true
        }
    }

    private fun TouchStroke.toPath(areaRandom: Random): Path = when (mode) {
        TouchMode.PRESS -> Path().apply { moveTo(from!!, random) }
        TouchMode.DRAG -> Path().apply { line(from, to, random) }
        TouchMode.RANDOM_AREA -> generateRandomAreaPath(area!!, durationMs!!, areaRandom, randomAreaEnd)
    }

    /**
     * Execute the provided pause.
     * @param pause the pause to be executed.
     */
    private suspend fun executePause(pause: Pause) {
        val duration = when (pause.pauseMode) {
            PauseMode.FIXED -> pause.pauseDuration!!.getPauseDurationMs(random)
            PauseMode.RANDOM_RANGE -> selectRandomPauseDuration(
                pause.randomMinDurationMs!!,
                pause.randomMostLikelyDurationMs!!,
                pause.randomMaxDurationMs!!,
                pause.randomSpread!!,
                pauseRangeRandom,
            )
        }
        delay(duration)
    }

    /**
     * Execute the provided intent.
     * @param intent the intent to be executed.
     */
    private suspend fun executeIntent(intent: Intent) {
        val androidIntent = AndroidIntent().apply {
            action = intent.intentAction!!
            flags = intent.flags!!

            intent.componentName?.let {
                component = intent.componentName
            }

            intent.extras?.forEach { putDomainExtra(it) }
        }

        if (intent.isBroadcast) {
            withContext(Dispatchers.Main) {
                androidExecutor.sendBroadcast(androidIntent)
            }
            delay(INTENT_BROADCAST_DELAY)
        } else {
            withContext(Dispatchers.Main) {
                androidExecutor.startActivity(androidIntent)
            }
            delay(INTENT_START_ACTIVITY_DELAY)
        }
    }

    /**
     * Execute the provided toggle event.
     * @param toggleEvent the toggleEvent to be executed.
     */
    private fun executeToggleEvent(toggleEvent: ToggleEvent) {
        if (toggleEvent.toggleAll) {
            when (toggleEvent.toggleAllType) {
                ToggleEvent.ToggleType.ENABLE -> processingState.enableAll()
                ToggleEvent.ToggleType.DISABLE -> processingState.disableAll()
                ToggleEvent.ToggleType.TOGGLE -> processingState.toggleAll()
                null -> Unit
            }

            return
        }

        toggleEvent.eventToggles.forEach { eventToggle ->
            when (eventToggle.toggleType) {
                ToggleEvent.ToggleType.ENABLE -> processingState.enableEvent(eventToggle.targetEventId!!.databaseId)
                ToggleEvent.ToggleType.DISABLE -> processingState.disableEvent(eventToggle.targetEventId!!.databaseId)
                ToggleEvent.ToggleType.TOGGLE -> processingState.toggleEvent(eventToggle.targetEventId!!.databaseId)
            }
        }
    }

    /**
     * Execute the provided change counter.
     * @param changeCounter the changeCounter action to be executed.
     */
    private fun executeChangeCounter(changeCounter: ChangeCounter) {
        val oldValue = processingState.getCounterValue(changeCounter.counterName) ?: return

        val operandValue = when (val operationValue = changeCounter.operationValue) {
            is CounterOperationValue.Counter -> processingState.getCounterValue(operationValue.value) ?: 0.0
            is CounterOperationValue.Number -> operationValue.value
        }

        processingState.setCounterValue(
            counterName = changeCounter.counterName,
            value = when (changeCounter.operation) {
                ChangeCounter.OperationType.ADD -> oldValue + operandValue
                ChangeCounter.OperationType.MINUS -> oldValue - operandValue
                ChangeCounter.OperationType.SET -> operandValue
            }
        )
    }

    private fun executeNotification(event: Event, notification: Notification) {
        val counters = buildMap {
            notification.messageText.findCounterReferences().forEach { counterName ->
                processingState.getCounterValue(counterName)?.let { counterValue ->
                    put(counterName, counterValue)
                }
            }
        }

        androidExecutor.postNotification(
            ActionNotificationRequest(
                actionId = notification.id.databaseId,
                title = notification.name ?: "Klick'r",
                message = notification.messageText.replaceCounterReferences(counters),
                eventId = event.id.databaseId,
                groupName = event.name,
                importance = notification.channelImportance,
            )
        )
    }

    private suspend fun executeSystemAction(action: SystemAction) {
        val globalAction = when (action.type) {
            SystemAction.Type.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            SystemAction.Type.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            SystemAction.Type.RECENT_APPS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        }

        withContext(Dispatchers.Main) {
            androidExecutor.performGlobalAction(globalAction)
        }
    }

    private suspend fun executeSetText(action: SetText) {
        val counters = buildMap {
            action.text.findCounterReferences().forEach { counterName ->
                processingState.getCounterValue(counterName)?.let { counterValue ->
                    put(counterName, counterValue)
                }
            }
        }

        withContext(Dispatchers.Main) {
            androidExecutor.writeTextOnFocusedItem(
                text = action.text.replaceCounterReferences(counters),
                validate = action.validateInput,
            )
        }
    }

    private suspend fun executeWebhook(action: Webhook) {
        val (url, contentType, body) = when (action.mode) {
            WebhookMode.TELEGRAM_BOT -> {
                val token = action.telegramBotToken
                val chatId = action.telegramChatId
                val messageTemplate = action.telegramMessage
                if (token.isNullOrBlank() || chatId.isNullOrBlank() || messageTemplate == null) {
                    Log.w(TAG, "Webhook configuration is incomplete")
                    return
                }

                val message = messageTemplate.replaceWebhookCounterReferences()
                if (message.length > MAX_WEBHOOK_TELEGRAM_MESSAGE_LENGTH) {
                    Log.w(TAG, "Webhook Telegram message exceeds the supported length")
                    return
                }

                val encodedChatId = URLEncoder.encode(chatId, StandardCharsets.UTF_8.name())
                val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.name())
                Triple(
                    "https://api.telegram.org/bot$token/sendMessage",
                    "application/x-www-form-urlencoded; charset=UTF-8",
                    "chat_id=$encodedChatId&text=$encodedMessage".toByteArray(StandardCharsets.UTF_8),
                )
            }

            WebhookMode.CUSTOM_POST -> {
                val url = action.customUrl
                val contentType = action.customContentType
                val bodyTemplate = action.customBody
                if (url.isNullOrBlank() || contentType.isNullOrBlank() || bodyTemplate == null) {
                    Log.w(TAG, "Webhook configuration is incomplete")
                    return
                }
                Triple(
                    url,
                    contentType,
                    bodyTemplate.replaceWebhookCounterReferences().toByteArray(StandardCharsets.UTF_8),
                )
            }
        }

        try {
            val result = webhookHttpClient.post(url, contentType, body)
            if (result.statusCode !in 200..299) {
                Log.w(TAG, "Webhook request failed with HTTP status ${result.statusCode}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w(TAG, "Webhook request failed")
        }
    }

    private fun String.replaceWebhookCounterReferences(): String =
        WEBHOOK_COUNTER_REFERENCE_REGEX.replace(this) { match ->
            val counterName = match.groupValues[1]
            processingState.getCounterValue(counterName)?.toWebhookString() ?: match.value
        }

    private fun Double.toWebhookString(): String {
        val text = toString()
        return if (isFinite() && this % 1.0 == 0.0) {
            text.removeSuffix(".0").replace(".0E", "E")
        } else {
            text
        }
    }
}

/** Tag for logs. */
private const val TAG = "ActionExecutor"
/** Waiting delay after a start activity to avoid overflowing the system. */
private const val INTENT_START_ACTIVITY_DELAY = 1000L
/** Waiting delay after a broadcast to avoid overflowing the system. */
private const val INTENT_BROADCAST_DELAY = 100L
private val WEBHOOK_COUNTER_REFERENCE_REGEX = Regex("\\{([^{}]+)\\}")
