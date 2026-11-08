package com.oneuiclone.home.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.oneuiclone.home.motion.Easing
import kotlin.math.abs
import kotlin.math.max

/**
 * HOME ⇄ APPS screen transition, ported from One UI Home (spec §3):
 *  - the screen being shown:  scale 0.94→1, alpha = ENTER(f), translationY ±86dp → 0
 *  - the screen being hidden: scale 1→0.94, alpha = 1−EXIT(f), translationY 0 → ∓86dp·f
 *  - release: fling if |v| > 1 px/ms, commit if fling or f > 0.4, reverse fling cancels.
 *
 * How it settles is the Pixel launcher's spring-physics drawer (androidx SpringAnimation): after a swipe or a tap
 * the screen takes the finger's release velocity, overshoots a little past its resting place and rocks back, so a
 * hard swipe flies in and a gentle one drifts in. A transition asked for with a duration (the search bar's morph
 * has to stay in step with it) is still timed, with the cubic curve.
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

    private var anim: ValueAnimator? = null
    private var spring: SpringAnimation? = null
    /** Progress of the current transition toward [target]. */
    private var f = 0f
    private var toApps = true
    private fun running() = anim?.isRunning == true || spring?.isRunning == true
    val isTransitioning get() = running() || dragging
    private var dragging = false

    private fun stopRunning() {
        anim?.cancel()
        spring?.cancel()
    }

    fun begin(towardApps: Boolean) {
        stopRunning()
        toApps = towardApps
        dragging = true
        apps.visibility = View.VISIBLE
        home.visibility = View.VISIBLE
        // composite cached layers while scaling/fading (no per-frame redraw of every icon)
        home.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        apps.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        onTransitionStart?.invoke(towardApps)
    }

    /** Raw finger progress (no interpolation while dragging, like gotoScreenWithAnimation(ongoing=false)). */
    fun drag(progress: Float) {
        apply(progress.coerceIn(0f, 1f))
    }

    /**
     * @param velocity Samsung-px/ms, positive toward the target screen.
     */
    fun release(velocity: Float) {
        dragging = false
        val isFling = abs(velocity) > 1f
        val reverseFling = isFling && velocity < 0f
        val commit = (isFling || f > 0.4f) && !reverseFling
        // the finger's speed (px/ms of the 800 px swipe) as progress per second, carried into the spring
        val v0 = (velocity * 1000f / SWIPE_PX).coerceIn(-MAX_VELOCITY, MAX_VELOCITY)
        springTo(if (commit) 1f else 0f, v0)
    }

    /** [duration] -1: settle on the spring; otherwise a timed run of exactly that long. */
    fun goTo(target: State, duration: Long = -1L) {
        if (target == state && !running() && !dragging) return
        if (running() && (target == State.APPS) == toApps) return
        if (running()) {
            // turned around mid-way: run the same transition back from where it is (no restart, no blink)
            if (duration < 0) springTo(0f, 0f)
            else animateTo(0f, max(100L, (duration * f).toLong()), Easing.CUBIC_OUT)
            return
        }
        begin(target == State.APPS)
        dragging = false
        f = 0f
        if (duration < 0) springTo(1f, 0f) else animateTo(1f, duration, Easing.CUBIC_OUT)
    }

    fun setImmediate(target: State) {
        stopRunning()
        toApps = target == State.APPS
        apply(1f)
        finish(true)
    }

    /** Settle the transition's progress on [end] (0 or 1) with a spring that starts at [velocity] (progress per second). */
    private fun springTo(end: Float, velocity: Float) {
        stopRunning()
        spring = SpringAnimation(FloatValueHolder(f)).apply {
            setSpring(SpringForce(end).setStiffness(STIFFNESS).setDampingRatio(DAMPING))
            setStartVelocity(velocity)
            setMinimumVisibleChange(0.002f)
            addUpdateListener { _, value, _ -> apply(value) }
            addEndListener { _, canceled, _, _ -> if (!canceled) finish(end >= 1f) }
            start()
        }
    }

    private fun animateTo(end: Float, duration: Long, interp: android.view.animation.Interpolator) {
        stopRunning()
        val start = f
        anim = ValueAnimator.ofFloat(start, end).apply {
            this.duration = duration
            interpolator = interp
            addUpdateListener { apply(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) finish(end >= 1f) }
            })
            start()
        }
    }

    private fun finish(committed: Boolean) {
        val newState = if (committed) (if (toApps) State.APPS else State.HOME) else (if (toApps) State.HOME else State.APPS)
        val showApps = newState == State.APPS
        home.setLayerType(View.LAYER_TYPE_NONE, null)
        apps.setLayerType(View.LAYER_TYPE_NONE, null)
        // settle exactly
        setScreen(apps, showApps)
        setScreen(home, !showApps)
        background.set(BackgroundController.Kind.APPS, if (showApps) 1f else 0f)
        f = 0f
        if (newState != state) {
            state = newState
        }
        onStateChanged?.invoke(state)
    }

    private fun setScreen(v: View, shown: Boolean) {
        v.scaleX = 1f; v.scaleY = 1f; v.translationY = 0f
        v.alpha = if (shown) 1f else 0f
        v.visibility = if (shown) View.VISIBLE else View.INVISIBLE
    }

    private fun apply(progress: Float) {
        f = progress
        val ty = transitionY()
        val showing = if (toApps) apps else home
        val hiding = if (toApps) home else apps
        // showing screen (direction-aware, HomescreenViewModel / AppscreenViewModel)
        val enter = Easing.ENTER.getInterpolation(f.coerceIn(0f, 1f))
        // past its resting place (the spring's overshoot) the screen swells and rises a touch more, then rocks back
        val over = (f - 1f).coerceAtLeast(0f)
        val sIn = 0.94f + 0.06f * enter + OVERSHOOT_SCALE * over
        showing.scaleX = sIn; showing.scaleY = sIn
        showing.alpha = enter
        val base = ty * (1f - f)                       // already carries it a little further once f is past 1
        val extra = ty * OVERSHOOT_RISE * over
        showing.translationY = if (toApps) base - extra else -base + extra
        // hiding screen
        val exit = Easing.EXIT.getInterpolation(f.coerceIn(0f, 1f))
        val sOut = 1f - 0.06f * exit
        hiding.scaleX = sOut; hiding.scaleY = sOut
        hiding.alpha = 1f - exit
        hiding.translationY = if (toApps) -ty * f else ty * f
        background.set(BackgroundController.Kind.APPS, (if (toApps) f else 1f - f).coerceIn(0f, 1f))
    }

    private companion object {
        /** Samsung px of the Home→Apps swipe, as [release]'s velocity is measured. */
        const val SWIPE_PX = 800f
        const val MAX_VELOCITY = 8f
        /** Spring of the settle: soft and a little loose, like the Pixel launcher's drawer. */
        const val STIFFNESS = 380f
        const val DAMPING = 0.68f
        /** How much the screen swells / rises per unit of overshoot (the overshoot itself is only a few %). */
        const val OVERSHOOT_SCALE = 0.5f
        const val OVERSHOOT_RISE = 2.5f
    }
}
