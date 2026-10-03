package com.buzbuz.smartautoclicker.core.database

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.database.entity.ActionEntity
import com.buzbuz.smartautoclicker.core.database.entity.ActionType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class ActionEntitySerializationTests {

    @Test
    fun randomMovementGeometryAndDurationRoundTripInBackupSerialization() {
        val action = ActionEntity(
            id = 5L,
            eventId = 2L,
            name = "random movement",
            type = ActionType.RANDOM_MOVEMENT,
            randomAreaLeft = -10,
            randomAreaTop = 20,
            randomAreaRight = 30,
            randomAreaBottom = 50,
            randomAreaDuration = 3_000L,
            randomAreaEndX = 100,
            randomAreaEndY = -25,
        )

        assertEquals(action, Json.decodeFromString<ActionEntity>(Json.encodeToString(action)))
    }

    @Test
    fun multitouchEndpointsAndPauseModesAreIncludedInExports() {
        val touchJson = Json.encodeToString(
            ActionEntity(
                id = 1L,
                eventId = 2L,
                name = "touch",
                type = ActionType.MULTI_TOUCH,
                firstTouchRandomEndX = 120,
                firstTouchRandomEndY = -25,
                secondTouchRandomEndX = 9,
                secondTouchRandomEndY = 10,
            ),
        )
        assertTrue(touchJson.contains("\"firstTouchRandomEndX\":120"))
        assertTrue(touchJson.contains("\"secondTouchRandomEndY\":10"))

        val fixedJson = Json.encodeToString(
            ActionEntity(
                id = 3L,
                eventId = 2L,
                name = "fixed",
                type = ActionType.PAUSE,
                pauseDuration = 1_000L,
                pauseMode = "FIXED",
            ),
        )
        assertTrue(fixedJson.contains("\"pauseMode\":\"FIXED\""))

        val rangeJson = Json.encodeToString(
            ActionEntity(
                id = 4L,
                eventId = 2L,
                name = "range",
                type = ActionType.PAUSE,
                pauseMode = "RANDOM_RANGE",
                pauseRandomMinDuration = 700L,
                pauseRandomMaxDuration = 1_800L,
                pauseRandomMostLikelyDuration = 1_200L,
            ),
        )
        assertTrue(rangeJson.contains("\"pauseMode\":\"RANDOM_RANGE\""))
        assertTrue(rangeJson.contains("\"pauseRandomMinDuration\":700"))
        assertTrue(rangeJson.contains("\"pauseRandomMaxDuration\":1800"))
        assertTrue(rangeJson.contains("\"pauseRandomMostLikelyDuration\":1200"))
    }
}
