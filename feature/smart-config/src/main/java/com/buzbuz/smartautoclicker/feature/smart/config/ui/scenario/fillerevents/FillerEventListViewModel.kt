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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.fillerevents

import android.content.Context
import androidx.lifecycle.ViewModel
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class FillerEventListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val editionRepository: EditionRepository,
) : ViewModel() {

    val fillerEvents = editionRepository.editionState.editedFillerEventsState.mapNotNull { it.value }

    fun createNewEvent(): FillerEvent = editionRepository.editedItemsBuilder.createNewFillerEvent(context)
    fun startEventEdition(event: FillerEvent) = editionRepository.startEventEdition(event)
    fun saveEventEdition() = editionRepository.upsertEditedEvent()
    fun deleteEditedEvent() = editionRepository.deleteEditedEvent()
    fun dismissEditedEvent() = editionRepository.stopEventEdition()
}
