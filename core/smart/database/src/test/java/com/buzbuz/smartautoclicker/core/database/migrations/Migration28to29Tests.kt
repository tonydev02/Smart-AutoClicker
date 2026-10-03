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
class Migration28to29Tests {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ClickDatabase::class.java)

    @Test
    fun migrateAddsNullableSpreadWithoutChangingExistingActions() {
        val path = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-28-29").path
        helper.createDatabase(path, 28).use { db ->
            db.execSQL("INSERT INTO scenario_table (id, name, detection_quality) VALUES (1, 'scenario', 0)")
            db.execSQL("INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type) VALUES (2, 1, 'event', 0, 0, 1, 'IMAGE_EVENT')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, pauseDuration, pause_mode) VALUES (3, 2, 0, 'fixed', 'PAUSE', 1250, 'FIXED')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, pauseDuration, pause_mode, pause_random_min_duration, pause_random_max_duration, pause_random_most_likely_duration) VALUES (4, 2, 1, 'range', 'PAUSE', NULL, 'RANDOM_RANGE', 2000, 30000, 5000)")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, first_touch_from_x, first_touch_from_y, first_touch_to_x, first_touch_to_y, first_touch_duration, first_touch_mode) VALUES (5, 2, 2, 'multi', 'MULTI_TOUCH', 10, 20, 30, 40, 900, 'DRAG')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, random_area_left, random_area_top, random_area_right, random_area_bottom, random_area_duration, random_area_end_x, random_area_end_y) VALUES (6, 2, 3, 'movement', 'RANDOM_MOVEMENT', 1, 2, 30, 40, 3000, 100, -25)")
        }

        helper.runMigrationsAndValidate(path, 29, true).use { db ->
            db.query("SELECT pauseDuration, pause_mode, pause_random_spread FROM action_table WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1250L, cursor.getLong(0))
                assertEquals("FIXED", cursor.getString(1))
                assertNull(cursor.getString(2))
            }
            db.query("SELECT pause_random_min_duration, pause_random_max_duration, pause_random_most_likely_duration, pause_random_spread FROM action_table WHERE id = 4").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2_000L, cursor.getLong(0))
                assertEquals(30_000L, cursor.getLong(1))
                assertEquals(5_000L, cursor.getLong(2))
                assertNull(cursor.getString(3))
            }
            db.query("SELECT first_touch_from_x, first_touch_to_y, first_touch_duration, first_touch_mode FROM action_table WHERE id = 5").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(10, cursor.getInt(0))
                assertEquals(40, cursor.getInt(1))
                assertEquals(900L, cursor.getLong(2))
                assertEquals("DRAG", cursor.getString(3))
            }
            db.query("SELECT random_area_left, random_area_bottom, random_area_duration, random_area_end_x, random_area_end_y FROM action_table WHERE id = 6").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(40, cursor.getInt(1))
                assertEquals(3_000L, cursor.getLong(2))
                assertEquals(100, cursor.getInt(3))
                assertEquals(-25, cursor.getInt(4))
            }
        }
    }
}
