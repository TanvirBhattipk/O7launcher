package com.oneuiclone.home.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import com.oneuiclone.home.device.DeviceProfile

/**
 * The Apps screen's scroll thumb: a thin pill on the right edge drawn like the search bar (the same blurred glass,
 * tint and thin border). It shows while the list scrolls, fades out a moment after it stops, and can be grabbed and
 * dragged to scroll quickly. Put it over the list's right edge; it only takes touches that land on the thumb.
 */
@SuppressLint("ViewConstructor")
class DrawerScrollbar(context: Context, private val dp: DeviceProfile, private val scroll: ScrollView) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp.dp(1f) }
    private val rect = RectF()
    private val thumbWidth = dp.dp(8f)
    private val thumbMin = dp.dp(56f)
    private val rightGap = dp.dp(4f)

    /** The blurred wallpaper behind the thumb (as the search bar has it); null for none. */
    var glass: WidgetGlass? = null
        set(v) {
            if (field === v) return
            if (isAttachedToWindow) { field?.views?.remove(this); v?.views?.add(this) }
            field = v; applyColors(); invalidate()
        }
    /** How see-through the glass tint is (0..1). */
    var glassTransparency = 0f
        set(v) { field = v; applyColors(); invalidate() }

    private val glassPaint = GlassPaint()
    private val toWindow = Matrix()
    private val drawnAt = Matrix()
    private val moved = ViewTreeObserver.OnPreDrawListener {
        if (glass != null && alpha > 0f) {
            toWindow.reset(); transformMatrixToGlobal(toWindow)
            if (toWindow != drawnAt) invalidate()
        }
        true
    }

    private var dragging = false
    private var grab = 0f
    private val hide = Runnable { animate().alpha(0f).setDuration(250L).start() }

    init {
        alpha = 0f
        applyColors()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass?.views?.add(this)
        viewTreeObserver.addOnPreDrawListener(moved)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        viewTreeObserver.removeOnPreDrawListener(moved)
        glass?.views?.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyColors(); invalidate()
    }

    private fun applyColors() {
        val c = SearchColors.of(context)
        fill.color = if (glass != null) searchGlassFill(context, glassTransparency) else c.homePill
        border.color = c.stroke
    }

    private fun content() = scroll.getChildAt(0)?.height ?: 0
    private fun range() = (content() - scroll.height).coerceAtLeast(0)
    private fun thumbHeight(): Float {
        val c = content()
        if (c <= 0) return 0f
        return (height.toFloat() * scroll.height / c).coerceIn(thumbMin, height.toFloat())
    }
    private fun thumbTop(): Float {
        val r = range()
        return if (r == 0) 0f else (height - thumbHeight()) * scroll.scrollY.coerceIn(0, r) / r
    }

    /** The list scrolled: show the thumb, and hide it again a moment after it stops. */
    fun onScrolled() {
        if (range() == 0) return
        removeCallbacks(hide)
        animate().cancel()
        alpha = 1f
        invalidate()
        if (!dragging) postDelayed(hide, 900L)
    }

    override fun onDraw(canvas: Canvas) {
        if (range() == 0) return
        val h = thumbHeight()
        val top = thumbTop()
        val right = width - rightGap
        rect.set(right - thumbWidth, top, right, top + h)
        val r = thumbWidth / 2f
        drawnAt.reset(); transformMatrixToGlobal(drawnAt)
        if (glassPaint.aim(glass, drawnAt)) canvas.drawRoundRect(rect, r, r, glassPaint.paint)
        canvas.drawRoundRect(rect, r, r, fill)
        canvas.drawRoundRect(rect, r, r, border)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (alpha < 0.3f || range() == 0) return false
                val top = thumbTop()
                val slop = dp.dp(14f)
                if (e.y < top - slop || e.y > top + thumbHeight() + slop) return false
                dragging = true
                grab = e.y - top
                removeCallbacks(hide)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val free = (height - thumbHeight()).coerceAtLeast(1f)
                val frac = ((e.y - grab) / free).coerceIn(0f, 1f)
                scroll.scrollTo(0, (frac * range()).toInt())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                postDelayed(hide, 900L)
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
