package com.oneuiclone.home.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.oneuiclone.home.motion.Easing
import kotlin.math.abs
import kotlin.math.max

/**
 * HOME ⇄ APPS, the way Pixel's drawer opens (with no sheet behind it): the wallpaper blurs and Home's icons blur and
 * fade away first, while the drawer's own content (the search bar, the suggested apps, the list) rises from the
 * bottom edge, fading in as it leaves it. The drawer follows the finger exactly (one pixel for one pixel, never
 * ahead of it); when the finger lets go the rest of the way is finished from where it is. Back and swipe down run it
 * the other way. Search asks for a [goTo] with a
 * duration instead: that only crossfades Home into the Apps screen's background (and back), so the search page can
 * fade in over it.
 */
class StateController(
    private val home: View,
    private val apps: View,
    private val background: BackgroundController,
    @Suppress("UNUSED_PARAMETER") private val transitionY: () -> Float,
) {
    enum class State { HOME, APPS }

    var state = State.HOME; private set
    var onStateChanged: ((State) -> Unit)? = null
    var onTransitionStart: ((toApps: Boolean) -> Unit)? = null

    private var anim: ValueAnimator? = null
    /** How far the Apps screen is open: 0 = Home, 1 = Apps. */
    private var a = 0f
    /** Progress of the swipe in hand, 0…1 toward its target. */
    private var f = 0f
    private var toApps = true
    private var dragging = false
    val isTransitioning get() = anim?.isRunning == true || dragging

    private fun prepare() {
        apps.visibility = View.VISIBLE
        home.visibility = View.VISIBLE
        home.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        apps.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    /** A swipe starts. */
    fun begin(towardApps: Boolean) {
        anim?.cancel()
        toApps = towardApps
        dragging = true
        f = 0f
        onTransitionStart?.invoke(towardApps)
        prepare()
    }

    /**
     * The finger moves: [progress] (0…1 of the swipe's distance) is what decides on release, while the screens follow
     * the finger itself, [distancePx] from where it went down, exactly.
     */
    fun drag(progress: Float, distancePx: Float) {
        f = progress.coerceIn(0f, 1f)
        val shown = (distancePx / max(1f, apps.height.toFloat())).coerceIn(0f, 1f)
        frame(if (toApps) shown else 1f - shown)
    }

    /**
     * The swipe is let go: it goes through when it was a fling or went far enough (a fling back cancels it); either
     * way the rest is animated from where it is.
     * @param velocity Samsung-px/ms, positive toward the target screen.
     */
    fun release(velocity: Float) {
        dragging = false
        // it takes a longer swipe than before and speed doesn't count: a quick flick that goes only a short way falls back
        val commit = f > COMMIT_AT && velocity > -1f
        f = 0f
        run(if (commit) (if (toApps) State.APPS else State.HOME) else state)
    }

    /** [duration] 0 or less: the drawer's own open/close; otherwise a crossfade over that long (search). */
    fun goTo(target: State, duration: Long = -1L) {
        if (target == state && anim?.isRunning != true && !dragging) return
        if (anim?.isRunning == true && (target == State.APPS) == toApps) return
        onTransitionStart?.invoke(target == State.APPS)
        if (duration <= 0L) run(target) else fade(target, duration)
    }

    fun setImmediate(target: State) {
        anim?.cancel()
        toApps = target == State.APPS
        settle(target)
    }

    private fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** One frame of the open/close at [p] (0 Home … 1 Apps). */
    private fun frame(p: Float) {
        a = p
        // the wallpaper blurs first (all of it by 40% of the way) and Home's icons blur, shrink a little and fade with it
        val blurP = smooth(p / 0.4f)
        background.set(BackgroundController.Kind.APPS, blurP)
        home.alpha = 1f - smooth(p / 0.35f)
        val s = 1f - 0.04f * blurP
        home.scaleX = s; home.scaleY = s
        homeBlur(blurP)
        // the drawer comes up from the bottom edge, fading in as it leaves it (nothing behind it: no sheet)
        apps.translationY = apps.height * (1f - p)
        apps.alpha = smooth(p / 0.3f)
    }

    private fun homeBlur(x: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        val r = 28f * x
        home.setRenderEffect(if (r > 0.5f) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) else null)
    }

    /** Finishes the open/close at [target] from wherever it is. */
    private fun run(target: State) {
        anim?.cancel()
        toApps = target == State.APPS
        prepare()
        val end = if (toApps) 1f else 0f
        anim = ValueAnimator.ofFloat(a, end).apply {
            duration = max(140L, (OPEN_MS * abs(end - a)).toLong())
            interpolator = Easing.CUBIC_OUT
            addUpdateListener { frame(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) settle(target) }
            })
            start()
        }
    }

    /** Search: Home and the Apps screen only crossfade (the search page does the moving). */
    private fun fade(target: State, duration: Long) {
        anim?.cancel()
        toApps = target == State.APPS
        prepare()
        apps.translationY = 0f
        home.scaleX = 1f; home.scaleY = 1f
        homeBlur(0f)
        val end = if (toApps) 1f else 0f
        anim = ValueAnimator.ofFloat(a, end).apply {
            this.duration = max(100L, (duration * abs(end - a)).toLong())
            interpolator = Easing.CUBIC_OUT
            addUpdateListener {
                a = it.animatedValue as Float
                apps.alpha = a
                home.alpha = 1f - a
                background.set(BackgroundController.Kind.APPS, a)
            }
            addListener(object : AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) settle(target) }
            })
            start()
        }
    }

    /** Shows exactly [target]'s screen. */
    private fun settle(target: State) {
        val showApps = target == State.APPS
        home.setLayerType(View.LAYER_TYPE_NONE, null)
        apps.setLayerType(View.LAYER_TYPE_NONE, null)
        homeBlur(0f)
        setScreen(apps, showApps)
        setScreen(home, !showApps)
        a = if (showApps) 1f else 0f
        background.set(BackgroundController.Kind.APPS, a)
        f = 0f
        dragging = false
        state = target
        onStateChanged?.invoke(state)
    }

    private fun setScreen(v: View, shown: Boolean) {
        v.scaleX = 1f; v.scaleY = 1f; v.translationY = 0f
        v.alpha = if (shown) 1f else 0f
        v.visibility = if (shown) View.VISIBLE else View.INVISIBLE
    }

    private companion object {
        const val OPEN_MS = 420L
        /** How far a swipe has to go (0…1 of its distance, about 185dp) to open or close. */
        const val COMMIT_AT = 0.65f
    }
}
