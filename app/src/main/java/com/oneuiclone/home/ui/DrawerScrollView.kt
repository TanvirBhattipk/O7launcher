package com.oneuiclone.home.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.ScrollView
import com.oneuiclone.home.device.DeviceProfile
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The Apps screen's list, with its own stretch overscroll (drawn here, not by the system's EdgeEffect, so a tap is
 * never swallowed by a stretch that has only just settled):
 *  - at the bottom the list stretches when you pull past the end, and when a fling arrives there;
 *  - at the top it stretches only under a pull of the finger. A fling that comes back up to the top just stops, and a
 *    long swipe down at the top doesn't stretch further: it closes the Apps screen ([onPullToClose]), as does a quick
 *    flick down.
 * The stretch scales the list away from the edge that is pulled, and springs back when the finger lets go.
 */
@SuppressLint("ViewConstructor")
class DrawerScrollView(context: Context, private val dp: DeviceProfile) : ScrollView(context) {

    var onPullToClose: (() -> Unit)? = null

    private val maxStretch = 0.30f
    private val reach = dp.dp(160f)                             // pull (px) at which most of the stretch has been reached
    private val maxPull = dp.dp(220f)
    private val closeAt = dp.dp(96f)                            // a pull this long at the top closes instead of stretching
    private val stiffness = 200f
    private val damping = 2f * 0.8f * sqrt(stiffness)

    /** > 0: pulled down at the top; < 0: pulled up at the bottom (px of finger travel). */
    private var pull = 0f
    private var vel = 0f
    private var topPull = 0f
    private var touching = false
    private var closePending = false
    private var closing = false
    private var running = false
    private var last = 0L
    private var tracker: VelocityTracker? = null
    private val tick = Runnable { step() }

    init {
        overScrollMode = OVER_SCROLL_NEVER                      // the stretch is drawn below
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                closing = false; closePending = false
                touching = true
                topPull = 0f
                tracker?.recycle(); tracker = VelocityTracker.obtain()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                tracker?.computeCurrentVelocity(1000)
                val vy = tracker?.yVelocity ?: 0f
                tracker?.recycle(); tracker = null
                touching = false
                // a quick swipe down while the list is at its top closes it
                val flicked = topPull > dp.dp(16f) && vy > dp.dp(1000f)
                val shut = closing
                topPull = 0f
                if (ev.actionMasked == MotionEvent.ACTION_UP && scrollY == 0 && flicked && !shut) post { onPullToClose?.invoke() }
                if (shut) { closing = false; return true }
                start()                                            // let the stretch spring back
            }
        }
        if (closing) return true
        tracker?.addMovement(ev)
        val handled = super.onTouchEvent(ev)
        if (closePending) {
            // the swipe is long enough: the Apps screen closes, and the list lets go of the finger
            closePending = false
            closing = true
            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
            super.onTouchEvent(cancel)
            cancel.recycle()
            pull = 0f; vel = 0f; applyStretch()
            post { onPullToClose?.invoke() }
            return true
        }
        return handled
    }

    override fun overScrollBy(
        deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int, scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean,
    ): Boolean {
        if (isTouchEvent) {
            if (scrollY <= 0 && (deltaY < 0 || pull > 0f)) {
                if (deltaY < 0) topPull += -deltaY
                pull = max(0f, pull - deltaY)
                if (topPull > closeAt && onPullToClose != null) closePending = true
            } else if (scrollY >= scrollRangeY && (deltaY > 0 || pull < 0f)) {
                pull = min(0f, pull - deltaY)
            }
            pull = pull.coerceIn(-maxPull, maxPull)
            if (pull != 0f) { vel = 0f; applyStretch() }
        } else {
            // a fling arriving at the bottom stretches it (arriving at the top it just stops)
            val over = scrollY + deltaY - scrollRangeY
            if (over > 0 && scrollRangeY > 0) {
                pull = (pull - over * 2.5f).coerceIn(-maxPull, 0f)
                vel = 0f
                applyStretch()
                start()
            }
        }
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
    }

    private fun start() {
        if (running || touching) return
        running = true
        last = android.view.animation.AnimationUtils.currentAnimationTimeMillis()
        postOnAnimation(tick)
    }

    private fun step() {
        if (touching) { running = false; return }
        val now = android.view.animation.AnimationUtils.currentAnimationTimeMillis()
        val dt = ((now - last).coerceIn(1L, 32L)) / 1000f
        last = now
        vel += (-stiffness * pull - damping * vel) * dt
        pull += vel * dt
        if (abs(pull) < 0.4f && abs(vel) < 10f) {
            pull = 0f; vel = 0f; running = false
            applyStretch()
            return
        }
        applyStretch()
        postOnAnimation(tick)
    }

    /** The list scaled away from the edge that is pulled (the top row stays at the top when pulled there). */
    private fun applyStretch() {
        val c = getChildAt(0) ?: return
        if (pull == 0f) { c.scaleY = 1f; return }
        val s = maxStretch * (1f - exp(-abs(pull) / reach))
        c.pivotY = if (pull > 0f) scrollY.toFloat() else (scrollY + height).toFloat()
        c.scaleY = 1f + s
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick); running = false
        super.onDetachedFromWindow()
    }
}
