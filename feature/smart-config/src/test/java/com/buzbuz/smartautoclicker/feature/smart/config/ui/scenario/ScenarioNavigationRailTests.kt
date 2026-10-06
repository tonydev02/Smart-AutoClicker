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
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.buzbuz.smartautoclicker.core.common.overlays.databinding.DialogBaseNavBarBinding
import com.buzbuz.smartautoclicker.feature.smart.config.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [android.os.Build.VERSION_CODES.Q], qualifiers = "land")
class ScenarioNavigationRailTests {

    @Test
    fun allFiveDestinationsFitAndRemainSelectableInShortLandscapeDialog() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val context = ContextThemeWrapper(appContext, R.style.ScenarioDialogTheme)
        val binding = DialogBaseNavBarBinding.inflate(LayoutInflater.from(context))
        val rail = requireNotNull(binding.navBar)
        rail.inflateMenu(R.menu.menu_scenario_config)
        rail.configureScenarioNavigationRail(context)

        val density = context.resources.displayMetrics.density
        val itemMinHeight = context.resources.getDimensionPixelSize(R.dimen.scenario_navigation_rail_item_min_height)
        assertEquals((48 * density).toInt(), itemMinHeight)
        assertEquals(itemMinHeight, rail.itemMinimumHeight)
        assertEquals(itemMinHeight, rail.collapsedItemMinimumHeight)
        assertFalse("Scenario rail must stay collapsed", rail.isExpanded)
        assertEquals(0, rail.itemSpacing)
        assertEquals(0, rail.itemPaddingTop)
        assertEquals(0, rail.itemPaddingBottom)

        val viewportWidth = (800 * density).toInt()
        val viewportHeight = (325 * density).toInt()
        binding.root.measure(
            MeasureSpec.makeMeasureSpec(viewportWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(viewportHeight, MeasureSpec.EXACTLY),
        )
        binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)

        val destinationIds = listOf(
            R.id.page_image_events,
            R.id.page_trigger_events,
            R.id.page_filler_events,
            R.id.page_config,
            R.id.page_more,
        )
        val itemBounds = destinationIds.associateWith { destinationId ->
            val itemView = requireNotNull(rail.findViewById<View>(destinationId))
            Rect(0, 0, itemView.width, itemView.height).also { bounds ->
                rail.offsetDescendantRectToMyCoords(itemView, bounds)
            }
        }
        val moreBounds = itemBounds.getValue(R.id.page_more)
        assertTrue(
            "More is clipped: bounds=$moreBounds railHeight=${rail.height}, " +
                "itemMinHeight=${rail.itemMinimumHeight}, collapsedMinHeight=${rail.collapsedItemMinimumHeight}, " +
                "effectiveItemSpacing=${rail.itemSpacing}, allBounds=$itemBounds",
            moreBounds.top >= 0 && moreBounds.bottom <= rail.height,
        )

        destinationIds.forEach { destinationId ->
            val bounds = itemBounds.getValue(destinationId)
            assertTrue(
                "Destination is clipped: $destinationId bounds=$bounds",
                bounds.top >= 0 && bounds.bottom <= rail.height,
            )
            assertEquals(itemMinHeight, bounds.height())
            val itemView = requireNotNull(rail.findViewById<View>(destinationId))
            assertTrue("Destination is not selectable: $destinationId", itemView.performClick())
            assertEquals(destinationId, rail.selectedItemId)
        }
    }
}
