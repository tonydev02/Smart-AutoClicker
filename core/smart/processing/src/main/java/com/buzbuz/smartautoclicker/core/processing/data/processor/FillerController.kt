/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.processing.data.processor

import com.buzbuz.smartautoclicker.core.domain.model.event.FillerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Repeats the selected filler independently of recognition frames. */
internal class FillerController(
    private val fillers: List<FillerEvent>,
    private val scope: CoroutineScope,
    private val executeMovement: suspend (FillerEvent, () -> Boolean) -> Boolean,
) {
    private val lock = Any()
    private val eventsById = fillers.associateBy { it.id.databaseId }
    private val enabledIds = fillers.filter { it.enabledOnStart }.mapTo(mutableSetOf()) { it.id.databaseId }
    private var selectedId: Long? = enabledIds.firstOrNull()
    private var generation: Long = 0L
    private var foregroundOwners: Int = 0
    private var runningJob: Job? = null
    private var started: Boolean = false
    private var stopped: Boolean = false

    init {
        // Invalid configurations from old or hand-edited backups cannot start competing loops.
        if (enabledIds.size > 1) {
            enabledIds.retainAll(setOfNotNull(selectedId))
        }
    }

    fun start() {
        synchronized(lock) {
            if (stopped || started) return
            started = true
            invalidateAndScheduleLocked()
        }
    }

    fun setEnabled(event: FillerEvent, enabled: Boolean) {
        synchronized(lock) {
            if (stopped || eventsById[event.id.databaseId] == null) return
            if (enabled) {
                enabledIds.clear()
                enabledIds += event.id.databaseId
                selectedId = event.id.databaseId
            } else {
                enabledIds -= event.id.databaseId
                if (selectedId == event.id.databaseId) selectedId = null
            }
            invalidateAndScheduleLocked()
        }
    }

    fun isEnabled(eventId: Long): Boolean = synchronized(lock) { eventId in enabledIds }

    /** Called immediately before executing an entire fulfilled foreground event sequence. */
    fun onForegroundStarted() {
        synchronized(lock) {
            foregroundOwners++
            invalidateAndScheduleLocked()
        }
    }

    /** Called only after every action in the foreground event has completed. */
    fun onForegroundFinished() {
        synchronized(lock) {
            if (foregroundOwners > 0) foregroundOwners--
            invalidateAndScheduleLocked()
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            generation++
            runningJob?.cancel()
            runningJob = null
            enabledIds.clear()
            selectedId = null
        }
    }

    private fun invalidateAndScheduleLocked() {
        generation++
        runningJob?.cancel()
        runningJob = null
        if (!started || stopped || foregroundOwners != 0) return

        val id = selectedId?.takeIf(enabledIds::contains) ?: return
        val filler = eventsById[id] ?: return
        val runGeneration = generation
        runningJob = scope.launch {
            while (isActive && isCurrent(id, runGeneration) &&
                executeMovement(filler) { isCurrent(id, runGeneration) }
            ) {
                // No recognition-loop or timer delay between completed movements.
            }
        }
    }

    private fun isCurrent(id: Long, expectedGeneration: Long): Boolean = synchronized(lock) {
        started && !stopped && foregroundOwners == 0 && selectedId == id && id in enabledIds &&
                generation == expectedGeneration
    }
}
