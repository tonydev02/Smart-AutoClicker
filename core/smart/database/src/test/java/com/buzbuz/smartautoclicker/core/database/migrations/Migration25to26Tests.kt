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
class Migration25to26Tests {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ClickDatabase::class.java)

    @Test
    fun migratePreservesRandomAreaAndLegacyPauseDefaults() {
        val path = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-25-26").path
        helper.createDatabase(path, 25).use { db ->
            db.execSQL("INSERT INTO scenario_table (id, name, detection_quality) VALUES (1, 'scenario', 0)")
            db.execSQL("INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type) VALUES (2, 1, 'event', 0, 0, 1, 'IMAGE_EVENT')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, first_touch_duration, first_touch_mode, first_touch_area_left, first_touch_area_top, first_touch_area_right, first_touch_area_bottom, second_touch_duration, second_touch_mode, second_touch_area_left, second_touch_area_top, second_touch_area_right, second_touch_area_bottom) VALUES (3, 2, 0, 'random area', 'MULTI_TOUCH', 900, 'RANDOM_AREA', 10, 20, 30, 40, 500, 'RANDOM_AREA', 50, 60, 90, 100)")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, pauseDuration) VALUES (4, 2, 1, 'wait', 'PAUSE', 1250)")
        }

        helper.runMigrationsAndValidate(path, 26, true).use { db ->
            db.query("SELECT first_touch_mode, first_touch_area_left, first_touch_duration, first_touch_random_end_x, first_touch_random_end_y, second_touch_mode, second_touch_area_left, second_touch_duration, second_touch_random_end_x, second_touch_random_end_y FROM action_table WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("RANDOM_AREA", cursor.getString(0))
                assertEquals(10, cursor.getInt(1))
                assertEquals(900L, cursor.getLong(2))
                assertNull(cursor.getString(3))
                assertNull(cursor.getString(4))
                assertEquals("RANDOM_AREA", cursor.getString(5))
                assertEquals(50, cursor.getInt(6))
                assertEquals(500L, cursor.getLong(7))
                assertNull(cursor.getString(8))
                assertNull(cursor.getString(9))
            }
            db.query("SELECT pauseDuration, pause_mode, pause_random_min_duration, pause_random_max_duration FROM action_table WHERE id = 4").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1250L, cursor.getLong(0))
                assertNull(cursor.getString(1))
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
            }
        }
    }
}
