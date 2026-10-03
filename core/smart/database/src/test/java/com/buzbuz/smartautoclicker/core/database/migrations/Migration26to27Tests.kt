package com.buzbuz.smartautoclicker.core.database.migrations

import android.content.Context
import android.os.Build
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzbuz.smartautoclicker.core.database.ClickDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration26to27Tests {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ClickDatabase::class.java)

    @Test
    fun migrateAddsNullableStandaloneMovementColumnsWithoutChangingExistingActions() {
        val path = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-26-27").path
        helper.createDatabase(path, 26).use { db ->
            db.execSQL("INSERT INTO scenario_table (id, name, detection_quality) VALUES (1, 'scenario', 0)")
            db.execSQL("INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type) VALUES (2, 1, 'event', 0, 0, 1, 'IMAGE_EVENT')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, x, y, pressDuration, clickPositionType) VALUES (3, 2, 0, 'click', 'CLICK', 11, 12, 100, 'USER_SELECTED')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, fromX, fromY, toX, toY, swipeDuration) VALUES (4, 2, 1, 'swipe', 'SWIPE', 1, 2, 3, 4, 500)")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, first_touch_duration, first_touch_mode, first_touch_area_left, first_touch_area_top, first_touch_area_right, first_touch_area_bottom, first_touch_random_end_x, first_touch_random_end_y, second_touch_duration, second_touch_mode, second_touch_area_left, second_touch_area_top, second_touch_area_right, second_touch_area_bottom) VALUES (5, 2, 2, 'multi', 'MULTI_TOUCH', 900, 'RANDOM_AREA', 10, 20, 30, 40, 100, -20, 500, 'RANDOM_AREA', 50, 60, 90, 100)")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, pauseDuration, pause_mode, pause_random_min_duration, pause_random_max_duration) VALUES (6, 2, 3, 'pause', 'PAUSE', NULL, 'RANDOM_RANGE', 700, 1800)")
        }

        helper.runMigrationsAndValidate(path, 27, true).use { db ->
            db.query("SELECT type, x, y, pressDuration, random_area_left, random_area_duration FROM action_table WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CLICK", cursor.getString(0))
                assertEquals(11, cursor.getInt(1))
                assertEquals(12, cursor.getInt(2))
                assertEquals(100L, cursor.getLong(3))
                assertNull(cursor.getString(4))
                assertNull(cursor.getString(5))
            }
            db.query("SELECT fromX, fromY, toX, toY, swipeDuration, random_area_left FROM action_table WHERE id = 4").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(2, cursor.getInt(1))
                assertEquals(3, cursor.getInt(2))
                assertEquals(4, cursor.getInt(3))
                assertEquals(500L, cursor.getLong(4))
                assertNull(cursor.getString(5))
            }
            db.query("SELECT first_touch_mode, first_touch_area_left, first_touch_random_end_x, first_touch_random_end_y, random_area_left FROM action_table WHERE id = 5").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("RANDOM_AREA", cursor.getString(0))
                assertEquals(10, cursor.getInt(1))
                assertEquals(100, cursor.getInt(2))
                assertEquals(-20, cursor.getInt(3))
                assertNull(cursor.getString(4))
            }
            db.query("SELECT pause_mode, pause_random_min_duration, pause_random_max_duration, random_area_duration FROM action_table WHERE id = 6").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("RANDOM_RANGE", cursor.getString(0))
                assertEquals(700L, cursor.getLong(1))
                assertEquals(1800L, cursor.getLong(2))
                assertNull(cursor.getString(3))
            }
        }
    }
}
