/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.brief

import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.selection.ActionTypeChoice
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartActionsBriefViewModelTests {

    @Test
    fun actionTypeChoices_includeRandomMovementAndPause() {
        val choices = buildActionTypeChoices(canCopy = false, legacyEnabled = false)

        assertTrue(choices.contains(ActionTypeChoice.RandomMovement))
        assertTrue(choices.contains(ActionTypeChoice.Pause))
    }
}
