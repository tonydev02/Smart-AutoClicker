/*
 * Copyright (C) 2024 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.database.serialization.compat

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.buzbuz.smartautoclicker.core.database.entity.ActionType
import com.buzbuz.smartautoclicker.core.database.entity.ConditionType
import com.buzbuz.smartautoclicker.core.database.entity.CounterComparisonOperation
import com.buzbuz.smartautoclicker.core.database.serialization.DeserializerFactory

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Tests for compatibility deserialization in [CompatDeserializer]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class CompatDeserializerTests {

    private val deserializer = object : CompatDeserializer() {}

    private fun createJsonImageCondition(detectionType: Int): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(1L),
            "eventId" to JsonPrimitive(1L),
            "name" to JsonPrimitive("test"),
            "priority" to JsonPrimitive(0),
            "type" to JsonPrimitive(ConditionType.ON_IMAGE_DETECTED.name),
            "path" to JsonPrimitive("/test/path"),
            "areaLeft" to JsonPrimitive(0),
            "areaTop" to JsonPrimitive(0),
            "areaRight" to JsonPrimitive(100),
            "areaBottom" to JsonPrimitive(100),
            "shouldBeDetected" to JsonPrimitive(true),
            "detectionType" to JsonPrimitive(detectionType),
            "threshold" to JsonPrimitive(4),
        )
    )

    @Test
    fun deserializeConditionImageDetected_detectionType_exactMatch_isPreserved() {
        // Given detectionType = 1 (EXACT)
        val json = createJsonImageCondition(detectionType = 1)

        // When
        val result = deserializer.deserializeConditionImageDetected(json)

        // Then
        assertNotNull(result)
        assertEquals(1, result!!.detectionType)
    }

    @Test
    fun deserializeConditionImageDetected_detectionType_wholeScreen_isPreserved() {
        // Given detectionType = 2 (WHOLE_SCREEN)
        val json = createJsonImageCondition(detectionType = 2)

        // When
        val result = deserializer.deserializeConditionImageDetected(json)

        // Then
        assertNotNull(result)
        assertEquals(2, result!!.detectionType)
    }

    @Test
    fun deserializeConditionImageDetected_detectionType_inArea_isPreserved() {
        // Given detectionType = 3 (IN_AREA) — this was incorrectly clamped to 2 when
        // DETECTION_TYPE_UPPER_BOUND was wrongly set to 2 instead of 3.
        val json = createJsonImageCondition(detectionType = 3)

        // When
        val result = deserializer.deserializeConditionImageDetected(json)

        // Then
        assertNotNull(result)
        assertEquals(3, result!!.detectionType)
    }

    @Test
    fun deserializeConditionImageDetected_detectionType_belowLowerBound_isClampedToDefault() {
        // Given detectionType = 0, below DETECTION_TYPE_LOWER_BOUND = 1
        val json = createJsonImageCondition(detectionType = 0)

        // When
        val result = deserializer.deserializeConditionImageDetected(json)

        // Then
        assertNotNull(result)
        assertEquals(1, result!!.detectionType)
    }

    @Test
    fun deserializeConditionImageDetected_detectionType_aboveUpperBound_isClamped() {
        // Given detectionType = 4, above DETECTION_TYPE_UPPER_BOUND = 3
        val json = createJsonImageCondition(detectionType = 4)

        // When
        val result = deserializer.deserializeConditionImageDetected(json)

        // Then
        assertNotNull(result)
        assertEquals(3, result!!.detectionType)
    }

    // ===== deserializeConditionNumberDetected – threshold =====

    private fun createJsonNumberCondition(threshold: Int? = null): JsonObject {
        val map = mutableMapOf(
            "id" to JsonPrimitive(1L),
            "eventId" to JsonPrimitive(1L),
            "name" to JsonPrimitive("test"),
            "type" to JsonPrimitive(ConditionType.ON_NUMBER_DETECTED.name),
            "detectionAreaLeft" to JsonPrimitive(0),
            "detectionAreaTop" to JsonPrimitive(0),
            "detectionAreaRight" to JsonPrimitive(100),
            "detectionAreaBottom" to JsonPrimitive(100),
            "numberCounterComparisonOperation" to JsonPrimitive(CounterComparisonOperation.GREATER.name),
            "numberCounterValue" to JsonPrimitive(0.0),
        )
        if (threshold != null) map["threshold"] = JsonPrimitive(threshold)
        return JsonObject(map)
    }

    @Test
    fun deserializeConditionNumberDetected_threshold_validValue_isPreserved() {
        val json = createJsonNumberCondition(threshold = 10)

        val result = deserializer.deserializeConditionNumberDetected(json)

        assertNotNull(result)
        assertEquals(10, result!!.threshold)
    }

    @Test
    fun deserializeConditionNumberDetected_threshold_belowLowerBound_isClamped() {
        // threshold = -1, below CONDITION_THRESHOLD_LOWER_BOUND = 0
        val json = createJsonNumberCondition(threshold = -1)

        val result = deserializer.deserializeConditionNumberDetected(json)

        assertNotNull(result)
        assertEquals(0, result!!.threshold)
    }

    @Test
    fun deserializeConditionNumberDetected_threshold_aboveUpperBound_isClamped() {
        // threshold = 99, above CONDITION_THRESHOLD_UPPER_BOUND = 20
        val json = createJsonNumberCondition(threshold = 99)

        val result = deserializer.deserializeConditionNumberDetected(json)

        assertNotNull(result)
        assertEquals(20, result!!.threshold)
    }

    @Test
    fun deserializeConditionNumberDetected_threshold_missing_usesDefault() {
        val json = createJsonNumberCondition(threshold = null)

        val result = deserializer.deserializeConditionNumberDetected(json)

        assertNotNull(result)
        assertEquals(4, result!!.threshold)
    }

    @Test
    fun deserializeAction_multiTouchFromV24_preservesDragStrokes() {
        val json = JsonObject(
            mapOf(
                "id" to JsonPrimitive(1L),
                "eventId" to JsonPrimitive(2L),
                "name" to JsonPrimitive("legacy multitouch"),
                "priority" to JsonPrimitive(3),
                "type" to JsonPrimitive(ActionType.MULTI_TOUCH.name),
                "firstTouchFromX" to JsonPrimitive(11),
                "firstTouchFromY" to JsonPrimitive(12),
                "firstTouchToX" to JsonPrimitive(13),
                "firstTouchToY" to JsonPrimitive(14),
                "firstTouchDuration" to JsonPrimitive(150L),
                "secondTouchFromX" to JsonPrimitive(21),
                "secondTouchFromY" to JsonPrimitive(22),
                "secondTouchToX" to JsonPrimitive(23),
                "secondTouchToY" to JsonPrimitive(24),
                "secondTouchDuration" to JsonPrimitive(250L),
            )
        )

        val result = deserializeActionForVersion(24, json)

        assertNotNull(result)
        assertEquals(ActionType.MULTI_TOUCH, result!!.type)
        assertEquals(11, result.firstTouchFromX)
        assertEquals(12, result.firstTouchFromY)
        assertEquals(13, result.firstTouchToX)
        assertEquals(14, result.firstTouchToY)
        assertEquals(150L, result.firstTouchDuration)
        assertEquals(21, result.secondTouchFromX)
        assertEquals(22, result.secondTouchFromY)
        assertEquals(23, result.secondTouchToX)
        assertEquals(24, result.secondTouchToY)
        assertEquals(250L, result.secondTouchDuration)
        assertNull(result.firstTouchMode)
        assertNull(result.secondTouchMode)
    }

    @Test
    fun deserializeAction_multiTouchFromV25_preservesPressAndRandomAreaModes() {
        val json = createActionJson(
            ActionType.MULTI_TOUCH,
            mapOf(
                "firstTouchMode" to JsonPrimitive("PRESS"),
                "firstTouchFromX" to JsonPrimitive(50),
                "firstTouchFromY" to JsonPrimitive(60),
                "firstTouchDuration" to JsonPrimitive(300L),
                "secondTouchMode" to JsonPrimitive("RANDOM_AREA"),
                "secondTouchDuration" to JsonPrimitive(400L),
                "secondTouchAreaLeft" to JsonPrimitive(10),
                "secondTouchAreaTop" to JsonPrimitive(20),
                "secondTouchAreaRight" to JsonPrimitive(40),
                "secondTouchAreaBottom" to JsonPrimitive(60),
            ),
        )

        val result = deserializeActionForVersion(25, json)!!

        assertEquals(ActionType.MULTI_TOUCH, result.type)
        assertEquals("PRESS", result.firstTouchMode)
        assertEquals(50, result.firstTouchFromX)
        assertEquals(60, result.firstTouchFromY)
        assertNull(result.firstTouchToX)
        assertNull(result.firstTouchToY)
        assertEquals("RANDOM_AREA", result.secondTouchMode)
        assertNull(result.secondTouchFromX)
        assertNull(result.secondTouchToX)
        assertEquals(10, result.secondTouchAreaLeft)
        assertEquals(20, result.secondTouchAreaTop)
        assertEquals(40, result.secondTouchAreaRight)
        assertEquals(60, result.secondTouchAreaBottom)
    }

    @Test
    fun deserializeAction_multiTouchFromV26_preservesRandomAreaEndpoint() {
        val json = createActionJson(
            ActionType.MULTI_TOUCH,
            mapOf(
                "firstTouchMode" to JsonPrimitive("RANDOM_AREA"),
                "firstTouchDuration" to JsonPrimitive(500L),
                "firstTouchAreaLeft" to JsonPrimitive(10),
                "firstTouchAreaTop" to JsonPrimitive(20),
                "firstTouchAreaRight" to JsonPrimitive(40),
                "firstTouchAreaBottom" to JsonPrimitive(60),
                "firstTouchRandomEndX" to JsonPrimitive(70),
                "firstTouchRandomEndY" to JsonPrimitive(80),
                "secondTouchMode" to JsonPrimitive("PRESS"),
                "secondTouchFromX" to JsonPrimitive(30),
                "secondTouchFromY" to JsonPrimitive(40),
                "secondTouchDuration" to JsonPrimitive(600L),
            ),
        )

        val result = deserializeActionForVersion(26, json)!!

        assertEquals("RANDOM_AREA", result.firstTouchMode)
        assertEquals(10, result.firstTouchAreaLeft)
        assertEquals(20, result.firstTouchAreaTop)
        assertEquals(40, result.firstTouchAreaRight)
        assertEquals(60, result.firstTouchAreaBottom)
        assertEquals(500L, result.firstTouchDuration)
        assertEquals(70, result.firstTouchRandomEndX)
        assertEquals(80, result.firstTouchRandomEndY)
    }

    @Test
    fun deserializeAction_pauseFromV26_preservesRandomRange() {
        val json = createActionJson(
            ActionType.PAUSE,
            mapOf(
                "pauseMode" to JsonPrimitive("RANDOM_RANGE"),
                "pauseRandomMinDuration" to JsonPrimitive(700L),
                "pauseRandomMostLikelyDuration" to JsonPrimitive(1200L),
                "pauseRandomMaxDuration" to JsonPrimitive(1800L),
            ),
        )

        val result = deserializeActionForVersion(26, json)!!

        assertEquals(ActionType.PAUSE, result.type)
        assertEquals("RANDOM_RANGE", result.pauseMode)
        assertNull(result.pauseDuration)
        assertEquals(700L, result.pauseRandomMinDuration)
        assertEquals(1_200L, result.pauseRandomMostLikelyDuration)
        assertEquals(1800L, result.pauseRandomMaxDuration)
    }

    @Test
    fun deserializeAction_pauseFromV27WithoutPeakPreservesLegacyBounds() {
        val json = createActionJson(
            ActionType.PAUSE,
            mapOf(
                "pauseMode" to JsonPrimitive("RANDOM_RANGE"),
                "pauseRandomMinDuration" to JsonPrimitive(700L),
                "pauseRandomMaxDuration" to JsonPrimitive(1800L),
            ),
        )

        val result = deserializeActionForVersion(27, json)!!

        assertEquals("RANDOM_RANGE", result.pauseMode)
        assertEquals(700L, result.pauseRandomMinDuration)
        assertEquals(1800L, result.pauseRandomMaxDuration)
        assertNull(result.pauseRandomMostLikelyDuration)
    }

    @Test
    fun deserializeAction_pauseFromV28WithoutSpreadPreservesRangeAndLeavesSpreadNull() {
        val json = createActionJson(
            ActionType.PAUSE,
            mapOf(
                "pauseMode" to JsonPrimitive("RANDOM_RANGE"),
                "pauseRandomMinDuration" to JsonPrimitive(2_000L),
                "pauseRandomMostLikelyDuration" to JsonPrimitive(5_000L),
                "pauseRandomMaxDuration" to JsonPrimitive(30_000L),
            ),
        )

        val result = deserializeActionForVersion(28, json)!!

        assertEquals("RANDOM_RANGE", result.pauseMode)
        assertEquals(2_000L, result.pauseRandomMinDuration)
        assertEquals(5_000L, result.pauseRandomMostLikelyDuration)
        assertEquals(30_000L, result.pauseRandomMaxDuration)
        assertNull(result.pauseRandomSpread)
    }

    @Test
    fun deserializeAction_pauseFromV28PreservesValidSpreadAndRejectsInvalidSpread() {
        val validJson = createActionJson(
            ActionType.PAUSE,
            mapOf(
                "pauseMode" to JsonPrimitive("RANDOM_RANGE"),
                "pauseRandomMinDuration" to JsonPrimitive(2_000L),
                "pauseRandomMostLikelyDuration" to JsonPrimitive(5_000L),
                "pauseRandomMaxDuration" to JsonPrimitive(30_000L),
                "pauseRandomSpread" to JsonPrimitive(0.85),
            ),
        )
        val invalidJson = createActionJson(
            ActionType.PAUSE,
            mapOf(
                "pauseMode" to JsonPrimitive("RANDOM_RANGE"),
                "pauseRandomMinDuration" to JsonPrimitive(2_000L),
                "pauseRandomMostLikelyDuration" to JsonPrimitive(5_000L),
                "pauseRandomMaxDuration" to JsonPrimitive(30_000L),
                "pauseRandomSpread" to JsonPrimitive(1.5),
            ),
        )

        assertEquals(0.85, deserializeActionForVersion(28, validJson)!!.pauseRandomSpread!!, 0.0)
        assertNull(deserializeActionForVersion(28, invalidJson))
    }

    @Test
    fun deserializeAction_pauseFromV26_rejectsInvalidRandomRange() {
        val json = createActionJson(
            ActionType.PAUSE,
            mapOf(
                "pauseMode" to JsonPrimitive("RANDOM_RANGE"),
                "pauseRandomMinDuration" to JsonPrimitive(1800L),
                "pauseRandomMaxDuration" to JsonPrimitive(700L),
            ),
        )

        assertNull(deserializeActionForVersion(26, json))
    }

    @Test
    fun deserializeAction_pauseWithPeakOutsideRangeIsRejected() {
        val json = createActionJson(
            ActionType.PAUSE,
            mapOf(
                "pauseMode" to JsonPrimitive("RANDOM_RANGE"),
                "pauseRandomMinDuration" to JsonPrimitive(700L),
                "pauseRandomMostLikelyDuration" to JsonPrimitive(1_801L),
                "pauseRandomMaxDuration" to JsonPrimitive(1_800L),
            ),
        )

        assertNull(deserializeActionForVersion(27, json))
    }

    @Test
    fun deserializeAction_legacyPauseWithoutMode_remainsFixed() {
        val json = createActionJson(
            ActionType.PAUSE,
            mapOf("pauseDuration" to JsonPrimitive(333L)),
        )

        val result = deserializeActionForVersion(24, json)!!

        assertEquals(ActionType.PAUSE, result.type)
        assertNull(result.pauseMode)
        assertEquals(333L, result.pauseDuration)
        assertNull(result.pauseRandomMinDuration)
        assertNull(result.pauseRandomMaxDuration)
    }

    @Test
    fun deserializeAction_pauseWithFixedMode_preservesDuration() {
        val json = createActionJson(
            ActionType.PAUSE,
            mapOf(
                "pauseMode" to JsonPrimitive("FIXED"),
                "pauseDuration" to JsonPrimitive(444L),
            ),
        )

        val result = deserializeActionForVersion(26, json)!!

        assertEquals("FIXED", result.pauseMode)
        assertEquals(444L, result.pauseDuration)
    }

    @Test
    fun deserializeAction_randomMovementFromV27Compat_preservesAreaDurationAndOptionalEndpoint() {
        val compatDeserializer = object : CompatDeserializer() {}
        val withEndpoint = createActionJson(
            ActionType.RANDOM_MOVEMENT,
            randomMovementFields(endX = 500, endY = 200),
        )
        val withoutEndpoint = createActionJson(
            ActionType.RANDOM_MOVEMENT,
            randomMovementFields(),
        )

        val resultWithEndpoint = compatDeserializer.deserializeAction(withEndpoint, emptyList(), 1)!!
        val resultWithoutEndpoint = compatDeserializer.deserializeAction(withoutEndpoint, emptyList(), 1)!!

        assertEquals(ActionType.RANDOM_MOVEMENT, resultWithEndpoint.type)
        assertEquals(1L, resultWithEndpoint.id)
        assertEquals(2L, resultWithEndpoint.eventId)
        assertEquals("compat action", resultWithEndpoint.name)
        assertEquals(3, resultWithEndpoint.priority)
        assertEquals(10, resultWithEndpoint.randomAreaLeft)
        assertEquals(20, resultWithEndpoint.randomAreaTop)
        assertEquals(40, resultWithEndpoint.randomAreaRight)
        assertEquals(60, resultWithEndpoint.randomAreaBottom)
        assertEquals(900L, resultWithEndpoint.randomAreaDuration)
        assertEquals(500, resultWithEndpoint.randomAreaEndX)
        assertEquals(200, resultWithEndpoint.randomAreaEndY)
        assertEquals(900L, resultWithoutEndpoint.randomAreaDuration)
        assertNull(resultWithoutEndpoint.randomAreaEndX)
        assertNull(resultWithoutEndpoint.randomAreaEndY)
    }

    @Test
    fun deserializeAction_webhookPreservesModeAndCredentials() {
        val json = createActionJson(
            ActionType.WEBHOOK,
            mapOf(
                "webhookMode" to JsonPrimitive("TELEGRAM_BOT"),
                "webhookTelegramBotToken" to JsonPrimitive("secret-token"),
                "webhookTelegramChatId" to JsonPrimitive("-100123"),
                "webhookTelegramMessage" to JsonPrimitive("hello {count}"),
                "webhookCustomUrl" to JsonPrimitive("https://example.com/hook"),
                "webhookCustomContentType" to JsonPrimitive("application/json"),
                "webhookCustomBody" to JsonPrimitive("""{"count":"{count}"}"""),
            ),
        )

        val result = deserializeActionForVersion(29, json)!!

        assertEquals(ActionType.WEBHOOK, result.type)
        assertEquals("TELEGRAM_BOT", result.webhookMode)
        assertEquals("secret-token", result.webhookTelegramBotToken)
        assertEquals("-100123", result.webhookTelegramChatId)
        assertEquals("hello {count}", result.webhookTelegramMessage)
        assertEquals("https://example.com/hook", result.webhookCustomUrl)
        assertEquals("application/json", result.webhookCustomContentType)
        assertEquals("""{"count":"{count}"}""", result.webhookCustomBody)
        assertNull(
            deserializeActionForVersion(
                29,
                createActionJson(ActionType.WEBHOOK, mapOf("webhookMode" to JsonPrimitive("INVALID"))),
            ),
        )
    }
    private fun deserializeActionForVersion(version: Int, json: JsonObject) =
        (DeserializerFactory.create(version) as CompatDeserializer)
            .deserializeAction(json, emptyList(), 1)

    private fun createActionJson(type: ActionType, fields: Map<String, JsonPrimitive>): JsonObject =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(1L),
                "eventId" to JsonPrimitive(2L),
                "name" to JsonPrimitive("compat action"),
                "priority" to JsonPrimitive(3),
                "type" to JsonPrimitive(type.name),
            ) + fields
        )

    private fun randomMovementFields(endX: Int? = null, endY: Int? = null): Map<String, JsonPrimitive> =
        buildMap {
            put("randomAreaLeft", JsonPrimitive(10))
            put("randomAreaTop", JsonPrimitive(20))
            put("randomAreaRight", JsonPrimitive(40))
            put("randomAreaBottom", JsonPrimitive(60))
            put("randomAreaDuration", JsonPrimitive(900L))
            endX?.let { put("randomAreaEndX", JsonPrimitive(it)) }
            endY?.let { put("randomAreaEndY", JsonPrimitive(it)) }
        }
}
