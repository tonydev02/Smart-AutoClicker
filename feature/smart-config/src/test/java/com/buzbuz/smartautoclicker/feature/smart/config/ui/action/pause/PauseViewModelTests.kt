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
        val initialized = normalizeRandomSpread(normalizeRandomPeak(missingPeak))
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

    @Test
    fun spreadSliderDefaultsMapsEndpointsAndDoesNotChangePauseDurationsOrPeak() {
        val pause = randomPause(2_000L, 30_000L, 5_000L)
        val initialized = normalizeRandomSpread(pause)

        assertEquals(0.60, initialized.randomSpread!!, 0.0)
        assertEquals(409, spreadToPosition(initialized.randomSpread))
        assertEquals(0.15, positionToSpread(0), 0.0)
        assertEquals(1.25, positionToSpread(1_000), 0.0)
        assertEquals(0.15, positionToSpread(-1), 0.0)
        assertEquals(1.25, positionToSpread(1_001), 0.0)
        assertEquals(2_000L, initialized.randomMinDurationMs)
        assertEquals(30_000L, initialized.randomMaxDurationMs)
        assertEquals(5_000L, initialized.randomMostLikelyDurationMs)
        assertEquals(2_000L, initialized.copy(randomSpread = positionToSpread(900)).randomMinDurationMs)
        assertEquals(30_000L, initialized.copy(randomSpread = positionToSpread(900)).randomMaxDurationMs)
        assertEquals(5_000L, initialized.copy(randomSpread = positionToSpread(900)).randomMostLikelyDurationMs)
    }

    @Test
    fun spreadNormalizationDefaultsIncompleteRangeAndLeavesFixedModeAlone() {
        val fixed = randomPause(700L, 1_800L, 1_000L).copy(
            pauseMode = PauseMode.FIXED,
            randomSpread = null,
        )
        val missingBounds = randomPause(700L, null, 1_000L).copy(randomSpread = null)
        val invalidSpread = randomPause(700L, 1_800L, 1_000L).copy(randomSpread = 1.5)

        assertEquals(fixed, normalizeRandomSpread(fixed))
        assertEquals(0.60, normalizeRandomSpread(missingBounds).randomSpread!!, 0.0)
        assertEquals(invalidSpread, normalizeRandomSpread(invalidSpread))
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
