package com.oneuiclone.home.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import com.oneuiclone.home.device.DeviceProfile
import kotlin.math.roundToInt

/**
 * The row of apps on the search page: up to [COLS] icons on a subtle rounded panel as wide as the search bar, with no
 * labels. The same padding all round: the first and last icons are [pad] from the panel's sides, and the icons are
 * [pad] from its top and its bottom. With fewer than [COLS] apps the group is centred. The icons are 20% smaller than
 * Home's; each sits in a square [hl] bigger than it all round, which is also the highlight of the first result.
 */
class RecentsRow(context: Context, private val dp: DeviceProfile) : ViewGroup(context) {

    val pad = dp.dpi(14f)
    /** How far the highlight square reaches past the icon. */
    private val hl = dp.dpi(7f)
    /** The size of an icon in the row (even, so it sits on whole pixels). */
    val iconPx = (dp.iconSizePx * ICON_SCALE).roundToInt().let { if (it % 2 == 1) it + 1 else it }
    private val slot = iconPx + 2 * hl

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
        for (i in 0 until childCount) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(slot, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(slot, MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, iconPx + 2 * pad)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val step = step(w)
        val used = iconPx + (childCount - 1).coerceAtLeast(0) * step
        val start = (w - used) / 2
        for (i in 0 until childCount) {
            val left = start + i * step - hl
            getChildAt(i).layout(left, pad - hl, left + slot, pad - hl + slot)
        }
    }

    companion object {
        const val COLS = 4
        const val ICON_SCALE = 0.8f
    }
}
