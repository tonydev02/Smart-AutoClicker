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
import android.graphics.Rect
import android.graphics.Point
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.common.actions.GESTURE_DURATION_MAX_VALUE
import com.buzbuz.smartautoclicker.core.domain.model.action.MultiTouch
import com.buzbuz.smartautoclicker.core.domain.model.action.TouchStroke
import com.buzbuz.smartautoclicker.core.domain.model.action.TouchMode
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
            firstMode = action.firstTouch.mode.ordinal,
            firstDuration = action.firstTouch.durationMs?.toString(),
            firstDurationError = !action.firstTouch.hasValidDuration(),
            firstPositionsDescription = action.firstTouch.positionsDescription(),
            firstPositionsError = !action.firstTouch.hasSelection(),
            firstAreaMode = action.firstTouch.mode == TouchMode.RANDOM_AREA,
            firstEndSpecific = action.firstTouch.randomAreaEnd != null,
            firstEndDescription = action.firstTouch.endPositionDescription(),
            secondMode = action.secondTouch.mode.ordinal,
            secondDuration = action.secondTouch.durationMs?.toString(),
            secondDurationError = !action.secondTouch.hasValidDuration(),
            secondPositionsDescription = action.secondTouch.positionsDescription(),
            secondPositionsError = !action.secondTouch.hasSelection(),
            secondAreaMode = action.secondTouch.mode == TouchMode.RANDOM_AREA,
            secondEndSpecific = action.secondTouch.randomAreaEnd != null,
            secondEndDescription = action.secondTouch.endPositionDescription(),
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

    fun setPositions(firstTouch: Boolean, from: Point, to: Point) = updateStroke(firstTouch) { stroke ->
        if (stroke.mode == TouchMode.PRESS) stroke.copy(from = from, to = null)
        else stroke.copy(from = from, to = to)
    }

    fun setArea(firstTouch: Boolean, area: Rect) = updateStroke(firstTouch) { it.copy(area = Rect(area)) }

    fun setMode(firstTouch: Boolean, mode: TouchMode) = updateStroke(firstTouch) { stroke ->
        when (mode) {
            TouchMode.PRESS -> stroke.copy(mode = mode, to = null, area = null, randomAreaEnd = null)
            TouchMode.DRAG -> stroke.copy(mode = mode, area = null, randomAreaEnd = null)
            TouchMode.RANDOM_AREA -> stroke.copy(mode = mode, from = null, to = null)
        }
    }

    fun setRandomAreaEnd(firstTouch: Boolean, end: Point?) = updateStroke(firstTouch) {
        it.copy(randomAreaEnd = end)
    }

    private fun updateStroke(firstTouch: Boolean, update: (TouchStroke) -> TouchStroke) =
        updateAction { action ->
            if (firstTouch) action.copy(firstTouch = update(action.firstTouch))
            else action.copy(secondTouch = update(action.secondTouch))
        }
    private fun updateAction(update: (MultiTouch) -> MultiTouch) {
        editionRepository.editionState.getEditedAction<MultiTouch>()?.let {
            editionRepository.updateEditedAction(update(it))
        }
    }

    private fun TouchStroke.hasSelection(): Boolean = when (mode) {
        TouchMode.PRESS -> from != null
        TouchMode.DRAG -> from != null && to != null
        TouchMode.RANDOM_AREA -> area?.isEmpty == false
    }

    private fun TouchStroke.hasValidDuration(): Boolean =
        durationMs != null && durationMs in 1..GESTURE_DURATION_MAX_VALUE

    private fun TouchStroke.positionsDescription(): String = when (mode) {
        TouchMode.PRESS -> from?.let { context.getString(R.string.field_multi_touch_positions_desc, it.x, it.y, it.x, it.y) }
            ?: context.getString(R.string.generic_select_the_position)
        TouchMode.DRAG -> {
            val start = from
            val end = to
            if (start != null && end != null) context.getString(
                R.string.field_multi_touch_positions_desc, start.x, start.y, end.x, end.y,
            ) else context.getString(R.string.generic_select_the_position)
        }
        TouchMode.RANDOM_AREA -> area?.toString() ?: context.getString(R.string.generic_select_the_position)
    }

    private fun TouchStroke.endPositionDescription(): String =
        randomAreaEnd?.let { context.getString(R.string.field_multi_touch_positions_desc, it.x, it.y, it.x, it.y) }
            ?: context.getString(R.string.generic_select_the_position)
}
