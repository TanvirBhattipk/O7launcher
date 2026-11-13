package com.oneuiclone.home.settings

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.oneuiclone.home.ui.Fonts
import com.oneuiclone.home.ui.GlassParams
import com.oneuiclone.home.ui.GlassShaders
import com.oneuiclone.home.ui.LiquidGlass
import com.oneuiclone.home.ui.WallpaperLayer
import java.io.File
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

/**
 * Glass playground, ported from Kyant's AndroidLiquidGlass catalog (GlassPlaygroundContent): a glass pane over the
 * wallpaper that you drag, pinch and rotate, with sliders for corner radius, blur radius, refraction height,
 * refraction amount and chromatic aberration. The values are the launcher's glass settings ([Prefs.glassParams]):
 * the Search pills and other glass surfaces use the refraction ones.
 */
class GlassPlaygroundActivity : Activity() {

    private lateinit var prefs: Prefs
    private var params = GlassParams()
    private lateinit var root: GestureRoot
    private lateinit var pane: GlassPane
    private lateinit var sheet: LinearLayout
    private val sliders = ArrayList<Pair<SeekBar, (GlassParams) -> Float>>()
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        prefs = Prefs(this)
        params = prefs.glassParams
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val bounds = windowManager.currentWindowMetrics.bounds
        val wall = loadWallpaper(bounds.width(), bounds.height())

        root = GestureRoot(this)
        root.addView(WallView(this, wall), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val size = dp(256f)
        pane = GlassPane(this, wall, size, size)
        pane.applyParams(params)
        root.pane = pane
        root.addView(pane, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(48f) })

        // settings sheet
        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(20f), dp(24f), dp(12f))
            background = GradientDrawable().apply { cornerRadius = dp(32f).toFloat(); setColor(0x80FFFFFF.toInt()) }
        }
        row("Corner radius", 0f, 1f, { it.cornerRadius }) { p, v -> p.copy(cornerRadius = v) }
        row("Blur radius", 0f, 32f, { it.blurDp }) { p, v -> p.copy(blurDp = v) }
        row("Refraction height", 0f, 1f, { it.refractionHeight }) { p, v -> p.copy(refractionHeight = v) }
        row("Refraction amount", 0f, 1f, { it.refractionAmount }) { p, v -> p.copy(refractionAmount = v) }
        row("Chromatic aberration", 0f, 1f, { it.chromaticAberration }) { p, v -> p.copy(chromaticAberration = v) }
        val sheetLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        root.addView(sheet, sheetLp)

        val toggle = button("🔽") {
            sheet.visibility = if (sheet.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            (it as TextView).text = if (sheet.visibility == View.VISIBLE) "🔽" else "🔼"
        }
        val reset = button("Reset") {
            params = GlassParams()
            sliders.forEach { (sb, get) -> sb.progress = toProgress(get(params), sb) }
            pane.applyParams(params)
            prefs.glassParams = params
            pane.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).rotation(0f).setDuration(300).start()
        }
        val btnLp = { g: Int -> FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44f), g) }
        root.addView(toggle, btnLp(Gravity.BOTTOM or Gravity.START))
        root.addView(reset, btnLp(Gravity.BOTTOM or Gravity.END))

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            (pane.layoutParams as FrameLayout.LayoutParams).topMargin = dp(48f) + bars.top
            pane.requestLayout()
            val bottom = bars.bottom
            (toggle.layoutParams as FrameLayout.LayoutParams).apply { setMargins(dp(20f), 0, 0, dp(20f) + bottom) }
            (reset.layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, 0, dp(20f), dp(20f) + bottom) }
            (sheet.layoutParams as FrameLayout.LayoutParams).apply { setMargins(dp(16f), 0, dp(16f), dp(72f) + bottom) }
            root.requestLayout()
            insets
        }
        setContentView(root)
    }

    override fun onPause() {
        prefs.glassParams = params
        super.onPause()
    }

    // ---- UI helpers ----

    private fun toProgress(v: Float, sb: SeekBar) = (v / (sb.tag as Float) * sb.max).toInt()

    /** A labelled slider over [min]..[max]; live updates the pane, saves when let go. */
    private fun row(title: String, lo: Float, hi: Float, get: (GlassParams) -> Float, set: (GlassParams, Float) -> GlassParams) {
        val label = TextView(this).apply {
            text = title
            setTextColor(0xFF000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Fonts.ui(this@GlassPlaygroundActivity, 500)
        }
        val value = TextView(this).apply {
            setTextColor(0xFF3E3E3E.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        fun show(v: Float) { value.text = if (hi > 1f) "%.0f dp".format(v) else "%.2f".format(v) }
        val sb = SeekBar(this).apply {
            this.max = 1000
            tag = hi
            progress = (get(params) / hi * 1000).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFFF8D28.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p / 1000f * hi
                    show(v)
                    if (fromUser) { params = set(params, v); pane.applyParams(params) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { prefs.glassParams = params }
            })
        }
        show(get(params))
        sliders += sb to get
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }
        sheet.addView(head)
        sheet.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8f) })
    }

    private fun button(label: String, onClick: (View) -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setPadding(dp(20f), 0, dp(20f), 0)
        background = GradientDrawable().apply { cornerRadius = dp(22f).toFloat(); setColor(0xFFFF8D28.toInt()) }
        setOnClickListener(onClick)
    }

    // ---- wallpaper ----

    private fun loadWallpaper(w: Int, h: Int): Bitmap {
        if (WallpaperLayer.hasOwnCopy(this)) {
            BitmapFactory.decodeFile(File(filesDir, WallpaperLayer.OWN_FILE).path)?.let { return WallpaperLayer.centerCrop(it, w, h) }
        }
        return fallbackArt(w, h)
    }

    /** No readable wallpaper: colourful shapes and lines, enough for the refraction to show. */
    private fun fallbackArt(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF1B2A6B.toInt(), 0xFF7B2FF7.toInt(), 0xFFFF6B9D.toInt(), 0xFFFFB347.toInt()), null, Shader.TileMode.CLAMP)
        c.drawPaint(p)
        p.shader = null
        val colors = intArrayOf(0xFF00E5FF.toInt(), 0xFFFFEA00.toInt(), 0xFF76FF03.toInt(), 0xFFFFFFFF.toInt())
        for (i in 0 until 14) {
            p.color = colors[i % colors.size]; p.alpha = 190
            c.drawCircle(w * ((i * 37) % 100) / 100f, h * ((i * 53) % 100) / 100f, w * (0.04f + (i % 4) * 0.03f), p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = d * 2f; p.color = 0x66FFFFFF
        var x = 0f
        while (x < w) { c.drawLine(x, 0f, x, h.toFloat(), p); x += d * 48f }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w.toFloat(), y, p); y += d * 48f }
        return b
    }
}

/** The wallpaper, full window. */
internal class WallView(context: Context, private val bmp: Bitmap) : View(context) {
    private val p = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()
    override fun onDraw(canvas: Canvas) {
        dst.set(0, 0, width, height)
        canvas.drawBitmap(bmp, null, dst, p)
    }
}

/** Drag, pinch and rotate on anything that isn't a control: moves [pane] (Kyant's detectTransformGestures). */
internal class GestureRoot(context: Context) : FrameLayout(context) {
    var pane: View? = null
    private var haveRef = false
    private var refCount = 0
    private var refX = 0f; private var refY = 0f; private var refDist = 0f; private var refAngle = 0f

    init { isClickable = true }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pane = pane ?: return false
        val lifted = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        val ids = (0 until e.pointerCount).filter { it != lifted }.take(2)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL || ids.isEmpty()) { haveRef = false; return true }
        val cx = ids.map { e.getX(it) }.average().toFloat()
        val cy = ids.map { e.getY(it) }.average().toFloat()
        var dist = 0f; var angle = 0f
        if (ids.size == 2) {
            val dx = e.getX(ids[1]) - e.getX(ids[0]); val dy = e.getY(ids[1]) - e.getY(ids[0])
            dist = hypot(dx, dy); angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        }
        val fresh = e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_UP
        if (!haveRef || fresh || ids.size != refCount) {
            haveRef = true; refCount = ids.size; refX = cx; refY = cy; refDist = dist; refAngle = angle
            return true
        }
        pane.translationX += cx - refX
        pane.translationY += cy - refY
        if (ids.size == 2 && refDist > 0f) {
            pane.scaleX = (pane.scaleX * dist / refDist).coerceIn(0.3f, 4f)
            pane.scaleY = pane.scaleX
            var da = angle - refAngle
            while (da > 180f) da -= 360f
            while (da < -180f) da += 360f
            pane.rotation += da
        }
        refX = cx; refY = cy; refDist = dist; refAngle = angle
        return true
    }
}

/**
 * The glass: the wallpaper behind it (window-fixed, so it stays put while the pane moves) through vibrancy,
 * blur and the lens, clipped to a rounded rect, with the plain edge highlight on top.
 */
internal class GlassPane(context: Context, wall: Bitmap, private val w: Int, private val h: Int) : FrameLayout(context) {
    private val density = context.resources.displayMetrics.density
    private val backdrop = Backdrop(context, wall)
    private val rim = Rim(context)
    private var radius = 0f
    private val plain by lazy { RuntimeShader(GlassShaders.refraction(mapped = false, dispersion = false)) }
    private val dispersed by lazy { RuntimeShader(GlassShaders.refraction(mapped = false, dispersion = true)) }

    init {
        clipChildren = false
        clipToPadding = false
        addView(backdrop, LayoutParams(w, h))
        addView(rim, LayoutParams(w, h))
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
    }

    /** Applies [p] (Kyant: vibrancy -> blur -> lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)). */
    fun applyParams(p: GlassParams) {
        val minSide = min(w, h).toFloat()
        radius = minSide / 2f * p.cornerRadius
        val refH = (p.refractionHeight * minSide * 0.5f)
        val refA = p.refractionAmount * minSide
        val blurPx = p.blurDp * density
        val pad = ceil(refH).toInt() + ceil(blurPx * 2f).toInt()

        backdrop.layoutParams = (backdrop.layoutParams as LayoutParams).apply {
            width = w + 2 * pad; height = h + 2 * pad; setMargins(-pad, -pad, 0, 0)
        }
        rim.radius = radius

        var inner = RenderEffect.createColorFilterEffect(LiquidGlass.saturationFilter(1.5f))
        if (blurPx > 0.5f) inner = RenderEffect.createBlurEffect(blurPx, blurPx, inner, Shader.TileMode.CLAMP)
        val effect = if (refH > 0f && refA > 0f) {
            val s = if (p.chromaticAberration > 0f) dispersed else plain
            s.setFloatUniform("size", w.toFloat(), h.toFloat())
            s.setFloatUniform("offset", -pad.toFloat(), -pad.toFloat())
            s.setFloatUniform("cornerRadii", floatArrayOf(radius, radius, radius, radius))
            s.setFloatUniform("refractionHeight", refH)
            s.setFloatUniform("refractionAmount", -refA)
            s.setFloatUniform("depthEffect", 1f)
            if (p.chromaticAberration > 0f) s.setFloatUniform("chromaticAberration", 1f)
            RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(s, "content"), inner)
        } else inner
        backdrop.setRenderEffect(effect)
        invalidateOutline()
        rim.invalidate()
        requestLayout()
    }

    /** Draws the window-fixed wallpaper; redraws whenever the pane's on-screen transform changes. */
    private class Backdrop(context: Context, private val wall: Bitmap) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val toWindow = Matrix()
        private val drawnAt = Matrix()
        private val inv = Matrix()
        private val dst = Rect()
        private val moved = ViewTreeObserver.OnPreDrawListener {
            toWindow.reset(); transformMatrixToGlobal(toWindow)
            if (toWindow != drawnAt) invalidate()
            true
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); viewTreeObserver.addOnPreDrawListener(moved) }
        override fun onDetachedFromWindow() { viewTreeObserver.removeOnPreDrawListener(moved); super.onDetachedFromWindow() }

        override fun onDraw(canvas: Canvas) {
            drawnAt.reset(); transformMatrixToGlobal(drawnAt)
            if (!drawnAt.invert(inv)) return
            val r = rootView
            canvas.save()
            canvas.concat(inv)
            dst.set(0, 0, r.width, r.height)
            canvas.drawBitmap(wall, null, dst, paint)
            canvas.restore()
        }
    }

    /** Highlight.Plain: a thin white edge added over the glass, clipped inside the shape. */
    private class Rim(context: Context) : View(context) {
        var radius = 0f
        private val density = context.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb((255 * 0.38f).toInt(), 255, 255, 255)
            blendMode = BlendMode.PLUS
        }
        private val shape = Path()
        private val rect = RectF()
        override fun onDraw(canvas: Canvas) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            val r = radius.coerceAtMost(min(width, height) / 2f)
            shape.reset(); shape.addRoundRect(rect, r, r, Path.Direction.CW)
            paint.strokeWidth = ceil(0.5f * density) * 2f
            canvas.save()
            canvas.clipPath(shape)
            canvas.drawPath(shape, paint)
            canvas.restore()
        }
    }
}
