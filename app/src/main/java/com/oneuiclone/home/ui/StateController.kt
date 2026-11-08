package com.oneuiclone.home.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import com.oneuiclone.home.motion.Easing
import kotlin.math.abs
import kotlin.math.max

/**
 * HOME ⇄ APPS. The Apps screen slides in from the bottom while it fades in, and slides back out to the bottom
 * while it fades out (a swipe up when it's let go, a swipe down, the back gesture). Search asks for a [goTo] with a
 * duration instead: that only crossfades Home into the Apps screen's blurred background (and back), so the search
 * page can fade in over it.
 */
class StateController(
    private val home: View,
    private val apps: View,
    private val background: BackgroundController,
    private val transitionY: () -> Float,
) {
    enum class State { HOME, APPS }

    var state = State.HOME; private set
    var onStateChanged: ((State) -> Unit)? = null
    var onTransitionStart: ((toApps: Boolean) -> Unit)? = null

    /** A timed crossfade (search only). */
    private var anim: ValueAnimator? = null
    /** How much of the Apps screen is showing: 0 = Home, 1 = Apps (moves only during a crossfade). */
    private var a = 0f
    /** Progress of a swipe, 0…1: it only decides, on release, whether the swipe goes through. */
    private var f = 0f
    private var toApps = true
    private var dragging = false
    val isTransitioning get() = anim?.isRunning == true || dragging

    /** A swipe starts: nothing moves with the finger. */
    fun begin(towardApps: Boolean) {
        anim?.cancel()
        toApps = towardApps
        dragging = true
        f = 0f
        onTransitionStart?.invoke(towardApps)
    }

    fun drag(progress: Float) {
        f = progress.coerceIn(0f, 1f)
    }

    /**
     * The swipe is let go: it goes through when it was a fling or went far enough (a fling back cancels it), and
     * then the screens switch at once.
     * @param velocity Samsung-px/ms, positive toward the target screen.
     */
    fun release(velocity: Float) {
        dragging = false
        val isFling = abs(velocity) > 1f
        val reverseFling = isFling && velocity < 0f
        val commit = (isFling || f > 0.4f) && !reverseFling
        f = 0f
        if (commit) slide(if (toApps) State.APPS else State.HOME)
    }

    /** [duration] 0 or less: switch at once; otherwise crossfade over that long. */
    fun goTo(target: State, duration: Long = -1L) {
        if (target == state && anim?.isRunning != true && !dragging) return
        if (duration <= 0L) {
            if (anim?.isRunning == true && (target == State.APPS) == toApps) return
            slide(target)
            return
        }
        if (anim?.isRunning == true && (target == State.APPS) == toApps) return
        fade(target, duration)
    }

    fun setImmediate(target: State) {
        anim?.cancel()
        toApps = target == State.APPS
        settle(target)
    }

    /** The Apps screen comes up from the bottom with a fade, or goes back down with one. */
    private fun slide(target: State) {
        anim?.cancel()
        toApps = target == State.APPS
        onTransitionStart?.invoke(toApps)
        apps.visibility = View.VISIBLE
        home.visibility = View.VISIBLE
        home.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        apps.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val end = if (toApps) 1f else 0f
        anim = ValueAnimator.ofFloat(a, end).apply {
            this.duration = max(120L, (SLIDE_MS * abs(end - a)).toLong())
            interpolator = Easing.CUBIC_OUT
            addUpdateListener {
                a = it.animatedValue as Float
                val ty = max(transitionY(), apps.height * 0.14f)
                apps.alpha = a
                apps.translationY = ty * (1f - a)
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

    private fun fade(target: State, duration: Long) {
        anim?.cancel()
        apps.translationY = 0f
        toApps = target == State.APPS
        onTransitionStart?.invoke(toApps)
        apps.visibility = View.VISIBLE
        home.visibility = View.VISIBLE
        home.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        apps.setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
        setScreen(apps, showApps)
        setScreen(home, !showApps)
        a = if (showApps) 1f else 0f
        background.set(BackgroundController.Kind.APPS, a)
        f = 0f
        dragging = false
        state = target
        onStateChanged?.invoke(state)
    }

    private companion object { const val SLIDE_MS = 320L }

    private fun setScreen(v: View, shown: Boolean) {
        v.scaleX = 1f; v.scaleY = 1f; v.translationY = 0f
        v.alpha = if (shown) 1f else 0f
        v.visibility = if (shown) View.VISIBLE else View.INVISIBLE
    }
}
