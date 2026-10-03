/*
 * Copyright (C) 2025 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.domain.model.action.mapper

import com.buzbuz.smartautoclicker.core.database.entity.ActionEntity
import com.buzbuz.smartautoclicker.core.database.entity.ActionType
import com.buzbuz.smartautoclicker.core.database.entity.CounterOperationValueType
import com.buzbuz.smartautoclicker.core.domain.model.counter.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.ChangeCounter
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.action.Intent
import com.buzbuz.smartautoclicker.core.domain.model.action.Notification
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.action.SetText
import com.buzbuz.smartautoclicker.core.domain.model.action.Swipe
import com.buzbuz.smartautoclicker.core.domain.model.action.SystemAction
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.action.Webhook
import com.buzbuz.smartautoclicker.core.domain.model.action.MultiTouch
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.domain.model.action.PauseMode
import android.graphics.Rect
import com.buzbuz.smartautoclicker.core.domain.model.action.TouchMode


internal fun Action.toEntity(): ActionEntity {
    if (!isComplete()) throw IllegalStateException("Can't transform to entity, action is incomplete: $this")

    return when (this) {
        is Click -> toClickEntity()
        is MultiTouch -> toMultiTouchEntity()
        is RandomMovement -> toRandomMovementEntity()
        is Swipe -> toSwipeEntity()
        is Pause -> toPauseEntity()
        is Intent -> toIntentEntity()
        is ToggleEvent -> toToggleEventEntity()
        is ChangeCounter -> toChangeCounterEntity()
        is Notification -> toNotificationEntity()
        is SystemAction -> toSystemActionEntity()
        is Webhook -> toWebhookEntity()
        is SetText -> toSetTextEntity()
    }
}

private fun Click.toClickEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.CLICK,
        pressDuration = pressDuration,
        clickPositionType = positionType.toEntity(),
        x = position?.x,
        y = position?.y,
        clickOnConditionId = clickOnConditionId?.databaseId,
        clickOffsetX = clickOffset?.x,
        clickOffsetY = clickOffset?.y,
    )

private fun Swipe.toSwipeEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.SWIPE,
        swipeDuration = swipeDuration,
        fromX = from?.x,
        fromY = from?.y,
        toX = to?.x,
        toY = to?.y,
    )


private fun RandomMovement.toRandomMovementEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.RANDOM_MOVEMENT,
        randomAreaLeft = area!!.left,
        randomAreaTop = area.top,
        randomAreaRight = area.right,
        randomAreaBottom = area.bottom,
        randomAreaDuration = durationMs,
        randomAreaEndX = endPosition?.x,
        randomAreaEndY = endPosition?.y,
    )
private fun MultiTouch.toMultiTouchEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.MULTI_TOUCH,
        firstTouchFromX = firstTouch.from?.x,
        firstTouchFromY = firstTouch.from?.y,
        firstTouchToX = firstTouch.to?.x,
        firstTouchToY = firstTouch.to?.y,
        firstTouchDuration = firstTouch.durationMs,
        firstTouchMode = firstTouch.mode.name,
        firstTouchAreaLeft = firstTouch.area?.left,
        firstTouchAreaTop = firstTouch.area?.top,
        firstTouchAreaRight = firstTouch.area?.right,
        firstTouchAreaBottom = firstTouch.area?.bottom,
        secondTouchFromX = secondTouch.from?.x,
        secondTouchFromY = secondTouch.from?.y,
        secondTouchToX = secondTouch.to?.x,
        secondTouchToY = secondTouch.to?.y,
        secondTouchDuration = secondTouch.durationMs,
        secondTouchMode = secondTouch.mode.name,
        secondTouchAreaLeft = secondTouch.area?.left,
        secondTouchAreaTop = secondTouch.area?.top,
        secondTouchAreaRight = secondTouch.area?.right,
        secondTouchAreaBottom = secondTouch.area?.bottom,
        firstTouchRandomEndX = firstTouch.randomAreaEnd?.x,
        firstTouchRandomEndY = firstTouch.randomAreaEnd?.y,
        secondTouchRandomEndX = secondTouch.randomAreaEnd?.x,
        secondTouchRandomEndY = secondTouch.randomAreaEnd?.y,
    )

private fun Pause.toPauseEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.PAUSE,
        pauseDuration = pauseDuration,
        pauseMode = pauseMode.name,
        pauseRandomMinDuration = randomMinDurationMs,
        pauseRandomMaxDuration = randomMaxDurationMs,
        pauseRandomMostLikelyDuration = randomMostLikelyDurationMs,
        pauseRandomSpread = randomSpread,
    )

private fun Intent.toIntentEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.INTENT,
        isAdvanced = isAdvanced,
        isBroadcast = isBroadcast,
        intentAction = intentAction,
        componentName = componentName?.flattenToString(),
        flags = flags,
    )

private fun ToggleEvent.toToggleEventEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.TOGGLE_EVENT,
        toggleAllType = toggleAllType?.toEntity(),
        toggleAll = toggleAll,
    )

private fun ChangeCounter.toChangeCounterEntity(): ActionEntity {
    val isNumberValue = operationValue is CounterOperationValue.Number

    return ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.CHANGE_COUNTER,
        counterName = counterName,
        counterOperation = operation.toEntity(),
        counterOperationValueType = if (isNumberValue) CounterOperationValueType.NUMBER else CounterOperationValueType.COUNTER,
        counterOperationValue = if (isNumberValue) operationValue.value else null,
        counterOperationCounterName = if (isNumberValue) null else operationValue.value as String,
    )
}

private fun Notification.toNotificationEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.NOTIFICATION,
        notificationImportance = channelImportance,
        notificationMessageText = messageText,
    )

private fun Webhook.toWebhookEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.WEBHOOK,
        webhookMode = mode.name,
        webhookTelegramBotToken = telegramBotToken,
        webhookTelegramChatId = telegramChatId,
        webhookTelegramMessage = telegramMessage,
        webhookCustomUrl = customUrl,
        webhookCustomContentType = customContentType,
        webhookCustomBody = customBody,
    )

private fun SystemAction.toSystemActionEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.SYSTEM,
        systemActionType = type.toEntity(),
    )

private fun SetText.toSetTextEntity(): ActionEntity =
    ActionEntity(
        id = id.databaseId,
        eventId = eventId.databaseId,
        priority = priority,
        name = name!!,
        type = ActionType.TEXT,
        textValue = text,
        textValidateInput = validateInput,
    )
