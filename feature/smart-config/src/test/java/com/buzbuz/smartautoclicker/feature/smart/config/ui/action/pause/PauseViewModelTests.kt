package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.pause

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.action.PauseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseViewModelTests {

    @Test
    fun randomPeakInitializesAndClampsOnlyForValidRandomRanges() {
        val missingPeak = randomPause(
            minimum = Long.MAX_VALUE - 100L,
            maximum = Long.MAX_VALUE,
            peak = null,
        )
        val initialized = normalizeRandomPeak(missingPeak)
        assertEquals(Long.MAX_VALUE - 50L, initialized.randomMostLikelyDurationMs)
        assertTrue(initialized.isComplete())

        assertEquals(700L, normalizeRandomPeak(randomPause(700L, 1_800L, 400L)).randomMostLikelyDurationMs)
        assertEquals(1_800L, normalizeRandomPeak(randomPause(700L, 1_800L, 2_000L)).randomMostLikelyDurationMs)

        val missingBound = randomPause(700L, null, 900L)
        val reversedBounds = randomPause(1_800L, 700L, 1_000L)
        assertEquals(missingBound, normalizeRandomPeak(missingBound))
        assertEquals(reversedBounds, normalizeRandomPeak(reversedBounds))
        assertEquals(
            randomPause(700L, 1_800L, 400L).copy(pauseMode = PauseMode.FIXED),
            normalizeRandomPeak(randomPause(700L, 1_800L, 400L).copy(pauseMode = PauseMode.FIXED)),
        )
    }

    @Test
    fun normalizedSliderMapsDurationsWithoutUsingDisplayUnits() {
        val minimum = Long.MAX_VALUE - 100L
        val maximum = Long.MAX_VALUE
        assertEquals(0, durationToPosition(minimum, minimum, maximum))
        assertEquals(5_000, durationToPosition(minimum + 50L, minimum, maximum))
        assertEquals(10_000, durationToPosition(maximum, minimum, maximum))
        assertEquals(minimum + 50L, positionToDuration(5_000, minimum, maximum))
        assertEquals(minimum, positionToDuration(-1, minimum, maximum))
        assertEquals(maximum, positionToDuration(10_001, minimum, maximum))
        assertEquals(0, durationToPosition(750L, 700L, 700L))
        assertEquals(700L, positionToDuration(10_000, 700L, 700L))
    }

    private fun randomPause(minimum: Long?, maximum: Long?, peak: Long?) = Pause(
        id = Identifier(databaseId = 1L),
        eventId = Identifier(databaseId = 2L),
        name = "Pause",
        priority = 0,
        pauseMode = PauseMode.RANDOM_RANGE,
        randomMinDurationMs = minimum,
        randomMaxDurationMs = maximum,
        randomMostLikelyDurationMs = peak,
    )
}
