/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.feature.smart.config.domain

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.bitmaps.BitmapRepository
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfig
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.action.toUiAction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [android.os.Build.VERSION_CODES.Q])
class EditedItemsBuilderTests {

    @Test
    fun newFillerMovementHasRegularDefaultsAndCanBeRenderedAndEdited() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = newEditionRepository()
        val filler = repository.editedItemsBuilder.createNewFillerEvent(context)
        val movement = filler.movement

        assertNotNull(movement.name)
        assertTrue(movement.name.orEmpty().isNotEmpty())
        assertEquals(context.getString(R.string.item_random_movement_title), movement.name)
        assertFalse(movement.area?.isEmpty ?: true)
        assertEquals(Rect(476, 896, 604, 1024), movement.area)
        assertEquals(250L, movement.durationMs)
        assertTrue(movement.isComplete())

        val item = movement.toUiAction(context, filler)
        assertEquals(movement.name, item.name)
        assertFalse(item.haveError)

        repository.startEventEdition(filler)
        repository.startActionEdition(movement)
        assertTrue(repository.isEditingEvent.first())
        assertTrue(repository.isEditingAction.first())
        assertEquals(filler, repository.editionState.getEditedEvent())
        assertEquals(movement, repository.editionState.getEditedAction<RandomMovement>())
    }

    @Test
    fun missingActionNameRendersAsInvalidInsteadOfCrashing() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filler = FillerEvent(
            id = Identifier(databaseId = 10L),
            scenarioId = Identifier(databaseId = 1L),
            name = "Filler",
            movement = RandomMovement(
                id = Identifier(databaseId = 11L),
                eventId = Identifier(databaseId = 10L),
                name = null,
                priority = 0,
            ),
        )

        val item = filler.movement.toUiAction(context, filler)

        assertEquals("", item.name)
        assertTrue(item.haveError)
    }

    private suspend fun newEditionRepository(): EditionRepository {
        val scenario = Scenario(Identifier(databaseId = 1L), "Scenario", detectionQuality = 600)
        val dataSource = mockk<IRepository> {
            coEvery { getScenario(1L) } returns scenario
            coEvery { getScreenEvents(1L) } returns emptyList()
            coEvery { getTriggerEvents(1L) } returns emptyList()
            coEvery { getFillerEvents(1L) } returns emptyList()
            coEvery { getCounters(1L) } returns emptyList()
        }
        val displayConfig = DisplayConfig(
            sizePx = Point(1080, 1920),
            orientation = Configuration.ORIENTATION_PORTRAIT,
            safeInsetTopPx = 0,
            roundedCorners = emptyMap(),
        )
        val displayConfigManager = mockk<DisplayConfigManager>()
        every { displayConfigManager.displayConfig } returns displayConfig

        return EditionRepository(dataSource, mockk<BitmapRepository>(relaxed = true), displayConfigManager)
            .also { assertTrue(it.startEdition(1L)) }
    }
}
