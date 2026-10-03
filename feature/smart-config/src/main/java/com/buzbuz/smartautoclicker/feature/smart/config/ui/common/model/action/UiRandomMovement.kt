package com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.action

import android.content.Context
import com.buzbuz.smartautoclicker.core.domain.model.action.RandomMovement
import com.buzbuz.smartautoclicker.core.ui.utils.formatDuration
import com.buzbuz.smartautoclicker.feature.smart.config.R

internal fun getRandomMovementIconRes(): Int = getSwipeIconRes()

internal fun RandomMovement.getDescription(context: Context, inError: Boolean): String {
    if (inError) return context.getString(R.string.item_error_action_invalid_generic)
    val rect = area
    val endpoint = endPosition?.let { "${it.x}, ${it.y}" }
        ?: context.getString(R.string.field_multi_touch_end_anywhere)
    return if (rect == null) context.getString(R.string.item_random_movement_details_invalid)
    else context.getString(
        R.string.item_random_movement_details,
        formatDuration(durationMs ?: 1L), rect.left, rect.top, rect.right, rect.bottom, endpoint,
    )
}
