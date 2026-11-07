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
 * The Apps screen's list with a Pixel-launcher style bounce: while it scrolls the icons trail a little behind
 * (the lower ones more, so the rows ripple), and when it stops or hits an end they spring back past rest and settle.
 * One spring drives every icon, so it stays cheap.
 */
@SuppressLint("ViewConstructor")
class BouncyScrollView(context: Context, private val dp: DeviceProfile) : ScrollView(context) {

    private val maxOffset = dp.dp(26f)
    private val stiffness = 190f
    private val damping = 2f * 0.42f * sqrt(stiffness)         // damping ratio 0.42: a clear overshoot, then rest

    private var x = 0f                                          // the spring's offset, px
    private var v = 0f                                          // and its velocity, px/s
    private var last = 0L
    private var running = false

    private val tick = Runnable { step() }

    private fun kick(delta: Float) {
        if (delta == 0f) return
        x = (x + delta).coerceIn(-maxOffset, maxOffset)
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

    /** Offsets every icon in view: more the lower it is on screen; the rest are put back at zero. */
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
            c.translationY = x * (0.4f + 0.9f * pos)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // scrolling down (content moves up): the icons trail below where they belong
        kick((t - oldt) * 0.8f)
    }

    override fun overScrollBy(
        deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int, scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean,
    ): Boolean {
        val r = super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
        val atTop = scrollY <= 0 && deltaY < 0
        val atBottom = scrollY >= scrollRangeY && deltaY > 0
        // pulling past an end: the icons give way in the direction of the pull
        if (atTop || atBottom) kick(-deltaY * 0.8f)
        return r
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick); running = false
        super.onDetachedFromWindow()
    }
}
