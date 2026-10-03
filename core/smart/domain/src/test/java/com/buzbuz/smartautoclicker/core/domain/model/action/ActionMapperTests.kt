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
package com.buzbuz.smartautoclicker.core.domain.model.action

import android.os.Build
import android.graphics.Point

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.database.entity.CompleteActionEntity
import com.buzbuz.smartautoclicker.core.domain.utils.asIdentifier
import com.buzbuz.smartautoclicker.core.domain.model.action.mapper.toDomain
import com.buzbuz.smartautoclicker.core.domain.model.action.mapper.toEntity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class ActionMapperTests {

    @Test
    fun click_toEntity() {
        assertEquals(
            ActionTestsData.getNewClickEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewClick(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity(),
        )
    }

    @Test
    fun click_toDomain() {
        assertEquals(
            ActionTestsData.getNewClick(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewClickEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }

    @Test
    fun swipe_toEntity() {
        assertEquals(
            ActionTestsData.getNewSwipeEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewSwipe(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity(),
        )
    }

    @Test
    fun swipe_toDomain() {
        assertEquals(
            ActionTestsData.getNewSwipe(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewSwipeEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }

    @Test
    fun multiTouch_mapsBothStrokesRoundTrip() {
        val action = MultiTouch(
            id = 17L.asIdentifier(),
            eventId = ActionTestsData.ACTION_EVENT_ID.asIdentifier(),
            name = "Two fingers",
            priority = 3,
            firstTouch = TouchStroke(Point(10, 20), Point(30, 40), 125L),
            secondTouch = TouchStroke(Point(50, 60), Point(50, 60), 500L),
        )

        val entity = action.toEntity()
        assertEquals(action, CompleteActionEntity(entity, emptyList(), emptyList()).toDomain())
        assertEquals(10, entity.firstTouchFromX)
        assertEquals(500L, entity.secondTouchDuration)
    }

    @Test
    fun multiTouch_validatesCompletenessAndCopiesWithoutIds() {
        val validStroke = TouchStroke(Point(1, 2), Point(3, 4), 50L)
        val action = MultiTouch(
            id = 1L.asIdentifier(),
            eventId = 2L.asIdentifier(),
            name = "Two fingers",
            priority = 0,
            firstTouch = validStroke,
            secondTouch = validStroke.copy(),
        )

        assertTrue(action.isComplete())
        assertFalse(action.copy(name = "").isComplete())
        assertFalse(action.copy(firstTouch = validStroke.copy(durationMs = 60_000L)).isComplete())
        assertFalse(action.copy(secondTouch = validStroke.copy(from = null)).isComplete())
        assertFalse(action.copy(secondTouch = validStroke.copy(durationMs = 0L)).isComplete())
        assertNotSame(action.firstTouch.from, action.deepCopy().firstTouch.from)
        assertEquals(action.hashCodeNoIds(), action.copy(id = 3L.asIdentifier(), eventId = 4L.asIdentifier()).hashCodeNoIds())
        assertEquals(action, action.deepCopy())
        assertEquals(9L, action.copyBase(id = 9L.asIdentifier()).id.databaseId)
    }

    @Test
    fun pause_toEntity() {
        assertEquals(
            ActionTestsData.getNewPauseEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewPause(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity(),
        )
    }

    @Test
    fun pause_toDomain() {
        assertEquals(
            ActionTestsData.getNewPause(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewPauseEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }

    @Test
    fun intent_toEntity() {
        assertEquals(
            ActionTestsData.getNewIntentEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewIntent(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity()
        )
    }

    @Test
    fun intent_toDomain() {
        assertEquals(
            ActionTestsData.getNewIntent(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewIntentEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }

    @Test
    fun toggleEvent_toEntity() {
        assertEquals(
            ActionTestsData.getNewToggleEventEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewToggleEvent(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity()
        )
    }

    @Test
    fun toggleEvent_toDomain() {
        assertEquals(
            ActionTestsData.getNewToggleEvent(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewToggleEventEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }

    @Test
    fun changeCounter_toEntity() {
        assertEquals(
            ActionTestsData.getNewChangeCounterEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewChangeCounter(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity(),
        )
    }

    @Test
    fun changeCounter_toDomain() {
        assertEquals(
            ActionTestsData.getNewChangeCounter(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewChangeCounterEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }

    @Test
    fun notification_toEntity() {
        assertEquals(
            ActionTestsData.getNewNotificationEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewNotification(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity(),
        )
    }

    @Test
    fun notification_toDomain() {
        assertEquals(
            ActionTestsData.getNewNotification(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewNotificationEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }

    @Test
    fun systemAction_toEntity() {
        assertEquals(
            ActionTestsData.getNewSystemActionEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewSystemAction(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity(),
        )
    }

    @Test
    fun systemAction_toDomain() {
        assertEquals(
            ActionTestsData.getNewSystemAction(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewSystemActionEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }

    @Test
    fun setText_toEntity() {
        assertEquals(
            ActionTestsData.getNewSetTextEntity(eventId = ActionTestsData.ACTION_EVENT_ID).action,
            ActionTestsData.getNewSetText(eventId = ActionTestsData.ACTION_EVENT_ID).toEntity(),
        )
    }

    @Test
    fun setText_toDomain() {
        assertEquals(
            ActionTestsData.getNewSetText(eventId = ActionTestsData.ACTION_EVENT_ID),
            ActionTestsData.getNewSetTextEntity(eventId = ActionTestsData.ACTION_EVENT_ID).toDomain(),
        )
    }
}