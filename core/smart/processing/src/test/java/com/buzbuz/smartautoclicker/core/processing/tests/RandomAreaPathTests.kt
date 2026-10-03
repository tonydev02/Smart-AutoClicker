package com.buzbuz.smartautoclicker.core.processing.tests

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.processing.data.processor.generateRandomAreaPath
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
