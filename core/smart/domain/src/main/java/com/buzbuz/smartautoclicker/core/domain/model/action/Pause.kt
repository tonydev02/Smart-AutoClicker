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
package com.buzbuz.smartautoclicker.core.domain.model.action

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier

/**
 * Pause action.
 *
 * [PauseMode.FIXED] uses [pauseDuration] with the legacy scenario-randomization behavior.
 * [PauseMode.RANDOM_RANGE] selects a fresh duration from [randomMinDurationMs] through
 * [randomMaxDurationMs], inclusive.
 */
data class Pause(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String? = null,
    override var priority: Int,
    val pauseDuration: Long? = null,
    val pauseMode: PauseMode = PauseMode.FIXED,
    val randomMinDurationMs: Long? = null,
    val randomMaxDurationMs: Long? = null,
) : Action() {

    override fun isComplete(): Boolean = super.isComplete() && when (pauseMode) {
        PauseMode.FIXED -> pauseDuration != null && pauseDuration > 0L
        PauseMode.RANDOM_RANGE ->
            randomMinDurationMs != null && randomMinDurationMs > 0L &&
                randomMaxDurationMs != null && randomMaxDurationMs > 0L &&
                randomMinDurationMs <= randomMaxDurationMs
    }

    override fun hashCodeNoIds(): Int =
        name.hashCode() + pauseDuration.hashCode() + pauseMode.hashCode() +
            randomMinDurationMs.hashCode() + randomMaxDurationMs.hashCode()

    override fun deepCopy(): Pause = copy(name = "" + name)
}

enum class PauseMode {
    FIXED,
    RANDOM_RANGE,
}