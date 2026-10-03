package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.multitouch

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.buzbuz.smartautoclicker.core.common.overlays.menu.OverlayMenu
import com.buzbuz.smartautoclicker.core.ui.views.areaselector.AreaSelectorView
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.OverlayValidationMenuBinding

/** MultiTouch-specific host for the shared screen-area selection view. */
internal class MultiTouchAreaSelectorMenu(
    private val initialArea: Rect?,
    private val onAreaSelected: (Rect) -> Unit,
) : OverlayMenu(theme = R.style.ScenarioConfigTheme) {

    private lateinit var binding: OverlayValidationMenuBinding
    private lateinit var selector: AreaSelectorView

    override fun tutorialMonitoringTag(): String = "MULTI_TOUCH_AREA_SELECTOR"

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup {
        selector = AreaSelectorView(context, displayConfigManager)
        binding = OverlayValidationMenuBinding.inflate(layoutInflater).apply {
            btnHelp.visibility = View.GONE
        }
        return binding.root
    }

    override fun onCreateOverlayView(): View = selector

    override fun onStart() {
        super.onStart()
        val area = initialArea?.takeUnless(Rect::isEmpty) ?: Rect(0, 0, 128, 128)
        selector.setSelection(Rect(area), Rect(0, 0, 1, 1))
    }

    override fun onMenuItemClicked(viewId: Int) {
        when (viewId) {
            R.id.btn_confirm -> {
                onAreaSelected(selector.getSelection())
                back()
            }
            R.id.btn_cancel -> back()
        }
    }
}
