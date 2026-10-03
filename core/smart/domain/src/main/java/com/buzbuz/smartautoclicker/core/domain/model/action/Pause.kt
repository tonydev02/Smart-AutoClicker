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
import com.buzbuz.smartautoclicker.core.common.actions.utils.isValidRandomPauseSpread

/**
 * Pause action.
 *
 * [PauseMode.RANDOM_RANGE] samples a bounded log-normal duration between [randomMinDurationMs] and
 * [randomMaxDurationMs], with its mode at [randomMostLikelyDurationMs] and shape controlled by [randomSpread].
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
    val randomMostLikelyDurationMs: Long? = null,
    val randomSpread: Double? = null,
) : Action() {

    override fun isComplete(): Boolean = super.isComplete() && when (pauseMode) {
        PauseMode.FIXED -> pauseDuration != null && pauseDuration > 0L
        PauseMode.RANDOM_RANGE -> {
            val min = randomMinDurationMs
            val max = randomMaxDurationMs
            val mostLikely = randomMostLikelyDurationMs
            val spread = randomSpread
            min != null && min > 0L &&
                max != null && max >= min &&
                mostLikely != null && mostLikely in min..max &&
                spread != null && spread.isValidRandomPauseSpread()
        }
    }

    override fun hashCodeNoIds(): Int =
        name.hashCode() + pauseDuration.hashCode() + pauseMode.hashCode() +
            randomMinDurationMs.hashCode() + randomMaxDurationMs.hashCode() +
            randomMostLikelyDurationMs.hashCode() + randomSpread.hashCode()

    override fun deepCopy(): Pause = copy(name = "" + name)
}

enum class PauseMode {
    FIXED,
    RANDOM_RANGE,
}