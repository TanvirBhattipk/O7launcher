package com.oneuiclone.home.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.oneuiclone.home.device.DeviceProfile

/**
 * The thin line under the Apps screen's first row of suggested apps (Pixel launcher), separating it from All apps.
 * The view is the suggested row's cell; the line is drawn just below it, in the middle of the gap the list leaves
 * there ([GAP_DP] tall, set in Launcher.bindApps), and like the search bar it ends where the outer icons do.
 */
class SuggestedRowBackground(context: Context, private val dp: DeviceProfile) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF
        strokeWidth = dp.dp(1f)
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val side = (dp.searchBarSide - dp.appsSidePadding).coerceAtLeast(0).toFloat()
        val y = height + dp.dp(GAP_DP) / 2f                     // outside the cell: the grid doesn't clip its children
        canvas.drawLine(side, y, width - side, y, paint)
    }

    companion object {
        const val GAP_DP = 40f
    }
}
