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
import android.graphics.Rect

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
    fun multiTouch_roundTripsEveryModeAndCopiesMutableGeometry() {
        val modes = listOf(
            TouchStroke(from = Point(2, 3), durationMs = 20L, mode = TouchMode.PRESS),
            TouchStroke(from = Point(1, 2), to = Point(30, 40), durationMs = 300L, mode = TouchMode.DRAG),
            TouchStroke(durationMs = 900L, mode = TouchMode.RANDOM_AREA, area = Rect(5, 6, 80, 90)),
        )
        modes.forEach { first ->
            val action = MultiTouch(
                id = 17L.asIdentifier(),
                eventId = ActionTestsData.ACTION_EVENT_ID.asIdentifier(),
                name = "Mode ${first.mode}",
                priority = 3,
                firstTouch = first,
                secondTouch = first.deepCopy(),
            )
            assertTrue(action.isComplete())
            assertEquals(action, CompleteActionEntity(action.toEntity(), emptyList(), emptyList()).toDomain())
            val copy = action.deepCopy()
            if (first.from != null) assertNotSame(first.from, copy.firstTouch.from)
            if (first.area != null) assertNotSame(first.area, copy.firstTouch.area)
            assertEquals(action.hashCodeNoIds(), action.copy(id = 3L.asIdentifier()).hashCodeNoIds())
            assertEquals(9L, action.copyBase(id = 9L.asIdentifier()).id.databaseId)
        }
    }

    @Test
    fun multiTouch_endpointRoundTripsAndDeepCopies() {
        val endpoint = Point(120, -25)
        val area = TouchStroke(
            durationMs = 400L,
            mode = TouchMode.RANDOM_AREA,
            area = Rect(0, 0, 20, 20),
            randomAreaEnd = endpoint,
        )
        val action = MultiTouch(
            id = 11L.asIdentifier(),
            eventId = 12L.asIdentifier(),
            name = "Endpoints",
            priority = 0,
            firstTouch = area,
            secondTouch = area.copy(randomAreaEnd = Point(9, 10)),
        )
        assertTrue(area.isComplete())
        val restored = CompleteActionEntity(action.toEntity(), emptyList(), emptyList()).toDomain() as MultiTouch
        assertEquals(Point(120, -25), restored.firstTouch.randomAreaEnd)
        assertEquals(Point(9, 10), restored.secondTouch.randomAreaEnd)
        assertNotSame(endpoint, action.deepCopy().firstTouch.randomAreaEnd)
        assertEquals(
            action.copy(firstTouch = area.copy(randomAreaEnd = null)),
            CompleteActionEntity(
                action.copy(firstTouch = area.copy(randomAreaEnd = null)).toEntity(),
                emptyList(),
                emptyList(),
            ).toDomain(),
        )
    }

    @Test
    fun pause_randomRangeRoundTripsAndLegacyEntityDefaultsToFixed() {
        val pause = Pause(
            id = 1L.asIdentifier(),
            eventId = 2L.asIdentifier(),
            name = "Range",
            priority = 0,
            pauseMode = PauseMode.RANDOM_RANGE,
            randomMinDurationMs = 700L,
            randomMaxDurationMs = 1800L,
        )
        assertTrue(pause.isComplete())
        assertEquals(pause, CompleteActionEntity(pause.toEntity(), emptyList(), emptyList()).toDomain())
        val legacy = ActionTestsData.getNewPauseEntity(eventId = 2L).copy(
            action = ActionTestsData.getNewPauseEntity(eventId = 2L).action.copy(pauseMode = null),
        )
        val restored = legacy.toDomain() as Pause
        assertEquals(PauseMode.FIXED, restored.pauseMode)
        assertEquals(500L, restored.pauseDuration)
        assertFalse(pause.copy(randomMinDurationMs = null).isComplete())
        assertFalse(pause.copy(randomMaxDurationMs = null).isComplete())
        assertFalse(pause.copy(randomMinDurationMs = 0L).isComplete())
        assertFalse(pause.copy(randomMaxDurationMs = -1L).isComplete())
        assertFalse(pause.copy(randomMinDurationMs = 1801L).isComplete())
        assertTrue(pause.copy(randomMinDurationMs = 700L, randomMaxDurationMs = 700L).isComplete())

        val fixed = pause.copy(
            pauseDuration = 1_000L,
            pauseMode = PauseMode.FIXED,
            randomMinDurationMs = null,
            randomMaxDurationMs = null,
        )
        assertTrue(fixed.isComplete())
        assertFalse(fixed.copy(pauseDuration = null).isComplete())
        assertFalse(fixed.copy(pauseDuration = 0L).isComplete())
        assertFalse(pause.copy(randomMinDurationMs = -1L).isComplete())
        assertFalse(pause.copy(randomMaxDurationMs = 0L).isComplete())
    }
    @Test
    fun multiTouch_roundTripsIndependentTouchModes() {
        val press = TouchStroke(from = Point(1, 1), durationMs = 10L, mode = TouchMode.PRESS)
        val drag = TouchStroke(from = Point(2, 3), to = Point(8, 9), durationMs = 40L, mode = TouchMode.DRAG)
        val area = TouchStroke(durationMs = 50L, mode = TouchMode.RANDOM_AREA, area = Rect(0, 0, 20, 30))
        listOf(press to drag, area to press, area to area).forEach { (first, second) ->
            val action = MultiTouch(
                id = 17L.asIdentifier(),
                eventId = ActionTestsData.ACTION_EVENT_ID.asIdentifier(),
                name = "Independent modes",
                priority = 0,
                firstTouch = first,
                secondTouch = second,
            )
            assertEquals(action, CompleteActionEntity(action.toEntity(), emptyList(), emptyList()).toDomain())
        }
    }

    @Test
    fun multiTouch_rejectsModeSpecificMissingAndInvalidFields() {
        assertFalse(TouchStroke(durationMs = 10L, mode = TouchMode.PRESS).isComplete())
        assertFalse(TouchStroke(from = Point(), durationMs = 0L, mode = TouchMode.PRESS).isComplete())
        assertFalse(TouchStroke(from = Point(), durationMs = 10L, mode = TouchMode.DRAG).isComplete())
        assertFalse(TouchStroke(to = Point(), durationMs = 10L, mode = TouchMode.DRAG).isComplete())
        assertFalse(TouchStroke(durationMs = 10L, mode = TouchMode.RANDOM_AREA).isComplete())
        assertFalse(TouchStroke(durationMs = 10L, mode = TouchMode.RANDOM_AREA, area = Rect(0, 0, 0, 10)).isComplete())
    }

    @Test
    fun multiTouch_legacyEntityWithoutModeIsInterpretedAsDrag() {
        val drag = TouchStroke(Point(1, 2), Point(3, 4), 200L)
        val action = MultiTouch(
            id = 17L.asIdentifier(),
            eventId = ActionTestsData.ACTION_EVENT_ID.asIdentifier(),
            name = "Legacy",
            priority = 0,
            firstTouch = drag,
            secondTouch = drag,
        )
        val legacyEntity = action.toEntity().copy(firstTouchMode = null, secondTouchMode = null)
        val restored = CompleteActionEntity(legacyEntity, emptyList(), emptyList()).toDomain() as MultiTouch
        assertEquals(TouchMode.DRAG, restored.firstTouch.mode)
        assertEquals(TouchMode.DRAG, restored.secondTouch.mode)
        assertTrue(restored.isComplete())
    }
    @Test
    fun randomMovement_validatesCopiesAndMapsWithOptionalEndpoints() {
        val action = RandomMovement(
            id = 1L.asIdentifier(),
            eventId = 2L.asIdentifier(),
            name = "Random movement",
            priority = 0,
            area = Rect(10, 20, 40, 60),
            durationMs = 3_000L,
        )
        assertTrue(action.isComplete())
        assertTrue(action.copy(endPosition = Point(100, -5)).isComplete())
        assertFalse(action.copy(area = null).isComplete())
        assertFalse(action.copy(area = Rect(0, 0, 0, 10)).isComplete())
        assertFalse(action.copy(area = Rect(0, 0, 10, 0)).isComplete())
        assertFalse(action.copy(durationMs = null).isComplete())
        assertFalse(action.copy(durationMs = 0L).isComplete())
        assertFalse(action.copy(durationMs = 60_000L).isComplete())

        val endpoint = Point(100, -5)
        val copy = action.copy(endPosition = endpoint).deepCopy()
        assertNotSame(action.area, action.deepCopy().area)
        assertNotSame(endpoint, copy.endPosition)
        assertEquals(action.hashCodeNoIds(), action.copy(id = 8L.asIdentifier(), eventId = 9L.asIdentifier()).hashCodeNoIds())
        assertEquals(7L, action.copyBase(id = 7L.asIdentifier()).id.databaseId)

        listOf(action, action.copy(endPosition = Point(25, 30)), action.copy(endPosition = endpoint)).forEach { configured ->
            val restored = CompleteActionEntity(configured.toEntity(), emptyList(), emptyList()).toDomain()
            assertEquals(configured, restored)
        }
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