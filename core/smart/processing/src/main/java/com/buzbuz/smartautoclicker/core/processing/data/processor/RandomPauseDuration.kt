package com.buzbuz.smartautoclicker.core.processing.data.processor

import kotlin.random.Random

/** Selects an inclusive random pause duration without overflowing at Long.MAX_VALUE. */
internal fun selectRandomPauseDuration(minimumMs: Long, maximumMs: Long, random: Random): Long {
    require(minimumMs > 0L && maximumMs >= minimumMs)
    return if (maximumMs == Long.MAX_VALUE) {
        val width = maximumMs - minimumMs + 1L
        minimumMs + random.nextLong(width)
    } else {
        random.nextLong(minimumMs, maximumMs + 1L)
    }
}
