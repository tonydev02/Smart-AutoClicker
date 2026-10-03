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

import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.core.graphics.toPoint
import androidx.core.graphics.toPointF
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.actions.GESTURE_DURATION_MAX_VALUE
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.PositionSelectorMenu
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonEnabledState
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setError
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnClickListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.utils.MinMaxInputFilter
import com.buzbuz.smartautoclicker.core.ui.views.itembrief.renderers.SwipeDescription
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogConfigActionMultiTouchBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.OnActionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import com.buzbuz.smartautoclicker.core.common.tutorial.domain.model.monitoring.MonitoredOverlayType

class MultiTouchDialog(
    private val listener: OnActionConfigCompleteListener,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    override fun tutorialMonitoringTag(): String = MonitoredOverlayType.MULTI_TOUCH.name

    private val viewModel: MultiTouchViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { multiTouchViewModel() },
    )

    private lateinit var viewBinding: DialogConfigActionMultiTouchBinding

    override fun onCreateView(): ViewGroup {
        viewBinding = DialogConfigActionMultiTouchBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_multi_touch)
                buttonDismiss.setDebouncedOnClickListener { back() }
                buttonSave.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onSave() }
                }
                buttonDelete.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onDelete() }
                }
            }
            fieldName.apply {
                setLabel(R.string.generic_name)
                setOnTextChangedListener { viewModel.setName(it.toString()) }
                textField.filters = arrayOf(InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length)))
            }
            setupDurationField(fieldFirstDuration, true)
            setupDurationField(fieldSecondDuration, false)
            fieldFirstPositions.apply {
                setTitle(context.getString(R.string.field_multi_touch_positions_title, 1))
                setOnClickListener { debounceUserInteraction { showPositionSelector(true) } }
            }
            fieldSecondPositions.apply {
                setTitle(context.getString(R.string.field_multi_touch_positions_title, 2))
                setOnClickListener { debounceUserInteraction { showPositionSelector(false) } }
            }
        }
        hideSoftInputOnFocusLoss(viewBinding.fieldName.textField)
        hideSoftInputOnFocusLoss(viewBinding.fieldFirstDuration.textField)
        hideSoftInputOnFocusLoss(viewBinding.fieldSecondDuration.textField)
        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::updateUi) }
            }
        }
    }

    private fun onSave() {
        listener.onConfirmClicked()
        super.back()
    }

    private fun onDelete() {
        listener.onDeleteClicked()
        super.back()
    }

    override fun back() {
        if (viewModel.hasUnsavedModifications()) {
            context.showCloseWithoutSavingDialog {
                listener.onDismissClicked()
                super.back()
            }
        } else {
            listener.onDismissClicked()
            super.back()
        }
    }

    private fun setupDurationField(field: com.buzbuz.smartautoclicker.core.ui.databinding.IncludeFieldTextInputBinding, firstTouch: Boolean) {
        field.apply {
            textField.filters = arrayOf(MinMaxInputFilter(1, GESTURE_DURATION_MAX_VALUE.toInt()))
            setLabel(if (firstTouch) R.string.field_multi_touch_first_duration else R.string.field_multi_touch_second_duration)
            setOnTextChangedListener {
                viewModel.setDuration(firstTouch, it.toString().takeIf { text -> text.isNotEmpty() }?.toLong())
            }
        }
    }

    private fun updateUi(state: MultiTouchUiState?) {
        state ?: return
        viewBinding.apply {
            layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, state.canBeSaved)
            fieldName.setText(state.name)
            fieldName.setError(state.nameError)
            fieldFirstDuration.setText(state.firstDuration, InputType.TYPE_CLASS_NUMBER)
            fieldFirstDuration.setError(state.firstDurationError)
            fieldFirstPositions.setDescription(state.firstPositionsDescription)
            fieldFirstPositions.setError(state.firstPositionsError)
            fieldSecondDuration.setText(state.secondDuration, InputType.TYPE_CLASS_NUMBER)
            fieldSecondDuration.setError(state.secondDurationError)
            fieldSecondPositions.setDescription(state.secondPositionsDescription)
            fieldSecondPositions.setError(state.secondPositionsError)
        }
    }

    private fun showPositionSelector(firstTouch: Boolean) {
        val stroke = viewModel.getEditedAction()?.let { if (firstTouch) it.firstTouch else it.secondTouch } ?: return
        overlayManager.navigateTo(
            context = context,
            newOverlay = PositionSelectorMenu(
                tutorialMonitoringTag = MonitoredOverlayType.MULTI_TOUCH_POSITION.name,
                itemBriefDescription = SwipeDescription(
                    from = stroke.from?.toPointF(),
                    to = stroke.to?.toPointF(),
                    swipeDurationMs = stroke.durationMs ?: 250L,
                ),
                onConfirm = { description ->
                    (description as SwipeDescription).let { selected ->
                        viewModel.setPositions(firstTouch, selected.from!!.toPoint(), selected.to!!.toPoint())
                    }
                },
            ),
            hideCurrent = true,
        )
    }

}
