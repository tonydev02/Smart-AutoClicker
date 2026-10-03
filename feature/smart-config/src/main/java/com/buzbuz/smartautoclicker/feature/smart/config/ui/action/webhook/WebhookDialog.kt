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

import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.actions.text.appendCounterReference
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonEnabledState
import com.buzbuz.smartautoclicker.core.domain.model.action.WebhookMode
import com.buzbuz.smartautoclicker.core.domain.model.action.MAX_WEBHOOK_TELEGRAM_MESSAGE_LENGTH
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnCheckboxClickedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTextValue
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setup
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogConfigActionWebhookBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.OnActionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.counter.selection.CounterSelectionDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class WebhookDialog(
    private val listener: OnActionConfigCompleteListener,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    private val viewModel: WebhookViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { webhookViewModel() },
    )
    private lateinit var binding: DialogConfigActionWebhookBinding

    override fun onCreateView(): ViewGroup {
        binding = DialogConfigActionWebhookBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_webhook)
                buttonDismiss.setDebouncedOnClickListener { back() }
                buttonSave.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onSaveButtonClicked() }
                }
                buttonDelete.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onDeleteButtonClicked() }
                }
            }
            fieldName.apply {
                setLabel(R.string.generic_name)
                textField.filters = arrayOf(InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length)))
                setOnTextChangedListener { viewModel.setName(it.toString()) }
            }
            fieldToken.apply {
                setLabel(R.string.field_webhook_token)
                textField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setOnTextChangedListener { viewModel.setTelegramBotToken(it.toString()) }
            }
            fieldChatId.apply {
                setLabel(R.string.field_webhook_chat_id)
                setOnTextChangedListener { viewModel.setTelegramChatId(it.toString()) }
            }
            fieldTelegramMessage.setup(
                label = R.string.field_webhook_message,
                icon = R.drawable.ic_append_counter,
                disableInputWithCheckbox = false,
            )
            fieldTelegramMessage.textField.apply {
                setSingleLine(false)
                minLines = 2
                maxLines = 5
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            fieldTelegramMessage.textField.filters = arrayOf(InputFilter.LengthFilter(MAX_WEBHOOK_TELEGRAM_MESSAGE_LENGTH))
            fieldTelegramMessage.setOnTextChangedListener { viewModel.setTelegramMessage(it.toString()) }
            fieldTelegramMessage.setOnCheckboxClickedListener {
                val cursor = fieldTelegramMessage.textField.selectionEnd
                showCounterSelectionDialog { counter ->
                    val updated = fieldTelegramMessage.textField.text.toString()
                        .appendCounterReference(counter, cursor)
                    viewModel.setTelegramMessage(updated)
                    fieldTelegramMessage.setTextValue(updated, force = true)
                }
            }
            fieldUrl.apply {
                setLabel(R.string.field_webhook_url)
                setOnTextChangedListener { viewModel.setCustomUrl(it.toString()) }
            }
            fieldContentType.apply {
                setLabel(R.string.field_webhook_content_type)
                setOnTextChangedListener { viewModel.setCustomContentType(it.toString()) }
            }
            fieldBody.setup(
                label = R.string.field_webhook_body,
                icon = R.drawable.ic_append_counter,
                disableInputWithCheckbox = false,
            )
            fieldBody.textField.apply {
                setSingleLine(false)
                minLines = 3
                maxLines = 8
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            fieldBody.setOnTextChangedListener { viewModel.setCustomBody(it.toString()) }
            fieldBody.setOnCheckboxClickedListener {
                val cursor = fieldBody.textField.selectionEnd
                showCounterSelectionDialog { counter ->
                    val updated = fieldBody.textField.text.toString().appendCounterReference(counter, cursor)
                    viewModel.setCustomBody(updated)
                    fieldBody.setTextValue(updated, force = true)
                }
            }
            fieldMode.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.mode_telegram -> viewModel.setMode(WebhookMode.TELEGRAM_BOT)
                    R.id.mode_custom -> viewModel.setMode(WebhookMode.CUSTOM_POST)
                }
            }
        }
        return binding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) { viewModel.isEditingAction.collect(::onActionEditingStateChanged) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::onUiStateUpdated) }
        }
    }

    override fun back() {
        if (viewModel.hasUnsavedModifications()) {
            context.showCloseWithoutSavingDialog {
                listener.onDismissClicked()
                super.back()
            }
            return
        }
        listener.onDismissClicked()
        super.back()
    }

    private fun onUiStateUpdated(state: WebhookUiState?) {
        state ?: return
        binding.apply {
            layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, state.canBeSaved)
            fieldName.setText(state.name)
            fieldToken.setText(state.telegramBotToken)
            fieldChatId.setText(state.telegramChatId)
            fieldTelegramMessage.setTextValue(state.telegramMessage)
            fieldUrl.setText(state.customUrl)
            fieldContentType.setText(state.customContentType)
            fieldBody.setTextValue(state.customBody)
            modeTelegram.isChecked = state.mode == WebhookMode.TELEGRAM_BOT
            modeCustom.isChecked = state.mode == WebhookMode.CUSTOM_POST
            telegramFields.visibility = if (modeTelegram.isChecked) View.VISIBLE else View.GONE
            customFields.visibility = if (modeCustom.isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun onSaveButtonClicked() {
        listener.onConfirmClicked()
        super.back()
    }

    private fun onDeleteButtonClicked() {
        listener.onDeleteClicked()
        super.back()
    }

    private fun showCounterSelectionDialog(onSelected: (String) -> Unit) {
        overlayManager.navigateTo(context, CounterSelectionDialog(onSelected), hideCurrent = true)
    }

    private fun onActionEditingStateChanged(isEditingAction: Boolean) {
        if (!isEditingAction) {
            Log.e(TAG, "Closing WebhookDialog because there is no action edited")
            finish()
        }
    }

    private companion object {
        const val TAG = "WebhookDialog"
    }
}

