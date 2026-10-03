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
class Migration24to25Tests {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ClickDatabase::class.java)

    @Test
    fun migrate_existingMultiTouch_keepsLegacyDragCoordinates() {
        val path = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-24-25").path
        helper.createDatabase(path, 24).use { db ->
            db.execSQL("INSERT INTO scenario_table (id, name, detection_quality) VALUES (1, 'scenario', 0)")
            db.execSQL("INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type) VALUES (2, 1, 'event', 0, 0, 1, 'IMAGE_EVENT')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, first_touch_from_x, first_touch_from_y, first_touch_to_x, first_touch_to_y, first_touch_duration, second_touch_from_x, second_touch_from_y, second_touch_to_x, second_touch_to_y, second_touch_duration) VALUES (3, 2, 0, 'multi', 'MULTI_TOUCH', 10, 20, 30, 40, 250, 50, 60, 70, 80, 500)")
        }

        helper.runMigrationsAndValidate(path, 25, true).use { db ->
            db.query("SELECT first_touch_from_x, first_touch_to_x, first_touch_mode, second_touch_mode FROM action_table WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(10, cursor.getInt(0))
                assertEquals(30, cursor.getInt(1))
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
            }
        }
    }
}
