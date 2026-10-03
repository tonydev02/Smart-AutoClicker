package com.buzbuz.smartautoclicker.core.processing.data.processor

import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import kotlin.math.hypot
import kotlin.random.Random

/** Generates bounded integer waypoints for one RANDOM_AREA stroke. */
internal fun generateRandomAreaPoints(area: Rect, durationMs: Long, random: Random): List<Point> {
    require(!area.isEmpty) { "Random area must have non-zero width and height" }
    require(durationMs > 0L)

    val pointCount = (durationMs / WAYPOINT_INTERVAL_MS + 1L).coerceIn(1L, MAX_WAYPOINTS.toLong()).toInt()
    val points = ArrayList<Point>(pointCount)
    val maxX = area.right.toLong() - 1L
    val maxY = area.bottom.toLong() - 1L
    val width = area.right.toLong() - area.left.toLong()
    val height = area.bottom.toLong() - area.top.toLong()
    val usefulDistance = minOf(12.0, hypot((width - 1L).toDouble(), (height - 1L).toDouble()) / 4.0)

    repeat(pointCount) {
        var selected = randomPoint(area, maxX, maxY, random)
        if (points.isNotEmpty() && usefulDistance > 0.0) {
            repeat(MAX_SEPARATION_ATTEMPTS) {
                if (distance(points.last(), selected) < usefulDistance) {
                    selected = randomPoint(area, maxX, maxY, random)
                }
            }
        }
        points += selected
    }
    return points
}

internal fun generateRandomAreaPath(area: Rect, durationMs: Long, random: Random): Path {
    val points = generateRandomAreaPoints(area, durationMs, random)
    return Path().apply {
        moveTo(points[0].x.toFloat(), points[0].y.toFloat())
        for (index in 1 until points.size) {
            lineTo(points[index].x.toFloat(), points[index].y.toFloat())
        }
    }
}

private fun randomPoint(area: Rect, maxX: Long, maxY: Long, random: Random): Point = Point(
    random.nextLong(area.left.toLong(), maxX + 1L).toInt(),
    random.nextLong(area.top.toLong(), maxY + 1L).toInt(),
)

private fun distance(first: Point, second: Point): Double =
    hypot(first.x.toDouble() - second.x.toDouble(), first.y.toDouble() - second.y.toDouble())

private const val WAYPOINT_INTERVAL_MS = 200L
private const val MAX_WAYPOINTS = 512
private const val MAX_SEPARATION_ATTEMPTS = 8
