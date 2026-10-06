/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [android.os.Build.VERSION_CODES.Q])
class ScenarioNavigationRailTests {

    @Test
    fun allFiveDestinationsFitAndRemainSelectableInShortLandscapeRail() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val context = ContextThemeWrapper(appContext, R.style.ScenarioConfigTheme)
        val rail = NavigationRailView(context)
        rail.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED
        rail.inflateMenu(R.menu.menu_scenario_config)
        rail.configureScenarioNavigationRail(context)

        val itemMinHeight = context.resources.getDimensionPixelSize(R.dimen.scenario_navigation_rail_item_min_height)
        assertEquals(48f, context.resources.getDimension(R.dimen.scenario_navigation_rail_item_min_height) /
            context.resources.displayMetrics.density)
        assertEquals(itemMinHeight, rail.itemMinimumHeight)

        val viewportHeight = (288 * context.resources.displayMetrics.density).toInt()
        val viewportWidth = (80 * context.resources.displayMetrics.density).toInt()
        rail.measure(
            MeasureSpec.makeMeasureSpec(viewportWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(viewportHeight, MeasureSpec.EXACTLY),
        )
        rail.layout(0, 0, rail.measuredWidth, rail.measuredHeight)

        val destinationIds = listOf(
            R.id.page_image_events,
            R.id.page_trigger_events,
            R.id.page_filler_events,
            R.id.page_config,
            R.id.page_more,
        )
        destinationIds.forEach { destinationId ->
            val itemView = rail.findViewById<View>(destinationId)
            assertNotNull("Missing navigation destination $destinationId", itemView)
            val itemBounds = Rect(0, 0, itemView.width, itemView.height)
            rail.offsetDescendantRectToMyCoords(itemView, itemBounds)
            assertTrue(
                "Destination is clipped: $destinationId bounds=$itemBounds railHeight=${rail.height}",
                itemBounds.top >= 0 && itemBounds.bottom <= rail.height,
            )
            assertTrue("Destination is too small: $destinationId", itemView.height >= itemMinHeight)
            assertTrue("Destination is not selectable: $destinationId", itemView.performClick())
            assertEquals(destinationId, rail.selectedItemId)
        }
    }
}
