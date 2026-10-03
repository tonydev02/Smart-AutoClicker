/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.multitouch

import android.content.Context
import android.graphics.Point
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.common.actions.GESTURE_DURATION_MAX_VALUE
import com.buzbuz.smartautoclicker.core.domain.model.action.MultiTouch
import com.buzbuz.smartautoclicker.core.domain.model.action.TouchStroke
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

class MultiTouchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editionRepository: EditionRepository,
) : ViewModel() {

    private val configuredAction = editionRepository.editionState.editedActionState
        .mapNotNull { it.value }
        .filterIsInstance<MultiTouch>()

    private val editedActionHasChanged: StateFlow<Boolean> =
        editionRepository.editionState.editedActionState
            .map { it.hasChanged }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val uiState: StateFlow<MultiTouchUiState?> = combine(
        configuredAction,
        editionRepository.editionState.editedActionState,
    ) { action, actionState ->
        MultiTouchUiState(
            canBeSaved = actionState.canBeSaved,
            name = action.name,
            nameError = action.name.isNullOrEmpty(),
            firstDuration = action.firstTouch.durationMs?.toString(),
            firstDurationError = !action.firstTouch.hasValidDuration(),
            firstPositionsDescription = action.firstTouch.positionsDescription(),
            firstPositionsError = !action.firstTouch.hasPositions(),
            secondDuration = action.secondTouch.durationMs?.toString(),
            secondDurationError = !action.secondTouch.hasValidDuration(),
            secondPositionsDescription = action.secondTouch.positionsDescription(),
            secondPositionsError = !action.secondTouch.hasPositions(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun getEditedAction(): MultiTouch? = editionRepository.editionState.getEditedAction()

    fun hasUnsavedModifications(): Boolean = editedActionHasChanged.value

    fun setName(name: String) = updateAction { it.copy(name = name) }

    fun setDuration(firstTouch: Boolean, durationMs: Long?) = updateAction { action ->
        val stroke = if (firstTouch) action.firstTouch else action.secondTouch
        val updated = stroke.copy(durationMs = durationMs)
        if (firstTouch) action.copy(firstTouch = updated) else action.copy(secondTouch = updated)
    }

    fun setPositions(firstTouch: Boolean, from: Point, to: Point) = updateAction { action ->
        val stroke = if (firstTouch) action.firstTouch else action.secondTouch
        val updated = stroke.copy(from = from, to = to)
        if (firstTouch) action.copy(firstTouch = updated) else action.copy(secondTouch = updated)
    }

    private fun updateAction(update: (MultiTouch) -> MultiTouch) {
        editionRepository.editionState.getEditedAction<MultiTouch>()?.let {
            editionRepository.updateEditedAction(update(it))
        }
    }

    private fun TouchStroke.hasPositions(): Boolean = from != null && to != null

    private fun TouchStroke.hasValidDuration(): Boolean =
        durationMs != null && durationMs in 1..GESTURE_DURATION_MAX_VALUE

    private fun TouchStroke.positionsDescription(): String =
        if (hasPositions()) context.getString(
            R.string.field_multi_touch_positions_desc,
            from!!.x, from!!.y, to!!.x, to!!.y,
        ) else context.getString(R.string.generic_select_the_position)
}
