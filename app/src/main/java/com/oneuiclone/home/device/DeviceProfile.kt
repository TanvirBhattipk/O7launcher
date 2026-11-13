package com.oneuiclone.home.device

import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Phone layout derived from One UI Home fractions (spec §1.1 / §15). All fractions are of the full
 * window size, exactly as PhoneCellLayoutInfo uses them.
 */
class DeviceProfile(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val insets: Rect,
    val homeCols: Int = 4,
    val homeRows: Int = 6,
    val appsCols: Int = 4,
    val appsRows: Int = 6,
    val iconScale: Float = 1f,
    val labelScale: Float = 1f,
    /** Extra space (dp) above and below Home's icons and the Apps screen's (Home screen settings). */
    val homeTopPadDp: Float = 0f,
    val homeBottomPadDp: Float = 0f,
    val appsTopPadDp: Float = 0f,
    val appsBottomPadDp: Float = 0f,
) {
    fun dp(v: Float) = v * density
    fun dpi(v: Float) = (v * density).roundToInt()

    // ---- workspace (home) ----
    val pageTopPadding = (heightPx * 0.057f).roundToInt()          // page_top_padding_height_ratio
    /**
     * page_bottom_padding_height_ratio, plus the navigation bar inset: Samsung's ratios are of the area above
     * its own gesture hint, so on a Pixel the dock would otherwise sit right on the gesture handle.
     */
    val pageBottomPadding = (heightPx * 0.007f).roundToInt() + insets.bottom
    val indicatorAreaHeight = (heightPx * 0.041f).roundToInt()     // indicator_area_height_ratio
    val hotseatHeight = (heightPx * 0.124f).roundToInt()           // hotseat_bar_height_ratio
    val pageSidePadding = (widthPx * 0.029f).roundToInt()          // wider icon grid (stock: page_left/right_padding_width_ratio 0.038)

    /** Where the grid would end with no extra bottom padding: the page indicator and the dock stay under it. */
    private val gridBottom = heightPx - pageBottomPadding - hotseatHeight - indicatorAreaHeight
    /** A little extra space (dp) kept above and below Home's icon rows, so the 4-column cells are closer to square. */
    private val HOME_GRID_EXTRA_PAD_DP = 16f
    /** Height of the icon grid (the Home top/bottom padding settings shrink it); its position is solved below. */
    private val gridHeight = (gridBottom - dpi(homeBottomPadDp)) - (pageTopPadding + dpi(homeTopPadDp)) - 2 * dpi(HOME_GRID_EXTRA_PAD_DP)
    val cellWidth = (widthPx - 2 * pageSidePadding) / homeCols
    val cellHeight = gridHeight / homeRows

    /** 65.2 % of the cell width (the stock 66 % made 5 % smaller, then 4 % bigger again): the "Icon size" slider's 100 %. Home, the dock and the Apps screen all use it. */
    val iconSizePx = (cellWidth * 0.652f * iconScale).roundToInt().coerceAtMost((cellHeight * 0.62f).roundToInt()).let { if (it % 2 == 1) it + 1 else it }
    /**
     * The icon size of the search page's Recents row: Home's 62.7 % of the cell width made 5 % smaller, and fixed, so
     * changing Home's icon size (or its icon scale) never changes it.
     */
    val recentsIconSizePx = (cellWidth * 0.627f * 0.95f).roundToInt().let { if (it % 2 == 1) it + 1 else it }
    val labelTextSizePx = dp(13f) * 0.855f * labelScale                // 10 % smaller than before (13 dp x 0.95 x 0.9): the "Label size" slider's 100 %; Home and the Apps screen
    val iconLabelGapPx = dp(5.5f)

    /** The dock stays exactly where the stock layout puts it. */
    private val dockDrop = dpi(12f)
    private val hotseatTop = gridBottom + indicatorAreaHeight + dockDrop
    val hotseatRect = Rect(
        pageSidePadding,                                             // the dock's icons sit in the same columns as Home's (stock: 0.036 of the width)
        hotseatTop,
        widthPx - pageSidePadding,
        hotseatTop + hotseatHeight,
    )

    // ---- even vertical spacing ----
    // Three gaps are made equal: status bar -> first icon row, last row's label -> Search pill, Search pill -> dock icons.
    /** HomeSearchButton.pillHeight. */
    private val searchPillHeight = dpi(32f)
    /** An icon + gap + label block as IconView draws it (label height ~ 1.17 x its text size), centred in its cell. */
    private val iconBlockHeight = iconSizePx + iconLabelGapPx + labelTextSizePx * 1.17f
    private val iconBlockOffset = (cellHeight - iconBlockHeight) / 2f
    /** From the grid's top to the bottom of the last row's label. */
    private val lastLabelBottomInGrid = (homeRows - 1) * cellHeight + iconBlockOffset + iconBlockHeight
    /** The dock icons are centred in their cell (no labels). */
    private val dockIconTop = hotseatTop + (hotseatHeight - iconSizePx) / 2f
    /**
     * Grid top G with  (G + offset - statusBar) = (dockIconTop - (G + lastLabelBottom) - pill) / 2  — the status-bar gap
     * equals each Search-pill gap. The Home top padding setting still moves the grid down from there.
     */
    private val gridTop = (((dockIconTop - lastLabelBottomInGrid - searchPillHeight - 2f * iconBlockOffset + 2f * insets.top) / 3f)
        .coerceAtLeast(insets.top.toFloat())).roundToInt() + dpi(homeTopPadDp)
    val workspaceGrid: Rect = Rect(
        pageSidePadding,
        gridTop,
        widthPx - pageSidePadding,
        gridTop + gridHeight,
    )

    /** The Search pill (and the page dots beside it) centred between the last row's label and the dock icons. */
    private val pillTop = workspaceGrid.top + lastLabelBottomInGrid +
        (dockIconTop - (workspaceGrid.top + lastLabelBottomInGrid) - searchPillHeight) / 2f
    private val indicatorTop = (pillTop + searchPillHeight / 2f - indicatorAreaHeight / 2f).roundToInt()
    val indicatorRect = Rect(0, indicatorTop, widthPx, indicatorTop + indicatorAreaHeight)

    // ---- page indicator (page_indicator_dot_size_sw_ratio = slot width) ----
    val indicatorSlotPx = (minOf(widthPx, heightPx) * 0.047f)
    val indicatorDotPx = dp(6f)

    // ---- apps screen ----
    val appsSidePadding = (widthPx * 0.029f).roundToInt()          // same grid width as Home (stock: apps_page_side_padding_width_ratio 0.038)
    /** Side margin of the search bar: its ends line up with the outer edges of the first and last icon columns. */
    val searchBarSide = (appsSidePadding + (((widthPx - 2 * appsSidePadding) / appsCols - iconSizePx) / 2f)).roundToInt()
    val searchBarWidth = widthPx - 2 * searchBarSide
    val searchBarHeight = dpi(52f)                                  // search_bar_wrapper_height
    val searchBarRadius = dp(32f)                                   // search_bar_bg_radius
    val searchBarBottomMargin = (heightPx * 0.022f).roundToInt() + insets.bottom
    /** The Apps search bar sits at the top, a little way under the status bar. */
    val searchBarTop = insets.top + (heightPx * 0.028f).roundToInt()
    /** Where the apps list starts: just under the search bar. */
    val appsListTop = searchBarTop + searchBarHeight + dpi(8f)
    val appsIndicatorHeight = (heightPx * 0.044f).roundToInt()     // apps_page_indicator_height_ratio
    // same grid height as when the bar was at the bottom, so the cells keep their size
    private val appsGridHeight = (heightPx - searchBarBottomMargin - searchBarHeight) - appsIndicatorHeight -
        (heightPx * 0.012f).roundToInt() - (heightPx * 0.088f).roundToInt()
    val appsGrid = Rect(
        appsSidePadding,
        appsListTop + dpi(6f) + dpi(appsTopPadDp),
        widthPx - appsSidePadding,
        appsListTop + dpi(6f) + dpi(appsTopPadDp) + appsGridHeight,
    )
    /** Space the Apps list keeps under its last row. */
    val appsBottomPad = dpi(appsBottomPadDp)
    val appsIndicatorRect = Rect(0, appsGrid.bottom, widthPx, appsGrid.bottom + appsIndicatorHeight)

    // ---- transitions ----
    /** swipe_vertical_transition_y */
    val swipeTransitionY = dp(86f)

    /** Home→Apps progress = drag / 800 px on S26 (FHD+, density 2.8125) ⇒ 284.4 dp. */
    val swipeProgressDistance = dp(284.4f)

    /** One UI Home works in raw px/ms on a 2.8125 density panel; scale its thresholds to this screen. */
    val samsungPxScale = density / 2.8125f

    val cornerRadius = dp(26f)

    /** Inset of a widget inside its cells on every side (WidgetContainer's padding). */
    val widgetPaddingPx = (cellWidth * 0.04f).toInt()
}
