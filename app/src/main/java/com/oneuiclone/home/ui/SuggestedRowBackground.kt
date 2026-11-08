package com.oneuiclone.home.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.oneuiclone.home.device.DeviceProfile

/**
 * The thin line under the Apps screen's first row of suggested apps (Pixel launcher), separating it from All apps.
 * It spans the row's cell, so it sits at the cell's bottom edge, halfway between the suggested icons' labels and
 * the first row of all apps, and like the search bar it ends where the outer icons do.
 */
class SuggestedRowBackground(context: Context, private val dp: DeviceProfile) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF
        strokeWidth = dp.dp(1f)
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val side = (dp.searchBarSide - dp.appsSidePadding).coerceAtLeast(0).toFloat()
        val y = height - paint.strokeWidth / 2f
        canvas.drawLine(side, y, width - side, y, paint)
    }
}
