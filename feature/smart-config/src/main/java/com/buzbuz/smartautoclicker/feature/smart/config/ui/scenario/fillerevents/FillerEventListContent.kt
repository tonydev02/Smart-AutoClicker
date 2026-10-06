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
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.navbar.NavBarDialogContent
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.navbar.viewModels
import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.setEmptyText
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.updateState
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeLoadableListBinding
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemFillerEventBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.EventDialog
import kotlinx.coroutines.launch

class FillerEventListContent(appContext: Context) : NavBarDialogContent(appContext) {

    private val viewModel: FillerEventListViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { fillerEventListViewModel() },
    )
    private lateinit var binding: IncludeLoadableListBinding
    private lateinit var adapter: FillerEventListAdapter

    override fun floatingActionButtonsAreAvailable(): Boolean = true

    override fun onCreateView(container: ViewGroup): ViewGroup {
        adapter = FillerEventListAdapter(::onEventClicked)
        binding = IncludeLoadableListBinding.inflate(LayoutInflater.from(context), container, false).apply {
            setEmptyText(R.string.message_empty_filler_event_list_title, R.string.message_empty_filler_event_list_desc)
            list.apply {
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                this.adapter = this@FillerEventListContent.adapter
            }
        }
        return binding.root
    }

    override fun onViewCreated() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fillerEvents.collect { events ->
                    binding.updateState(events)
                    adapter.submitList(events)
                }
            }
        }
    }

    override fun onPrimaryFloatingActionButtonClicked() {
        debounceUserInteraction { showEventConfigDialog(viewModel.createNewEvent()) }
    }

    private fun onEventClicked(event: FillerEvent) {
        debounceUserInteraction { showEventConfigDialog(event) }
    }

    private fun showEventConfigDialog(event: FillerEvent) {
        viewModel.startEventEdition(event)
        dialogController.overlayManager.navigateTo(
            context = context,
            newOverlay = EventDialog(
                onConfigComplete = viewModel::saveEventEdition,
                onDelete = viewModel::deleteEditedEvent,
                onDismiss = viewModel::dismissEditedEvent,
            ),
            hideCurrent = true,
        )
    }
}

private class FillerEventListAdapter(
    private val itemClickedListener: (FillerEvent) -> Unit,
) : ListAdapter<FillerEvent, FillerEventViewHolder>(FillerEventDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FillerEventViewHolder =
        FillerEventViewHolder(ItemFillerEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FillerEventViewHolder, position: Int) =
        holder.bind(getItem(position), itemClickedListener)
}

private class FillerEventViewHolder(
    private val binding: ItemFillerEventBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(event: FillerEvent, itemClickedListener: (FillerEvent) -> Unit) {
        binding.textName.text = event.name
        binding.textMovementState.setText(
            if (event.movement.isComplete()) R.string.item_filler_movement_configured
            else R.string.item_filler_movement_needs_configuration,
        )
        binding.root.setOnClickListener { itemClickedListener(event) }
    }
}

private object FillerEventDiffCallback : DiffUtil.ItemCallback<FillerEvent>() {
    override fun areItemsTheSame(oldItem: FillerEvent, newItem: FillerEvent): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: FillerEvent, newItem: FillerEvent): Boolean = oldItem == newItem
}
