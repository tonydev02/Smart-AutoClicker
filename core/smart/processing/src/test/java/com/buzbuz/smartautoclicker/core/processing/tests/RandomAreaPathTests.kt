package com.buzbuz.smartautoclicker.core.processing.tests

import android.graphics.Rect
import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.processing.data.processor.generateRandomAreaPath
import com.buzbuz.smartautoclicker.core.processing.data.processor.selectRandomPauseDuration
import com.buzbuz.smartautoclicker.core.processing.data.processor.generateRandomAreaPoints
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class RandomAreaPathTests {

    @Test
    fun generatedPathsStayInsideAreaForSmallAndLargeDurations() {
        val areas = listOf(
            Rect(10, 20, 210, 180),
            Rect(0, 0, 1, 1),
            Rect(-30, 2, -29, 30),
            Rect(4, 7, 40, 8),
        )
        val durations = listOf(1L, 20L, 250L, 2_000L, 59_999L)
        var seed = 0
        for (area in areas) {
            for (duration in durations) {
                val points = generateRandomAreaPoints(area, duration, Random(seed++))
                assertFalse(points.isEmpty())
                points.forEach { point ->
                    assertTrue(point.x >= area.left && point.x < area.right)
                    assertTrue(point.y >= area.top && point.y < area.bottom)
                }
                assertEquals(points, generateRandomAreaPoints(area, duration, Random(seed - 1)))
                assertFalse(generateRandomAreaPath(area, duration, Random(seed)).isEmpty)
            }
        }
    }

    @Test
    fun optionalEndpointIsLastAndDoesNotChangeRandomWaypoints() {
        val area = Rect(10, 20, 30, 40)
        val randomPoints = generateRandomAreaPoints(area, 800L, Random(7))
        val outside = Point(500, -40)
        val path = generateRandomAreaPath(area, 800L, Random(7), outside)
        val coordinates = path.approximate(0.1f)
        assertEquals((randomPoints.size + 1) * 3, coordinates.size)
        randomPoints.forEachIndexed { index, point ->
            assertEquals(point.x.toFloat(), coordinates[index * 3 + 1], 0f)
            assertEquals(point.y.toFloat(), coordinates[index * 3 + 2], 0f)
        }
        assertEquals(outside.x.toFloat(), coordinates[coordinates.lastIndex - 1], 0f)
        assertEquals(outside.y.toFloat(), coordinates.last(), 0f)
        assertEquals(
            generateRandomAreaPath(area, 800L, Random(7)).approximate(0.1f).toList(),
            generateRandomAreaPath(area, 800L, Random(7), null).approximate(0.1f).toList(),
        )
        assertFalse(generateRandomAreaPath(Rect(0, 0, 1, 1), 1L, Random(1), Point(0, 0)).isEmpty)
        assertFalse(generateRandomAreaPath(area, 800L, Random(7), randomPoints.first()).isEmpty)
        assertFalse(generateRandomAreaPath(area, 800L, Random(7), randomPoints.last()).isEmpty)
    }

    @Test
    fun randomPauseDurationIsBoundedDeterministicAndHandlesLongExtremes() {
        val random = Random(55)
        repeat(2_000) {
            val result = selectRandomPauseDuration(700L, 1_100L, 1_800L, 0.60, random)
            assertTrue(result in 700L..1_800L)
        }

        assertEquals(
            (0 until 100).map { selectRandomPauseDuration(700L, 1_100L, 1_800L, 0.60, Random(it)) },
            (0 until 100).map { selectRandomPauseDuration(700L, 1_100L, 1_800L, 0.60, Random(it)) },
        )
        assertEquals(
            Long.MAX_VALUE,
            selectRandomPauseDuration(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0.60, random),
        )
        repeat(100) {
            assertTrue(
                selectRandomPauseDuration(
                    Long.MAX_VALUE - 20L,
                    Long.MAX_VALUE - 10L,
                    Long.MAX_VALUE,
                    0.60,
                    random,
                ) in (Long.MAX_VALUE - 20L)..Long.MAX_VALUE,
            )
            assertTrue(selectRandomPauseDuration(1L, 1L, 100L, 0.60, random) in 1L..100L)
            assertTrue(selectRandomPauseDuration(1L, 100L, 100L, 0.60, random) in 1L..100L)
        }
        assertEquals(7L, selectRandomPauseDuration(7L, 7L, 7L, 0.60, random))
        assertEquals(100L, selectRandomPauseDuration(1L, 100L, 100L, 0.60, AlwaysNearOneRandom))

        val variedSamples = (0 until 100).map {
            selectRandomPauseDuration(1L, 50L, 100L, 0.60, Random(it))
        }.toSet()
        assertTrue(variedSamples.size > 1)
    }

    @Test
    fun randomPauseDurationSpreadControlsTailWithoutChangingHardBounds() {
        val focusedRandom = Random(42)
        val wideRandom = Random(42)
        val sampleCount = 20_000
        var focusedTotal = 0.0
        var wideTotal = 0.0
        var focusedUpperTailCount = 0
        var wideUpperTailCount = 0
        repeat(sampleCount) {
            val focused = selectRandomPauseDuration(2_000L, 5_000L, 30_000L, 0.25, focusedRandom)
            val wide = selectRandomPauseDuration(2_000L, 5_000L, 30_000L, 0.90, wideRandom)
            assertTrue(focused in 2_000L..30_000L)
            assertTrue(wide in 2_000L..30_000L)
            focusedTotal += focused
            wideTotal += wide
            if (focused > 12_000L) focusedUpperTailCount++
            if (wide > 12_000L) wideUpperTailCount++
        }

        assertTrue(wideTotal / sampleCount > focusedTotal / sampleCount * 1.15)
        assertTrue(wideUpperTailCount > focusedUpperTailCount + 500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun randomPauseDurationRejectsSpreadBelowSupportedRange() {
        selectRandomPauseDuration(1L, 5L, 10L, 0.149, Random(1))
    }

    private object AlwaysNearOneRandom : Random() {
        override fun nextBits(bitCount: Int): Int = -1
    }

    @Test
    fun largeAreaProducesSpatiallyUsefulMovement() {
        val points = generateRandomAreaPoints(Rect(0, 0, 1_000, 1_000), 1_000L, Random(42))
        assertTrue(points.size > 1)
        assertTrue(points.zipWithNext().any { (first, second) -> first != second })
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyAreaIsRejected() {
        generateRandomAreaPoints(Rect(2, 2, 2, 10), 100L, Random(1))
    }
}
