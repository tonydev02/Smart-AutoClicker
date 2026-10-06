/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.processing.tests

import android.graphics.Rect
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.processing.data.processor.state.EventsState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FillerBulkStateTests {

    @Test
    fun enableAllEnablesForegroundEventsAndPreservesFillerSelection() {
        val selected = filler(1, enabled = true)
        val state = newState(fillers = listOf(selected, filler(2, enabled = false)))

        state.enableAll()

        assertTrue(state.isEventEnabled(10L))
        assertTrue(state.isEventEnabled(20L))
        assertTrue(state.isEventEnabled(selected.id.databaseId))
        assertFalse(state.isEventEnabled(2L))

        val noSelection = newState(fillers = listOf(filler(1, enabled = false), filler(2, enabled = false)))
        noSelection.enableAll()
        assertTrue(noSelection.areAllFillerEventsDisabled())
    }

    @Test
    fun disableAllDisablesScreenTriggerAndActiveFiller() {
        val state = newState(fillers = listOf(filler(1, enabled = true)))

        state.disableAll()

        assertTrue(state.areAllEventsDisabled())
    }

    @Test
    fun toggleAllNeverSelectsFillerAndDisablesAnActiveOne() {
        val active = newState(fillers = listOf(filler(1, enabled = true)))
        active.toggleAll()
        assertTrue(active.areAllFillerEventsDisabled())
        assertFalse(active.isEventEnabled(10L))
        assertFalse(active.isEventEnabled(20L))

        val inactive = newState(fillers = listOf(filler(1, enabled = false), filler(2, enabled = false)))
        inactive.toggleAll()
        assertTrue(inactive.areAllFillerEventsDisabled())
        assertFalse(inactive.isEventEnabled(10L))
        assertFalse(inactive.isEventEnabled(20L))
    }

    private fun newState(fillers: List<FillerEvent>) = EventsState(
        screenEvents = listOf(screen(10L)),
        triggerEvents = listOf(trigger(20L)),
        fillerEvents = fillers,
    )

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
            area = Rect(0, 0, 10, 10),
            durationMs = 100L,
        ),
    )

    private fun screen(id: Long) = ScreenEvent(
        id = Identifier(databaseId = id),
        scenarioId = Identifier(databaseId = 100L),
        name = "Screen $id",
        conditionOperator = AND,
        priority = 0,
        keepDetecting = false,
        cooldownMs = 0L,
    )

    private fun trigger(id: Long) = TriggerEvent(
        id = Identifier(databaseId = id),
        scenarioId = Identifier(databaseId = 100L),
        name = "Trigger $id",
        conditionOperator = AND,
    )
}
