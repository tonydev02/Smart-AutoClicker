/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.database.migrations

import android.content.Context
import android.os.Build
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzbuz.smartautoclicker.core.database.ClickDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration30to31Tests {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ClickDatabase::class.java)

    @Test
    fun migrationPreservesScenarioEventAndRandomMovementAction() {
        val path = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-30-31").path
        helper.createDatabase(path, 30).use { db ->
            db.execSQL("INSERT INTO scenario_table (id, name, detection_quality) VALUES (1, 'scenario', 600)")
            db.execSQL("INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting, detecetion_cooldown_ms) VALUES (2, 1, 'screen', 0, 0, 1, 'IMAGE_EVENT', 0, 0)")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, random_area_left, random_area_top, random_area_right, random_area_bottom, random_area_duration, random_area_end_x, random_area_end_y) VALUES (3, 2, 0, 'movement', 'RANDOM_MOVEMENT', 10, 20, 100, 120, 3000, 50, 60)")
        }

        helper.runMigrationsAndValidate(path, 31, true).use { db ->
            db.query("SELECT name, detection_quality FROM scenario_table WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("scenario", cursor.getString(0))
                assertEquals(600, cursor.getInt(1))
            }
            db.query("SELECT name, type, random_area_duration, random_area_end_x, random_area_end_y FROM action_table WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("movement", cursor.getString(0))
                assertEquals("RANDOM_MOVEMENT", cursor.getString(1))
                assertEquals(3000L, cursor.getLong(2))
                assertEquals(50, cursor.getInt(3))
                assertEquals(60, cursor.getInt(4))
            }
        }
    }
}
