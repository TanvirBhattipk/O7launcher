package com.oneuiclone.home.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.ScrollView
import com.oneuiclone.home.device.DeviceProfile

/**
 * The Apps screen's list. At either end it gives way with Android's own stretch overscroll (EdgeEffect.TYPE_STRETCH,
 * on Android 12 and up: the whole list stretches under the finger and relaxes when it lets go; a fling that hits an
 * end stretches it too). A quick swipe down while the list is at its top (a flick, not a slow pull) closes the Apps
 * screen ([onPullToClose]).
 */
@SuppressLint("ViewConstructor")
class DrawerScrollView(context: Context, private val dp: DeviceProfile) : ScrollView(context) {

    var onPullToClose: (() -> Unit)? = null

    /** How far this touch has pulled the list down past its top. */
    private var topPull = 0f
    private var tracker: VelocityTracker? = null

    init {
        overScrollMode = OVER_SCROLL_ALWAYS
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                topPull = 0f
                tracker?.recycle(); tracker = VelocityTracker.obtain()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                tracker?.computeCurrentVelocity(1000)
                val vy = tracker?.yVelocity ?: 0f
                tracker?.recycle(); tracker = null
                val pulled = topPull
                topPull = 0f
                // only a quick swipe closes it: a slower pull just stretches
                val flicked = pulled > dp.dp(16f) && vy > dp.dp(1000f)
                if (ev.actionMasked == MotionEvent.ACTION_UP && scrollY == 0 && flicked) {
                    post { onPullToClose?.invoke() }
                }
            }
        }
        tracker?.addMovement(ev)
        return super.onTouchEvent(ev)
    }

    override fun overScrollBy(
        deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int, scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean,
    ): Boolean {
        if (isTouchEvent && scrollY <= 0 && deltaY < 0) topPull += -deltaY
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
    }
}
