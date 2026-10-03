package com.buzbuz.smartautoclicker.core.processing.data.processor

import com.buzbuz.smartautoclicker.core.common.actions.utils.isValidRandomPauseSpread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/** Samples a hard-bounded log-normal distribution by rejection, without clipping out-of-range values. */
internal fun selectRandomPauseDuration(
    minimumMs: Long,
    mostLikelyMs: Long,
    maximumMs: Long,
    spread: Double,
    random: Random,
): Long {
    require(minimumMs > 0L && maximumMs >= minimumMs)
    require(mostLikelyMs in minimumMs..maximumMs)
    require(spread.isValidRandomPauseSpread())

    if (minimumMs == maximumMs) return minimumMs

    val sigma = spread
    val mu = ln(mostLikelyMs.toDouble()) + sigma * sigma
    val minimum = minimumMs.toDouble()
    val maximum = maximumMs.toDouble()

    repeat(MAX_SAMPLING_ATTEMPTS) {
        val firstUniform = random.nextDouble().let { if (it == 0.0) Double.MIN_VALUE else it }
        val secondUniform = random.nextDouble()
        val standardNormal = sqrt(-2.0 * ln(firstUniform)) * cos(2.0 * PI * secondUniform)
        val candidate = exp(mu + sigma * standardNormal)
        if (candidate.isFinite() && candidate >= minimum && candidate <= maximum) {
            return candidate.roundToLong().coerceIn(minimumMs, maximumMs)
        }
    }

    return mostLikelyMs.coerceIn(minimumMs, maximumMs)
}

private const val MAX_SAMPLING_ATTEMPTS = 128
