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
class Migration27to28Tests {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ClickDatabase::class.java)

    @Test
    fun migrateAddsNullableMostLikelyDurationAndPreservesPauseSettings() {
        val path = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-27-28").path
        helper.createDatabase(path, 27).use { db ->
            db.execSQL("INSERT INTO scenario_table (id, name, detection_quality) VALUES (1, 'scenario', 0)")
            db.execSQL("INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type) VALUES (2, 1, 'event', 0, 0, 1, 'IMAGE_EVENT')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, pauseDuration, pause_mode) VALUES (3, 2, 0, 'fixed', 'PAUSE', 1250, 'FIXED')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, pauseDuration, pause_mode, pause_random_min_duration, pause_random_max_duration) VALUES (4, 2, 1, 'range', 'PAUSE', NULL, 'RANDOM_RANGE', 700, 1800)")
        }

        helper.runMigrationsAndValidate(path, 28, true).use { db ->
            db.query("SELECT pauseDuration, pause_mode, pause_random_min_duration, pause_random_max_duration, pause_random_most_likely_duration FROM action_table WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1250L, cursor.getLong(0))
                assertEquals("FIXED", cursor.getString(1))
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
                assertNull(cursor.getString(4))
            }
            db.query("SELECT pauseDuration, pause_mode, pause_random_min_duration, pause_random_max_duration, pause_random_most_likely_duration FROM action_table WHERE id = 4").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
                assertEquals("RANDOM_RANGE", cursor.getString(1))
                assertEquals(700L, cursor.getLong(2))
                assertEquals(1800L, cursor.getLong(3))
                assertNull(cursor.getString(4))
            }
        }
    }
}
