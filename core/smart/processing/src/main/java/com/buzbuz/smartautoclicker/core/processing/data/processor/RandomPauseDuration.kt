package com.buzbuz.smartautoclicker.core.processing.data.processor

import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/** Samples an inclusive duration from a triangular distribution without overflowing. */
internal fun selectRandomPauseDuration(
    minimumMs: Long,
    mostLikelyMs: Long,
    maximumMs: Long,
    random: Random,
): Long {
    require(minimumMs > 0L && maximumMs >= minimumMs)
    require(mostLikelyMs in minimumMs..maximumMs)

    val spanMs = maximumMs - minimumMs
    if (spanMs == 0L) return minimumMs

    val span = spanMs.toDouble()
    val modeOffset = (mostLikelyMs - minimumMs).toDouble()
    val split = modeOffset / span
    val sample = random.nextDouble()
    val offset = if (sample < split) {
        sqrt(sample * span * modeOffset)
    } else {
        span - sqrt((1.0 - sample) * span * (span - modeOffset))
    }

    return minimumMs + offset.roundToLong().coerceIn(0L, spanMs)
}
