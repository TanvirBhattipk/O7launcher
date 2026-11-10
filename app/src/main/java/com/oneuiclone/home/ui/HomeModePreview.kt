package com.oneuiclone.home.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

/**
 * The small phone drawings of Settings' "Home screen" choice: [classic] (every app on Home, a dock below) or
 * "With App drawer" (Home with the Apps screen: tabs, rows of apps, a search pill). About 1 : 1.75, drawn to fit.
 */
class HomeModePreview(context: Context, private val classic: Boolean) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val box = RectF()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, (w * 1.75f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        clip.reset(); clip.addRoundRect(0f, 0f, w, h, w * 0.1f, w * 0.1f, Path.Direction.CW)
        c.save(); c.clipPath(clip)
        if (classic) drawClassic(c, w, h) else drawDrawer(c, w, h)
        c.restore()
    }

    private fun icon(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color
        box.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        c.drawRoundRect(box, size * 0.3f, size * 0.3f, p)
    }

    private fun drawClassic(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF4A66F9.toInt(), 0xFF3FA6FF.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.color = Color.WHITE; p.textSize = w * 0.135f; p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        c.drawText("08:36", w * 0.08f, h * 0.17f, p)
        val size = w * 0.148f
        val xs = floatArrayOf(0.145f, 0.38f, 0.616f, 0.848f)
        fun row(y: Float, colors: IntArray) = colors.forEachIndexed { i, col -> icon(c, w * xs[i], h * y, size, col) }
        row(0.556f, intArrayOf(GREEN, SALMON, Color.WHITE, BLUE))
        row(0.71f, intArrayOf(YELLOW, Color.WHITE, GREEN, PINK))
        row(0.906f, intArrayOf(Color.WHITE, BLUE, YELLOW, GREEN))
        for (i in -1..1) {                                        // page dots: the first page is the bright one
            p.color = if (i == -1) Color.WHITE else 0x80FFFFFF.toInt()
            c.drawCircle(w * (0.5f + i * 0.037f), h * 0.808f, w * 0.012f, p)
        }
    }

    private fun drawDrawer(c: Canvas, w: Float, h: Float) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = 0xFFF4F5F7.toInt()
        c.drawRect(0f, 0f, w, h, p)
        val xs = floatArrayOf(0.16f, 0.39f, 0.62f, 0.846f)
        p.color = 0xFFE4E5EA.toInt()                              // the tabs
        for (x in xs) { box.set(w * (x - 0.068f), h * 0.08f, w * (x + 0.068f), h * 0.11f); c.drawRoundRect(box, w * 0.012f, w * 0.012f, p) }
        p.color = 0xFFCDCFD6.toInt()                              // under the first one
        box.set(w * (xs[0] - 0.068f), h * 0.118f, w * (xs[0] + 0.068f), h * 0.123f); c.drawRect(box, p)
        val size = w * 0.144f
        fun row(y: Float, colors: IntArray) = colors.forEachIndexed { i, col -> icon(c, w * xs[i], h * y, size, col) }
        row(0.213f, intArrayOf(YELLOW, GREEN, BLUE, PINK))
        row(0.343f, intArrayOf(GREEN, YELLOW, PINK, BLUE))
        p.color = 0xFFE4E5EA.toInt(); p.strokeWidth = h * 0.004f; c.drawLine(w * 0.09f, h * 0.43f, w * 0.91f, h * 0.43f, p)
        row(0.52f, intArrayOf(BLUE, PINK, YELLOW, BLUE))
        row(0.65f, intArrayOf(GREEN, BLUE, YELLOW, PINK))
        row(0.78f, intArrayOf(YELLOW, GREEN, PINK, BLUE))
        p.color = 0xFFE5E6EB.toInt()                              // the search pill
        box.set(w * 0.09f, h * 0.88f, w * 0.91f, h * 0.954f); c.drawRoundRect(box, h * 0.037f, h * 0.037f, p)
    }

    private companion object {
        val GREEN = 0xFF6BEFC3.toInt(); val SALMON = 0xFFFF9F7A.toInt(); val BLUE = 0xFF7DD3FF.toInt()
        val YELLOW = 0xFFFFC96B.toInt(); val PINK = 0xFFFF8099.toInt()
    }
}
