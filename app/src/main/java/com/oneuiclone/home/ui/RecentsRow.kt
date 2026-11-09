package com.oneuiclone.home.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import com.oneuiclone.home.device.DeviceProfile
import kotlin.math.roundToInt

/**
 * The row of apps on the search page: up to [COLS] icons, 20% smaller than Home's, on a subtle rounded panel as wide
 * as the search bar. The same padding all round: the first and last icons are [pad] from the panel's sides, and the
 * icons are [pad] from its top and its bottom.
 */
class RecentsRow(context: Context, private val dp: DeviceProfile) : ViewGroup(context) {

    val pad = dp.dpi(14f)
    /** The size of an icon in the row (even, so it sits on whole pixels). */
    val iconPx = (dp.iconSizePx * ICON_SCALE).roundToInt().let { if (it % 2 == 1) it + 1 else it }
    private val metrics = TextPaint().apply { textSize = dp.labelTextSizePx; typeface = Fonts.ui(context, 400) }.fontMetrics
    private val blockH = (iconPx + dp.iconLabelGapPx + (metrics.descent - metrics.ascent)).roundToInt()

    init {
        clipChildren = false
        clipToPadding = false
        background = GradientDrawable().apply { cornerRadius = dp.dp(22f); setColor(0x1AFFFFFF) }
    }

    /** An icon of the row's size. */
    inner class Icon(context: Context) : IconView(context, dp) {
        override val iconSize get() = iconPx
    }

    /** Distance between the icons' centres: the first icon's left edge and the last one's right edge are [pad] in. */
    private fun step(w: Int) = if (COLS <= 1) w else ((w - 2 * pad - iconPx) / (COLS - 1)).coerceAtLeast(iconPx)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val slot = step(w)
        for (i in 0 until childCount) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(slot, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(blockH, MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, blockH + 2 * pad)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val slot = step(r - l)
        for (i in 0 until childCount) {
            val cx = pad + iconPx / 2f + i * slot
            val left = (cx - slot / 2f).roundToInt()
            getChildAt(i).layout(left, pad, left + slot, pad + blockH)
        }
    }

    companion object {
        const val COLS = 4
        const val ICON_SCALE = 0.8f
    }
}
