package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.randommovement

import androidx.core.graphics.toPoint
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.graphics.toPointF
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.actions.GESTURE_DURATION_MAX_VALUE
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.PositionSelectorMenu
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
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
import com.buzbuz.smartautoclicker.core.ui.views.itembrief.renderers.ClickDescription
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogConfigActionRandomMovementBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.OnActionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.multitouch.RandomAreaSelectorMenu
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class RandomMovementDialog(private val listener: OnActionConfigCompleteListener) : OverlayDialog(R.style.ScenarioConfigTheme) {
    private val viewModel: RandomMovementViewModel by viewModels(entryPoint = ScenarioConfigViewModelsEntryPoint::class.java, creator = { randomMovementViewModel() })
    private lateinit var binding: DialogConfigActionRandomMovementBinding
    override fun onCreateView(): ViewGroup {
        binding = DialogConfigActionRandomMovementBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.item_random_movement_title)
                buttonDismiss.setDebouncedOnClickListener { back() }
                buttonSave.apply { visibility = View.VISIBLE; setDebouncedOnClickListener { listener.onConfirmClicked(); superBack() } }
                buttonDelete.apply { visibility = View.VISIBLE; setDebouncedOnClickListener { listener.onDeleteClicked(); superBack() } }
            }
            fieldName.apply {
                setLabel(R.string.generic_name)
                setOnTextChangedListener { viewModel.setName(it.toString()) }
                textField.filters = arrayOf(InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length)))
            }
            fieldDuration.apply {
                setLabel(R.string.field_random_movement_duration)
                textField.filters = arrayOf(MinMaxInputFilter(1, GESTURE_DURATION_MAX_VALUE.toInt()))
                setOnTextChangedListener { viewModel.setDuration(it.toString().takeIf(String::isNotEmpty)?.toLong()) }
            }
            fieldArea.apply { setTitle(context.getString(R.string.field_random_movement_area)); setOnClickListener { showAreaSelector() } }
            fieldEndMode.adapter = ArrayAdapter.createFromResource(context, R.array.multi_touch_end_modes, android.R.layout.simple_spinner_item).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            fieldEndMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val action = viewModel.action() ?: return
                    val specific = position == 1
                    if (specific != (action.endPosition != null)) {
                        if (specific) showEndSelector() else viewModel.setEnd(null)
                    }
                }
            }
            fieldEndPosition.apply { setTitle(context.getString(R.string.field_random_movement_end)); setOnClickListener { showEndSelector() } }
        }
        return binding.root
    }
    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::updateUi) } }
    }
    private fun updateUi(state: RandomMovementUiState?) {
        state ?: return
        binding.layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, state.canBeSaved)
        binding.fieldName.setText(state.name); binding.fieldDuration.setText(state.duration, InputType.TYPE_CLASS_NUMBER)
        binding.fieldArea.setDescription(state.area); binding.fieldArea.setError(!state.areaValid)
        binding.fieldEndMode.setSelection(if (state.specificEnd) 1 else 0)
        binding.fieldEndPosition.root.visibility = if (state.specificEnd) View.VISIBLE else View.GONE
        binding.fieldEndPosition.setDescription(state.endDescription)
    }
    private fun showAreaSelector() {
        overlayManager.navigateTo(context, RandomAreaSelectorMenu(viewModel.action()?.area) { viewModel.setArea(it) }, hideCurrent = true)
    }
    private fun showEndSelector() {
        val action = viewModel.action() ?: return
        overlayManager.navigateTo(context, PositionSelectorMenu(
            tutorialMonitoringTag = "RANDOM_MOVEMENT_POSITION",
            itemBriefDescription = ClickDescription(position = action.endPosition?.toPointF(), pressDurationMs = action.durationMs ?: 1L),
            onConfirm = { selected -> (selected as ClickDescription).position?.let { viewModel.setEnd(it.toPoint()) } },
        ), hideCurrent = true)
    }
    private fun superBack() { super.back() }
    override fun back() {
        if (viewModel.hasUnsavedModifications()) context.showCloseWithoutSavingDialog { listener.onDismissClicked(); super.back() }
        else { listener.onDismissClicked(); super.back() }
    }
}
