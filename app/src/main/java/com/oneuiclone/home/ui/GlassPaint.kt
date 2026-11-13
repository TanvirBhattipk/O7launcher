package com.oneuiclone.home.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader

/**
 * A paint that fills with [WidgetGlass]: whatever it draws shows the blurred wallpaper that's behind it on screen.
 * Aim it before each draw with the canvas's mapping to the window; [aimLiquid] also bends the glass at the shape's
 * edge (liquid glass).
 */
class GlassPaint {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap: Bitmap? = null
    private var bitmapShader: BitmapShader? = null
    private var refraction: RuntimeShader? = null
    private val local = Matrix()

    /** The glass as a shader in the canvas's coordinates, or null when there's nothing to draw. */
    private fun aimBitmap(glass: WidgetGlass?, toWindow: Matrix): BitmapShader? {
        val b = glass?.bitmap ?: return null
        if (!toWindow.invert(local)) return null
        local.preScale(glass.scale, glass.scale)                         // glass pixels → window → canvas
        var s = bitmapShader
        if (bitmap !== b || s == null) {
            bitmap = b
            s = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            bitmapShader = s
        }
        s.setLocalMatrix(local)
        return s
    }

    /** Points [paint] at [glass] for a canvas mapped to the window by [toWindow]; false when there's nothing to draw. */
    fun aim(glass: WidgetGlass?, toWindow: Matrix): Boolean {
        val s = aimBitmap(glass, toWindow) ?: return false
        if (paint.shader !== s) paint.shader = s
        if (paint.colorFilter != null) paint.colorFilter = null
        return true
    }

    /**
     * As [aim], for a round rect [rect] (corner [radius], in canvas px; [density] px per dp): with liquid glass on, the
     * glass is refracted near the edge and made vivid. Otherwise it is the plain glass.
     */
    fun aimLiquid(canvas: android.graphics.Canvas, glass: WidgetGlass?, toWindow: Matrix, rect: RectF, radius: Float, density: Float): Boolean {
        val s = aimBitmap(glass, toWindow) ?: return false
        if (!LiquidGlass.usable(canvas) || rect.width() <= 0f || rect.height() <= 0f) {
            if (paint.shader !== s) paint.shader = s
            if (paint.colorFilter != null) paint.colorFilter = null
            return true
        }
        val rs = refraction ?: RuntimeShader(LiquidGlass.REFRACT).also { refraction = it }
        rs.setInputShader("content", s)                                  // (after the matrix: the shader is captured here)
        rs.setFloatUniform("center", rect.centerX(), rect.centerY())
        rs.setFloatUniform("halfSize", rect.width() / 2f, rect.height() / 2f)
        rs.setFloatUniform("radius", radius)
        rs.setFloatUniform("refractionHeight", minOf(LiquidGlass.REFRACTION_DP * density, minOf(rect.width(), rect.height()) / 2f))
        rs.setFloatUniform("refractionAmount", -LiquidGlass.AMOUNT_DP * density)
        if (paint.shader !== rs) paint.shader = rs
        paint.colorFilter = LiquidGlass.vibrancy
        return true
    }
}

/**
 * Fills itself with [glass] (the blurred wallpaper behind it on screen) under [fill], as a closed folder's
 * container does with Style › Blur. The glass is fixed to the window, so it redraws whenever the view moves.
 * With a [cornerPx] (the container's corner radius) it is liquid glass: a lens at the edge and a lit rim.
 */
class GlassView(
    context: android.content.Context,
    private val glass: WidgetGlass,
    private val fill: Int,
    private val cornerPx: Float = 0f,
) : android.view.View(context) {
    private val glassPaint = GlassPaint()
    private val fillPaint = Paint().apply { color = fill }
    private val rim = if (cornerPx > 0f) LiquidRim() else null
    private val box = RectF()
    private val toWindow = Matrix()
    private val drawnAt = Matrix()
    private val moved = android.view.ViewTreeObserver.OnPreDrawListener {
        toWindow.reset(); transformMatrixToGlobal(toWindow)
        if (toWindow != drawnAt) invalidate()
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass.views += this
        viewTreeObserver.addOnPreDrawListener(moved)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(moved)
        glass.views -= this
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        drawnAt.reset(); transformMatrixToGlobal(drawnAt)
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        val density = resources.displayMetrics.density
        val ok = if (cornerPx > 0f) glassPaint.aimLiquid(canvas, glass, drawnAt, box, cornerPx, density) else glassPaint.aim(glass, drawnAt)
        if (ok) canvas.drawPaint(glassPaint.paint)
        canvas.drawPaint(fillPaint)
        rim?.draw(canvas, box, cornerPx, LiquidGlass.RIM_DP * density)
    }
}
