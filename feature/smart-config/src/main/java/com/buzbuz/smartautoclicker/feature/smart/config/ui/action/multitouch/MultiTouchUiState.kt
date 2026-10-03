package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.multitouch

data class MultiTouchUiState(
    val canBeSaved: Boolean,
    val name: String?,
    val nameError: Boolean,
    val firstDuration: String?,
    val firstDurationError: Boolean,
    val firstPositionsDescription: String,
    val firstPositionsError: Boolean,
    val secondDuration: String?,
    val secondDurationError: Boolean,
    val secondPositionsDescription: String,
    val secondPositionsError: Boolean,
)
