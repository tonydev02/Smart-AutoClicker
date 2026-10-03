package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.randommovement

import android.graphics.Point
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class RandomMovementViewModel @Inject constructor(private val editionRepository: EditionRepository) : ViewModel() {
    private val editedActionState = editionRepository.editionState.editedActionState
    private val configuredAction = editedActionState.map { it.value }.filterIsInstance<RandomMovement>()
    private val editedActionHasChanged: StateFlow<Boolean> = editedActionState
        .map { it.hasChanged }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val uiState = combine(configuredAction, editedActionState) { action, actionState ->
        RandomMovementUiState(
            canBeSaved = actionState.canBeSaved,
            name = action.name,
            duration = action.durationMs?.toString(),
            area = action.area?.let { "${it.left}, ${it.top} - ${it.right}, ${it.bottom}" },
            areaValid = action.area?.isEmpty == false,
            specificEnd = action.endPosition != null,
            endDescription = action.endPosition?.let { "${it.x}, ${it.y}" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun action() = editionRepository.editionState.getEditedAction<RandomMovement>()
    fun setName(value: String) = update { it.copy(name = value) }
    fun setEnd(value: Point?) = update { it.copy(endPosition = value?.let(::Point)) }
    fun setDuration(value: Long?) = update { it.copy(durationMs = value) }
    fun setArea(value: Rect) = update { it.copy(area = Rect(value)) }
    private fun update(transform: (RandomMovement) -> RandomMovement) {
        action()?.let { editionRepository.updateEditedAction(transform(it)) }
    }
    fun hasUnsavedModifications() = editedActionHasChanged.value
}

data class RandomMovementUiState(
    val canBeSaved: Boolean, val name: String?, val duration: String?, val area: String?,
    val areaValid: Boolean, val specificEnd: Boolean, val endDescription: String?,
)

