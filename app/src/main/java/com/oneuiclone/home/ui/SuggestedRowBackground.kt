package com.oneuiclone.home.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.oneuiclone.home.device.DeviceProfile

/**
 * The rounded, see-through panel behind the Apps screen's first row of suggested apps (Pixel launcher). It spans
 * the row and, like the search bar, ends where the outer icons do.
 */
class SuggestedRowBackground(context: Context, private val dp: DeviceProfile) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val side = (dp.searchBarSide - dp.appsSidePadding).coerceAtLeast(0).toFloat()
        val v = dp.dp(4f)
        rect.set(side, v, width - side, height - v)
        val r = dp.dp(28f)
        canvas.drawRoundRect(rect, r, r, paint)
    }
}
