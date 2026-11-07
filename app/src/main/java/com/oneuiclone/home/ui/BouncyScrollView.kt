package com.oneuiclone.home.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ScrollView
import com.oneuiclone.home.device.DeviceProfile
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The Apps screen's list with a Pixel-launcher style bounce at its two ends only: pulling or flinging past the first
 * icons (top) or the last icons (bottom) makes the icons nearest that end give way and spring back past rest, the
 * effect fading out towards the middle of the screen. Scrolling through the middle of the list does nothing.
 * One spring drives every icon, so it stays cheap.
 */
@SuppressLint("ViewConstructor")
class BouncyScrollView(context: Context, private val dp: DeviceProfile) : ScrollView(context) {

    private val maxOffset = dp.dp(34f)
    private val stiffness = 190f
    private val damping = 2f * 0.42f * sqrt(stiffness)         // damping ratio 0.42: a clear overshoot, then rest

    /** -1: the bounce is at the start of the list, 1: at its end. */
    private var edge = -1
    private var running = false

    private var x = 0f                                          // the spring's offset, px
    private var v = 0f                                          // and its velocity, px/s
    private var last = 0L

    private val tick = Runnable { step() }

    private fun kick(delta: Float, atEnd: Boolean) {
        if (delta == 0f) return
        edge = if (atEnd) 1 else -1
        x = (x + delta).coerceIn(-maxOffset, maxOffset)
        v += delta * 8f
        start()
    }

    private fun start() {
        if (running) return
        running = true
        last = AnimationUtils.currentAnimationTimeMillis()
        postOnAnimation(tick)
    }

    private fun step() {
        val now = AnimationUtils.currentAnimationTimeMillis()
        val dt = ((now - last).coerceIn(1L, 32L)) / 1000f
        last = now
        val a = -stiffness * x - damping * v
        v += a * dt
        x += v * dt
        if (abs(x) < 0.3f && abs(v) < 8f) {
            x = 0f; v = 0f; running = false
            applyOffsets()
            return
        }
        applyOffsets()
        postOnAnimation(tick)
    }

    /** Offsets every icon in view: most the ones at the end that bounced, fading out towards the middle. */
    private fun applyOffsets() {
        val grid = getChildAt(0) as? ViewGroup ?: return
        val h = height.coerceAtLeast(1)
        for (i in 0 until grid.childCount) {
            val c = grid.getChildAt(i)
            val top = c.top + grid.top - scrollY
            if (x == 0f || top > h || top + c.height < 0) {
                if (c.translationY != 0f) c.translationY = 0f
                continue
            }
            val pos = ((top + c.height / 2f) / h).coerceIn(0f, 1f)
            val w = if (edge < 0) 1f - pos else pos
            c.translationY = x * 1.3f * w * w
        }
    }

    override fun overScrollBy(
        deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int, scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean,
    ): Boolean {
        val r = super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
        // how far this step would carry past an end (a drag, or a fling arriving): the icons give way that way
        val target = scrollY + deltaY
        val excess = when {
            target < 0 -> target
            target > scrollRangeY -> target - scrollRangeY
            else -> 0
        }
        if (excess != 0) kick(-excess * 0.6f, excess > 0)
        return r
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick); running = false
        super.onDetachedFromWindow()
    }
}
