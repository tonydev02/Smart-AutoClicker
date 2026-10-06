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
package com.buzbuz.smartautoclicker.feature.smart.config.data.events

import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import kotlinx.coroutines.flow.StateFlow

/** Editor for filler events, whose sole action is their random movement. */
internal class FillerEventsEditor(
    onDeleteEvent: (FillerEvent) -> Unit,
    parentItem: StateFlow<Scenario?>,
) : EventsEditor<FillerEvent, Condition>(onDeleteEvent, canBeEmpty = true, parentItem) {

    override fun onEditedEventConditionsUpdated(conditions: List<Condition>) = Unit

    override fun copyEventWithNewChildren(
        event: FillerEvent,
        conditions: List<Condition>,
        actions: List<Action>,
    ): FillerEvent = event.copy(
        movement = actions.singleOrNull() as? RandomMovement ?: event.movement,
    )

    override fun upsertEditedItem() {
        val editedFiller = editedItem.value ?: return
        super.upsertEditedItem()

        val events = editedList.value ?: return
        val enabledFillerId = if (editedFiller.enabledOnStart) {
            editedFiller.id
        } else {
            events.asSequence()
                .filter { it.enabledOnStart }
                .minByOrNull { it.id.databaseId }
                ?.id
        }

        updateList(events.map { event ->
            event.copy(enabledOnStart = event.id == enabledFillerId)
        })
    }
}
