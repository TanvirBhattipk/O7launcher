package com.oneuiclone.home.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.ScrollView
import com.oneuiclone.home.device.DeviceProfile

/**
 * The Apps screen's list. At the bottom it gives way with Android's own stretch overscroll (EdgeEffect.TYPE_STRETCH,
 * on Android 12 and up: the whole list stretches under the finger and relaxes when it lets go; a fling that hits the
 * end stretches it too). There is none at the top: pulling down there is the swipe that closes the Apps screen
 * (Launcher handles it), so the overscroll is only on while the list has last moved toward its bottom.
 */
@SuppressLint("ViewConstructor")
class DrawerScrollView(context: Context, private val dp: DeviceProfile) : ScrollView(context) {

    var onPullToClose: (() -> Unit)? = null

    /** How far this touch has pulled the list down past its top. */
    private var topPull = 0f
    private var tracker: VelocityTracker? = null

    init {
        overScrollMode = OVER_SCROLL_NEVER                         // at the top to begin with
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t != oldt) overScrollMode = if (t > oldt) OVER_SCROLL_ALWAYS else OVER_SCROLL_NEVER
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                topPull = 0f
                if (scrollY <= 0) overScrollMode = OVER_SCROLL_NEVER
                tracker?.recycle(); tracker = VelocityTracker.obtain()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                tracker?.computeCurrentVelocity(1000)
                val vy = tracker?.yVelocity ?: 0f
                tracker?.recycle(); tracker = null
                val pulled = topPull
                topPull = 0f
                val farEnough = pulled > dp.dp(110f)
                val flicked = pulled > dp.dp(24f) && vy > dp.dp(1100f)
                if (ev.actionMasked == MotionEvent.ACTION_UP && scrollY == 0 && (farEnough || flicked)) {
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
