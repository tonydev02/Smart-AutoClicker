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
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.actions.GESTURE_DURATION_MAX_VALUE

/** Two screen strokes dispatched simultaneously as one accessibility gesture. */
data class MultiTouch(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String? = null,
    override var priority: Int,
    val firstTouch: TouchStroke,
    val secondTouch: TouchStroke,
) : Action() {

    override fun isComplete(): Boolean =
        super.isComplete() && firstTouch.isComplete() && secondTouch.isComplete()

    override fun hashCodeNoIds(): Int =
        name.hashCode() + firstTouch.hashCode() + secondTouch.hashCode()

    override fun deepCopy(): MultiTouch = copy(
        name = "" + name,
        firstTouch = firstTouch.deepCopy(),
        secondTouch = secondTouch.deepCopy(),
    )
}

/** The start/end coordinates and duration of one stroke in a [MultiTouch] action. */
data class TouchStroke(
    val from: Point?,
    val to: Point?,
    val durationMs: Long?,
) {
    fun isComplete(): Boolean =
        from != null && to != null && durationMs != null && durationMs in 1..GESTURE_DURATION_MAX_VALUE

    fun deepCopy(): TouchStroke = copy(
        from = from?.let(::Point),
        to = to?.let(::Point),
    )
}
