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
package com.buzbuz.smartautoclicker.core.domain.model.action

import android.graphics.Point
import android.graphics.Rect
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.actions.GESTURE_DURATION_MAX_VALUE

/** One finger moving randomly within a screen area for a configured duration. */
data class RandomMovement(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String? = null,
    override var priority: Int,
    val area: Rect? = null,
    val durationMs: Long? = null,
    val endPosition: Point? = null,
) : Action() {

    override fun isComplete(): Boolean =
        super.isComplete() && area?.isEmpty == false && durationMs?.let { it in 1..GESTURE_DURATION_MAX_VALUE } == true

    override fun hashCodeNoIds(): Int =
        name.hashCode() + area.hashCode() + durationMs.hashCode() + endPosition.hashCode()

    override fun deepCopy(): RandomMovement = copy(
        name = "" + name,
        area = area?.let(::Rect),
        endPosition = endPosition?.let(::Point),
    )
}
