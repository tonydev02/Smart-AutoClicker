/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.feature.smart.config.data.events

import android.graphics.Rect
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FillerEventsEditorTests {

    @Test
    fun enablingFillerOnStartDisablesThePreviousSelection() {
        val editor = newEditor(listOf(filler(1, enabled = true), filler(2, enabled = false)))

        editor.editAndSave(filler(2, enabled = false).copy(enabledOnStart = true))

        assertStartupSelection(editor, enabledId = 2L)
    }

    @Test
    fun disablingActiveFillerLeavesAllFillersDisabled() {
        val editor = newEditor(listOf(filler(1, enabled = false), filler(2, enabled = true)))

        editor.editAndSave(filler(2, enabled = true).copy(enabledOnStart = false))

        assertStartupSelection(editor, enabledId = null)
    }

    @Test
    fun editingUnrelatedPropertiesPreservesStartupSelection() {
        val editor = newEditor(listOf(filler(1, enabled = true), filler(2, enabled = false)))

        editor.editAndSave(filler(2, enabled = false).copy(name = "Renamed"))

        assertStartupSelection(editor, enabledId = 1L)
        assertEquals("Renamed", editor.editedList.value?.single { it.id.databaseId == 2L }?.name)
    }

    @Test
    fun savingNormalizesMalformedMultipleStartupSelections() {
        val editor = newEditor(listOf(filler(2, enabled = true), filler(1, enabled = true)))

        editor.editAndSave(filler(2, enabled = true).copy(name = "Edited"))

        assertStartupSelection(editor, enabledId = 2L)
        assertEquals(1, editor.editedList.value?.count { it.enabledOnStart })
    }

    private fun newEditor(events: List<FillerEvent>) = FillerEventsEditor(
        onDeleteEvent = {},
        parentItem = MutableStateFlow(Scenario(
            id = Identifier(databaseId = 100L),
            name = "Scenario",
            detectionQuality = 600,
        )),
    ).apply { startEdition(events) }

    private fun FillerEventsEditor.editAndSave(event: FillerEvent) {
        startItemEdition(editedList.value!!.single { it.id == event.id })
        updateEditedItem(event)
        upsertEditedItem()
    }

    private fun assertStartupSelection(editor: FillerEventsEditor, enabledId: Long?) {
        val events = editor.editedList.value.orEmpty()
        assertEquals(if (enabledId == null) 0 else 1, events.count { it.enabledOnStart })
        events.forEach { event ->
            if (event.id.databaseId == enabledId) assertTrue(event.enabledOnStart)
            else assertFalse(event.enabledOnStart)
        }
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
            area = Rect(0, 0, 10, 10),
            durationMs = 100L,
        ),
    )
}
