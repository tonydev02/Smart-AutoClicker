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

package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.webhook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.domain.model.action.Webhook
import com.buzbuz.smartautoclicker.core.domain.model.action.WebhookMode
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class)
class WebhookViewModel @Inject constructor(
    private val editionRepository: EditionRepository,
) : ViewModel() {

    private val configuredWebhook = editionRepository.editionState.editedActionState
        .mapNotNull { it.value }
        .filterIsInstance<Webhook>()

    private val editedActionHasChanged = editionRepository.editionState.editedActionState
        .map { it.hasChanged }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isEditingAction: Flow<Boolean> = editionRepository.isEditingAction
        .distinctUntilChanged()
        .debounce(1000)

    val uiState: StateFlow<WebhookUiState?> = combine(configuredWebhook, editedActionHasChanged) { webhook, hasChanged ->
        WebhookUiState(
            canBeSaved = webhook.isComplete(),
            hasUnsavedModifications = hasChanged,
            name = webhook.name.orEmpty(),
            mode = webhook.mode,
            telegramBotToken = webhook.telegramBotToken.orEmpty(),
            telegramChatId = webhook.telegramChatId.orEmpty(),
            telegramMessage = webhook.telegramMessage.orEmpty(),
            customUrl = webhook.customUrl.orEmpty(),
            customContentType = webhook.customContentType.orEmpty(),
            customBody = webhook.customBody.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun hasUnsavedModifications(): Boolean = uiState.value?.hasUnsavedModifications == true

    fun setName(value: String) = update { copy(name = value) }
    fun setMode(value: WebhookMode) = update { copy(mode = value) }
    fun setTelegramBotToken(value: String) = update { copy(telegramBotToken = value) }
    fun setTelegramChatId(value: String) = update { copy(telegramChatId = value) }
    fun setTelegramMessage(value: String) = update { copy(telegramMessage = value) }
    fun setCustomUrl(value: String) = update { copy(customUrl = value) }
    fun setCustomContentType(value: String) = update { copy(customContentType = value) }
    fun setCustomBody(value: String) = update { copy(customBody = value) }


    private fun update(updater: Webhook.() -> Webhook) {
        editionRepository.editionState.getEditedAction<Webhook>()?.let { webhook ->
            editionRepository.updateEditedAction(webhook.updater())
        }
    }
}
