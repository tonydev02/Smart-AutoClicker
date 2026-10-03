/*
 * Copyright (C) 2026 Kevin Buzeau
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
class Migration29to30Tests {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ClickDatabase::class.java)

    @Test
    fun migrateAddsNullableWebhookFieldsWithoutChangingExistingActions() {
        val path = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-29-30").path
        helper.createDatabase(path, 29).use { db ->
            db.execSQL("INSERT INTO scenario_table (id, name, detection_quality) VALUES (1, 'scenario', 0)")
            db.execSQL("INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type) VALUES (2, 1, 'event', 0, 0, 1, 'IMAGE_EVENT')")
            db.execSQL("INSERT INTO action_table (id, eventId, priority, name, type, notification_message_text, notification_importance) VALUES (3, 2, 0, 'notification', 'NOTIFICATION', 'unchanged', 3)")
        }

        helper.runMigrationsAndValidate(path, 30, true).use { db ->
            db.query("SELECT name, type, notification_message_text, webhook_mode, webhook_telegram_bot_token, webhook_telegram_chat_id, webhook_telegram_message, webhook_custom_url, webhook_custom_content_type, webhook_custom_body FROM action_table WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("notification", cursor.getString(0))
                assertEquals("NOTIFICATION", cursor.getString(1))
                assertEquals("unchanged", cursor.getString(2))
                for (column in 3..9) assertNull(cursor.getString(column))
            }
        }
    }
}
